package com.korkutsoftware.doruhavadisadmin.pdf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.korkutsoftware.doruhavadisadmin.R
import com.korkutsoftware.doruhavadisadmin.models.News

class NewsSelectionAdapter(
    private var items: MutableList<SelectableNews> = mutableListOf(),
    private val onSelectionChangedListener: (() -> Unit)? = null
) : RecyclerView.Adapter<NewsSelectionAdapter.ViewHolder>() {

    data class SelectableNews(
        val news: News,
        var isSelected: Boolean = true,
        var isMainHeadline: Boolean = false,
        var isCoverNews: Boolean = true
    )

    fun updateList(newsList: List<News>) {
        val headlineIndex = newsList.indexOfFirst { it.isHeadline }.let { if (it >= 0) it else 0 }
        items = newsList.mapIndexed { index, news ->
            SelectableNews(
                news = news,
                isSelected = true,
                isMainHeadline = (index == headlineIndex && newsList.isNotEmpty()),
                isCoverNews = true
            )
        }.toMutableList()
        notifyDataSetChanged()
        onSelectionChangedListener?.invoke()
    }

    fun selectAll() {
        items.forEach { it.isSelected = true }
        notifyDataSetChanged()
        onSelectionChangedListener?.invoke()
    }

    fun deselectAll() {
        items.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChangedListener?.invoke()
    }

    fun setMainHeadline(newsId: String) {
        items.forEach {
            it.isMainHeadline = (it.news.id == newsId)
        }
        notifyDataSetChanged()
        onSelectionChangedListener?.invoke()
    }

    fun getMainHeadlineNewsId(): String? {
        return items.firstOrNull { it.isMainHeadline }?.news?.id
            ?: items.firstOrNull { it.isSelected }?.news?.id
    }

    fun getCoverNewsIds(): List<String> {
        return items.filter { it.isSelected && it.isCoverNews && !it.isMainHeadline }.map { it.news.id }
    }

    fun getSelectedNewsIds(): List<String> {
        return items.filter { it.isSelected }.map { it.news.id }
    }

    fun getSelectedCount(): Int {
        return items.count { it.isSelected }
    }

    fun getTotalCount(): Int {
        return items.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news_selection, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cbSelect: MaterialCheckBox = itemView.findViewById(R.id.cbSelectNews)
        private val ivThumb: ImageView = itemView.findViewById(R.id.ivNewsThumb)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvNewsTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvNewsSubtitle)
        private val chipMainHeadline: Chip = itemView.findViewById(R.id.chipMainHeadline)
        private val cbCoverNews: MaterialCheckBox = itemView.findViewById(R.id.cbCoverNews)

        fun bind(item: SelectableNews) {
            val news = item.news
            tvTitle.text = news.title

            val locationStr = if (news.province.isNotEmpty()) {
                "${news.province} / ${news.district}"
            } else news.category
            tvSubtitle.text = locationStr

            if (news.mediaUrls.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(news.mediaUrls[0])
                    .centerCrop()
                    .into(ivThumb)
            } else {
                ivThumb.setImageResource(R.drawable.ic_news)
            }

            // Remove listeners before setting state to avoid listener loops
            cbSelect.setOnCheckedChangeListener(null)
            chipMainHeadline.setOnClickListener(null)
            cbCoverNews.setOnCheckedChangeListener(null)

            cbSelect.isChecked = item.isSelected
            chipMainHeadline.isChecked = item.isMainHeadline
            cbCoverNews.isChecked = item.isCoverNews

            cbSelect.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                if (!isChecked) {
                    item.isMainHeadline = false
                    item.isCoverNews = false
                }
                onSelectionChangedListener?.invoke()
            }

            chipMainHeadline.setOnClickListener {
                if (item.isSelected) {
                    setMainHeadline(news.id)
                } else {
                    cbSelect.isChecked = true
                    item.isSelected = true
                    setMainHeadline(news.id)
                }
            }

            cbCoverNews.setOnCheckedChangeListener { _, isChecked ->
                item.isCoverNews = isChecked
                onSelectionChangedListener?.invoke()
            }

            itemView.setOnClickListener {
                cbSelect.isChecked = !cbSelect.isChecked
            }
        }
    }
}
