package com.vietsnap

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vietsnap.databinding.ActivityHistoryBinding
import com.vietsnap.databinding.ItemHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var b: ActivityHistoryBinding
    private val store by lazy { HistoryStore(this) }
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(b.root)
        padForSystemBars(b.root)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        // Reload so deletions made in the viewer show up.
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { store.list() }
            adapter.submit(entries)
            b.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        private var items = emptyList<HistoryEntry>()

        @Suppress("NotifyDataSetChanged")
        fun submit(list: List<HistoryEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(h: Holder, position: Int) {
            val e = items[position]
            h.b.english.text = e.english
            h.b.date.text = DateUtils.getRelativeDateTimeString(
                this@HistoryActivity, e.time, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
            )
            h.b.thumb.setImageDrawable(null)
            h.b.thumb.tag = e.id
            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) { HistoryStore.thumbnail(e.image, 144) }
                if (h.b.thumb.tag == e.id) h.b.thumb.setImageBitmap(bmp)
            }
            h.b.root.setOnClickListener {
                startActivity(Intent(this@HistoryActivity, ViewerActivity::class.java).putExtra("id", e.id))
            }
        }
    }

    private class Holder(val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root)
}

/** Android 15 draws edge-to-edge; pad [view] so content clears the status and navigation bars. */
fun padForSystemBars(view: View) {
    ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
