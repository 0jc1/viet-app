package com.vietsnap

import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.DateUtils
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vietsnap.databinding.ActivityViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-size view of one saved shot and its translation. */
class ViewerActivity : AppCompatActivity() {

    private lateinit var b: ActivityViewerBinding
    private val store by lazy { HistoryStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        padForSystemBars(b.root)

        val id = intent.getStringExtra("id") ?: return finish()
        lifecycleScope.launch {
            val entry = withContext(Dispatchers.IO) { store.load(id) } ?: return@launch finish()
            b.english.text = entry.english
            val date = DateUtils.formatDateTime(
                this@ViewerActivity, entry.time,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
            )
            b.meta.text = if (entry.model != null) "$date · ${entry.model} · ${entry.ms} ms" else "$date · on-device"
            b.photo.setImageBitmap(withContext(Dispatchers.IO) { BitmapFactory.decodeFile(entry.image.path) })
        }

        b.delete.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Delete this photo and its translation?")
                .setPositiveButton("Delete") { _, _ ->
                    store.delete(id)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
