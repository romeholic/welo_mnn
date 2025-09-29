package com.welo.launcher.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.R
import com.welo.base.ImageLoader
import com.welo.entity.AppInfo


class HomeAppAdapter(private val onAppClicked: (appInfo: AppInfo?, isMore: Boolean) -> Unit) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val TYPE_APP: Int = 0 // 应用项
        private const val TYPE_MORE: Int = 1 // 更多按钮项
        private const val MAX_SHOW_COUNT = 4 // 最多显示 4 条数据，超过则显示“更多”
    }

    private var showMore: Boolean = false
    private var appList: List<AppInfo> = ArrayList()
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_MORE -> {
                val view = layoutInflater.inflate(R.layout.item_more, parent, false)
                MoreViewHolder(view)
            }

            else -> {
                val view = layoutInflater.inflate(R.layout.item_app, parent, false)
                ViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val appInfo = appList.getOrNull(position) ?: return
        if (holder is ViewHolder) {
            holder.bind(appInfo)
        } else if (holder is MoreViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int {
        return if (showMore) {
            // 显示：前 MAX_SHOW_COUNT 条数据 + 1 个“更多”按钮 → 共 MAX_SHOW_COUNT + 1 个 Item
            MAX_SHOW_COUNT + 1
        } else {
            // 不显示“更多”：直接显示所有数据
            appList.size
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (showMore && position == MAX_SHOW_COUNT) {
            return TYPE_MORE
        }
        return TYPE_APP
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        var appName: TextView = itemView.findViewById(R.id.app_name)

        fun bind(appInfo: AppInfo) {
            ImageLoader.Companion.getInstance(appIcon.context)
                .load(appInfo.appIcon, appIcon)
            appName.text = appInfo.appName
            appIcon.setOnClickListener {
                onAppClicked.invoke(appInfo, false)
            }
        }
    }

    // 更多项ViewHolder
    inner class MoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var btnMore: ImageView = itemView.findViewById(R.id.btn_more)

        fun bind() {
            btnMore.setOnClickListener {
                onAppClicked.invoke(null, true)
            }
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    fun submitList(appInfos: List<AppInfo>) {
        appList = appInfos
        showMore = appList.size > MAX_SHOW_COUNT
        notifyDataSetChanged()
    }
}