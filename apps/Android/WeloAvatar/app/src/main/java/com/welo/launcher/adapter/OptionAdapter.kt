package com.welo.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.R
import com.welo.launcher.entity.OptionItem

class OptionAdapter(private val options: List<OptionItem>) :
    RecyclerView.Adapter<OptionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_icon)
        val title: TextView = view.findViewById(R.id.tv_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_option_sheet, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option = options[position]
        holder.icon.setImageResource(option.iconResId)
        holder.title.text = option.title
        holder.itemView.setOnClickListener { option.action() }
    }

    override fun getItemCount(): Int = options.size
}