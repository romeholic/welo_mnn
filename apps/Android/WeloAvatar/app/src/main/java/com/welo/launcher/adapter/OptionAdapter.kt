package com.welo.launcher.adapter

import android.graphics.Color
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
        if (position == 0) {
            // 置灰处理 - 使用颜色滤镜
            holder.icon.setColorFilter(Color.GRAY, android.graphics.PorterDuff.Mode.MULTIPLY)
            holder.title.setTextColor(Color.GRAY)
            // 不可点击
            holder.itemView.isEnabled = false
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
        } else {
            // 正常状态 - 清除颜色滤镜
            holder.icon.clearColorFilter()
            holder.title.setTextColor(Color.WHITE) // 或者使用原来的文字颜色
            holder.itemView.isEnabled = true
            holder.itemView.isClickable = true
            holder.itemView.setOnClickListener { option.action() }
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            holder.blurBackground.setRenderEffect(
//                RenderEffect.createBlurEffect(
//                    4f,
//                    4f,
//                    Shader.TileMode.MIRROR
//                )
//            )
//        }
    }

    override fun getItemCount(): Int = options.size
}