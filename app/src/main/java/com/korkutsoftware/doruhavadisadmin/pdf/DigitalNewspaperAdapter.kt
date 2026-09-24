package com.korkutsoftware.doruhavadisadmin.pdf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.korkutsoftware.doruhavadisadmin.R
import com.korkutsoftware.doruhavadisadmin.models.DigitalNewspaper

class DigitalNewspaperAdapter(
    private var items: List<DigitalNewspaper> = emptyList(),
    private val onItemAction: (item: DigitalNewspaper, actionType: Int) -> Unit
) : RecyclerView.Adapter<DigitalNewspaperAdapter.ViewHolder>() {

    fun updateList(newList: List<DigitalNewspaper>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_digital_newspaper, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvNewspaperTitle)
        private val chipPageSize: Chip = itemView.findViewById(R.id.chipPageSize)
        private val tvIssueDate: TextView = itemView.findViewById(R.id.tvIssueDate)
        private val tvHeadline: TextView = itemView.findViewById(R.id.tvHeadlineTitle)
        private val tvNewsCount: TextView = itemView.findViewById(R.id.tvNewsCount)
        private val tvPublisher: TextView = itemView.findViewById(R.id.tvPublisher)
        private val btnViewPdf: MaterialButton = itemView.findViewById(R.id.btnViewPdf)
        private val btnShare: MaterialButton = itemView.findViewById(R.id.btnShareNewspaper)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDeleteNewspaper)

        fun bind(item: DigitalNewspaper) {
            tvTitle.text = if (item.title.isNotEmpty()) item.title else "📰 Doğru Havadis Gazetesi"
            chipPageSize.text = "${item.pageSize} Formatı"
            tvIssueDate.text = "📅 Tarih: ${item.issueDate}"

            if (item.headlineTitle.isNotEmpty()) {
                tvHeadline.visibility = View.VISIBLE
                tvHeadline.text = "⭐ Ana Manşet: ${item.headlineTitle}"
            } else {
                tvHeadline.visibility = View.GONE
            }

            tvNewsCount.text = "📊 ${item.newsCount} Haber Basıldı"
            tvPublisher.text = if (item.publisherName.isNotEmpty()) item.publisherName else "Doğru Havadis Admin"

            btnViewPdf.setOnClickListener {
                onItemAction(item, 0) // View PDF
            }

            btnShare.setOnClickListener {
                onItemAction(item, 1) // Share
            }

            btnDelete.setOnClickListener {
                onItemAction(item, 2) // Delete
            }

            itemView.setOnClickListener {
                onItemAction(item, 0)
            }
        }
    }
}
