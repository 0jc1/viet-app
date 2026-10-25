package com.vietsnap

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class OpenRouterClient(private val apiKey: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Keep the TLS connection alive between shots so each request skips the handshake.
        .connectionPool(ConnectionPool(4, 10, TimeUnit.MINUTES))
        .build()

    /** Opens connections ahead of the first shot (one per raced model). */
    fun warmUp() {
        repeat(MODELS.size) {
            val req = Request.Builder().url(BASE_URL).head().build()
            http.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) = response.close()
            })
        }
    }

    /**
     * Races all [MODELS] on the same image; the first one to stream a token wins and the
     * rest are cancelled. [onDelta] receives the accumulated text of the winner.
     */
    suspend fun translateImage(jpeg: ByteArray, onDelta: (model: String, text: String) -> Unit): String =
        coroutineScope {
            val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
            val winner = CompletableDeferred<Int>()
            val jobs = ArrayList<Deferred<Result<String>>>()
            MODELS.forEachIndexed { i, model ->
                jobs += async(Dispatchers.IO) {
                    runCatching {
                        stream(model, dataUrl) { text ->
                            if (winner.complete(i)) jobs.forEachIndexed { j, d -> if (j != i) d.cancel() }
                            if (winner.getCompleted() == i) onDelta(model, text)
                        }
                    }
                }
            }
            val results = jobs.map { d ->
                try {
                    d.await()
                } catch (e: CancellationException) {
                    coroutineContext.ensureActive() // rethrow if we ourselves were cancelled
                    Result.failure(e)
                }
            }
            val w = if (winner.isCompleted) winner.getCompleted() else -1
            results.getOrNull(w)?.getOrNull()
                ?: results.firstNotNullOfOrNull { it.getOrNull() }
                ?: throw (results.firstNotNullOfOrNull { it.exceptionOrNull() } ?: IOException("No response"))
        }

    private suspend fun stream(model: String, dataUrl: String, onText: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", model)
                .put("stream", true)
                .put("temperature", 0)
                .put("max_tokens", 600)
                .put("provider", JSONObject().put("sort", "latency"))
                .put("reasoning", JSONObject().put("enabled", false))
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", JSONArray()
                        .put(JSONObject().put("type", "image_url")
                            .put("image_url", JSONObject().put("url", dataUrl))))))
            val req = Request.Builder()
                .url(BASE_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val call = http.newCall(req)
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { call.cancel() }
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("HTTP ${resp.code}: ${resp.body?.string()?.take(200)}")
                }
                val source = resp.body!!.source()
                val out = StringBuilder()
                while (true) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    // SSE: skip keep-alive comments (": OPENROUTER PROCESSING") and blanks.
                    if (!line.startsWith("data: ")) continue
                    val data = line.removePrefix("data: ")
                    if (data == "[DONE]") break
                    val json = JSONObject(data)
                    json.optJSONObject("error")?.let { throw IOException(it.optString("message")) }
                    val delta = json.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("delta")?.optString("content").orEmpty()
                    if (delta.isNotEmpty()) {
                        out.append(delta)
                        onText(out.toString())
                    }
                }
                out.toString().trim()
            }
        }

    companion object {
        private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"

        // Fastest image models measured at ~1.4-1.8s per photo; racing both trims tail latency.
        val MODELS = listOf("google/gemini-3.1-flash-lite", "anthropic/claude-haiku-5.5")

        private const val SYSTEM_PROMPT =
            "You translate Vietnamese text in photos. Read all Vietnamese text in the image and " +
                "output only its English translation, keeping line breaks. If there is no " +
                "Vietnamese text, output: (no Vietnamese text found)"
    }
}
