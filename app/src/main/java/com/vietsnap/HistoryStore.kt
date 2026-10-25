package com.vietsnap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/** One saved shot: the photo that was sent for translation plus what came back. */
data class HistoryEntry(
    val id: String,
    val image: File,
    val english: String,
    val quick: String?,
    val model: String?,
    val ms: Long,
    val time: Long,
)

/** Stores each shot as `<id>.jpg` + `<id>.json` in app-private storage. */
class HistoryStore(context: Context) {

    private val dir = File(context.filesDir, "history").apply { mkdirs() }

    fun save(jpeg: ByteArray, english: String, quick: String?, model: String?, ms: Long): HistoryEntry {
        val time = System.currentTimeMillis()
        val id = time.toString()
        val image = File(dir, "$id.jpg").apply { writeBytes(jpeg) }
        val meta = JSONObject()
            .put("english", english)
            .put("quick", quick ?: JSONObject.NULL)
            .put("model", model ?: JSONObject.NULL)
            .put("ms", ms)
            .put("time", time)
        File(dir, "$id.json").writeText(meta.toString())
        return HistoryEntry(id, image, english, quick, model, ms, time)
    }

    /** Newest first. */
    fun list(): List<HistoryEntry> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { load(it.nameWithoutExtension) }
            .sortedByDescending { it.time }

    fun load(id: String): HistoryEntry? = runCatching {
        val json = JSONObject(File(dir, "$id.json").readText())
        HistoryEntry(
            id = id,
            image = File(dir, "$id.jpg"),
            english = json.getString("english"),
            quick = json.optString("quick").takeIf { !json.isNull("quick") },
            model = json.optString("model").takeIf { !json.isNull("model") },
            ms = json.optLong("ms"),
            time = json.optLong("time"),
        )
    }.getOrNull()

    fun delete(id: String) {
        File(dir, "$id.jpg").delete()
        File(dir, "$id.json").delete()
    }

    companion object {
        /** Decodes a small preview without loading the full image into memory. */
        fun thumbnail(file: File, targetPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
