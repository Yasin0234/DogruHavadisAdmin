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
import com.korkutsoftware.doruhavadisadmin.models.Columnist

class ColumnistAdapter(
    private var columnistList: List<Columnist>,
    private val onActionClick: (Columnist, Int) -> Unit // 0 for Update, 1 for Delete, 2 for Prepare Share
) : RecyclerView.Adapter<ColumnistAdapter.ColumnistViewHolder>() {

    class ColumnistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivWriterImage: ImageView = view.findViewById(R.id.iv_writer_image)
        val tvColumnistTitle: TextView = view.findViewById(R.id.tv_columnist_title)
        val tvWriterName: TextView = view.findViewById(R.id.tv_writer_name)
        val btnMore: ImageButton = view.findViewById(R.id.btn_more)
        val btnPrepareShare: View = view.findViewById(R.id.btn_prepare_share)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColumnistViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_columnist, parent, false)
        return ColumnistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColumnistViewHolder, position: Int) {
        val columnist = columnistList[position]
        holder.tvColumnistTitle.text = columnist.title
        holder.tvWriterName.text = "Yazar: ${columnist.writerName}"

        if (columnist.writerImageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(columnist.writerImageUrl)
                .centerCrop()
                .into(holder.ivWriterImage)
        } else {
            holder.ivWriterImage.setImageResource(R.drawable.ic_person)
        }

        holder.btnMore.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.news_item_menu) // Reusing the same menu for update/delete
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_update -> {
                        onActionClick(columnist, 0)
                        true
                    }
                    R.id.action_delete -> {
                        onActionClick(columnist, 1)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        holder.btnPrepareShare.setOnClickListener {
            onActionClick(columnist, 2)
        }
    }

    override fun getItemCount(): Int = columnistList.size

    fun updateData(newList: List<Columnist>) {
        columnistList = newList
        notifyDataSetChanged()
    }
}