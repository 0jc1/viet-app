package com.vietsnap

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Word(val vi: String, val en: String, val count: Int = 1, val lastSeen: Long = 0)

/** Every Vietnamese word discovered in photos, kept in one JSON file in app-private storage. */
class VocabStore(context: Context) {

    private val file = File(context.filesDir, "vocab.json")
    private val words = LinkedHashMap<String, Word>()

    init {
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val w = Word(o.getString("vi"), o.getString("en"), o.optInt("count", 1), o.optLong("last"))
                words[w.vi] = w
            }
        }
    }

    /** Records words seen in a photo; returns how many were new. Keeps the first meaning seen. */
    @Synchronized
    fun add(found: List<Word>): Int {
        val now = System.currentTimeMillis()
        var added = 0
        for (w in found) {
            val old = words[w.vi]
            if (old == null) added++
            words[w.vi] = old?.copy(count = old.count + 1, lastSeen = now) ?: w.copy(lastSeen = now)
        }
        write()
        return added
    }

    @Synchronized
    fun delete(vi: String) {
        words.remove(vi)
        write()
    }

    /** Most recently seen first. */
    @Synchronized
    fun list(): List<Word> = words.values.sortedByDescending { it.lastSeen }

    private fun write() {
        val arr = JSONArray()
        words.values.forEach {
            arr.put(JSONObject().put("vi", it.vi).put("en", it.en).put("count", it.count).put("last", it.lastSeen))
        }
        file.writeText(arr.toString())
    }

    companion object {
        /** Separates the English translation from the word list in the model's reply. */
        const val MARKER = "###WORDS###"

        /** The translation part only; safe to call on a partially streamed reply. */
        fun translationPart(raw: String): String = raw.substringBefore("###").trim()

        /** Parses the "vietnamese = english" lines after [MARKER]. */
        fun wordsPart(raw: String): List<Word> =
            raw.substringAfter(MARKER, "").lines().mapNotNull { line ->
                val vi = line.substringBefore("=", "").trim().lowercase()
                val en = line.substringAfter("=", "").trim()
                if (vi.isEmpty() || en.isEmpty()) null else Word(vi, en)
            }.distinctBy { it.vi }
    }
}
