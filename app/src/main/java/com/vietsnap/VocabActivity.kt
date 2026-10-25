package com.vietsnap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vietsnap.databinding.ActivityVocabBinding
import com.vietsnap.databinding.ItemWordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Two-column list of every discovered word: Vietnamese on the left, English on the right. */
class VocabActivity : AppCompatActivity() {

    private lateinit var b: ActivityVocabBinding
    private val vocab by lazy { VocabStore(this) }
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityVocabBinding.inflate(layoutInflater)
        setContentView(b.root)
        padForSystemBars(b.root)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        b.list.adapter = adapter
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val words = withContext(Dispatchers.IO) { vocab.list() }
            adapter.submit(words)
            b.title.text = if (words.isEmpty()) "Words" else "Words (${words.size})"
            b.empty.visibility = if (words.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        private var items = emptyList<Word>()

        @Suppress("NotifyDataSetChanged")
        fun submit(list: List<Word>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemWordBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(h: Holder, position: Int) {
            val w = items[position]
            h.b.vi.text = w.vi
            h.b.en.text = w.en
            h.b.count.text = if (w.count > 1) "×${w.count}" else ""
            h.b.root.setOnLongClickListener {
                AlertDialog.Builder(this@VocabActivity)
                    .setMessage("Remove \"${w.vi}\" from your words?")
                    .setPositiveButton("Remove") { _, _ ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { vocab.delete(w.vi) }
                            reload()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }
    }

    private class Holder(val b: ItemWordBinding) : RecyclerView.ViewHolder(b.root)
}
