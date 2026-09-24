package com.korkutsoftware.doruhavadisadmin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.korkutsoftware.doruhavadisadmin.models.News

// XML'den gelen veriler için model
data class XmlNews(
    val title: String = "",
    val description: String = "",
    val fullContent: String = "",
    val link: String = "",
    val imageUrl: String = "",
    val source: String = "Haberler.com",
    val city: String = "",
    val district: String = "",
    var isSaved: Boolean = false
)

class XmlNewsAdapter(
    private var xmlNewsList: List<XmlNews>,
    private val onSaveClick: (XmlNews) -> Unit
) : RecyclerView.Adapter<XmlNewsAdapter.XmlViewHolder>() {

    class XmlViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.iv_xml_news_image)
        val tvTitle: TextView = view.findViewById(R.id.tv_xml_news_title)
        val tvSource: TextView = view.findViewById(R.id.tv_xml_news_source)
        val tvLocation: TextView = view.findViewById(R.id.tv_xml_location_tag)
        val btnSave: Button = view.findViewById(R.id.btn_save_to_db)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): XmlViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_xml_news, parent, false)
        return XmlViewHolder(view)
    }

    override fun onBindViewHolder(holder: XmlViewHolder, position: Int) {
        val news = xmlNewsList[position]
        holder.tvTitle.text = news.title
        holder.tvSource.text = "Kaynak: ${news.source}"
        holder.tvLocation.text = "${news.city} / ${news.district}"

        if (news.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(news.imageUrl)
                .placeholder(R.drawable.ic_news)
                .error(R.drawable.ic_news)
                .centerCrop()
                .into(holder.ivImage)
        } else {
            holder.ivImage.setImageResource(R.drawable.ic_news)
        }

        if (news.isSaved) {
            holder.btnSave.text = "Kayıt Edildi"
            holder.btnSave.isEnabled = false
            holder.btnSave.alpha = 0.5f
        } else {
            holder.btnSave.text = "Veritabanına Kaydet"
            holder.btnSave.isEnabled = true
            holder.btnSave.alpha = 1.0f
        }

        holder.btnSave.setOnClickListener { onSaveClick(news) }
    }

    override fun getItemCount(): Int = xmlNewsList.size

    fun updateData(newList: List<XmlNews>) {
        xmlNewsList = newList
        notifyDataSetChanged()
    }
}