package com.korkutsoftware.doruhavadisadmin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.korkutsoftware.doruhavadisadmin.models.News

class NewsAdapter(
    private var newsList: List<News>,
    private val onActionClick: (News, Int) -> Unit // 0 for Update, 1 for Delete, 2 for Prepare Share
) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    class NewsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivNewsImage: ImageView = view.findViewById(R.id.iv_news_image)
        val tvNewsTitle: TextView = view.findViewById(R.id.tv_news_title)
        val tvNewsCategory: TextView = view.findViewById(R.id.tv_news_category)
        val tvAdminName: TextView = view.findViewById(R.id.tv_admin_name)
        val btnMore: ImageButton = view.findViewById(R.id.btn_more)
        val btnPrepareShare: View = view.findViewById(R.id.btn_prepare_share)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_news, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val news = newsList[position]
        holder.tvNewsTitle.text = news.title
        holder.tvNewsCategory.text = news.category.uppercase()
        holder.tvAdminName.text = "${news.adminName} • Doğru Havadis"

        if (news.mediaUrls.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(news.mediaUrls[0])
                .centerCrop()
                .into(holder.ivNewsImage)
        } else {
            holder.ivNewsImage.setImageResource(R.drawable.ic_news)
        }

        holder.btnMore.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.news_item_menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_update -> {
                        onActionClick(news, 0)
                        true
                    }
                    R.id.action_delete -> {
                        onActionClick(news, 1)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        holder.btnPrepareShare.setOnClickListener {
            onActionClick(news, 2)
        }
    }

    override fun getItemCount(): Int = newsList.size

    fun updateData(newList: List<News>) {
        newsList = newList
        notifyDataSetChanged()
    }
}