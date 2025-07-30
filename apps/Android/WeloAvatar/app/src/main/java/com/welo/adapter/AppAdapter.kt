package com.welo.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.mls.api.ApplicationProvider
import com.taobao.meta.avatar.R
import com.welo.base.ImageLoader
import com.welo.entity.AppInfo


class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var appList: List<AppInfo> = ArrayList()
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val appInfo = appList[position]
        if (holder is ViewHolder) {
            holder.bind(appInfo)
        }
    }

    override fun getItemCount(): Int {
        return appList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        var appName: TextView = itemView.findViewById(R.id.app_name)

        fun bind(appInfo: AppInfo) {
            ImageLoader.getInstance(appIcon.context)
                .load(appInfo.appIcon, appIcon)
//            appIcon.setImageDrawable(appInfo.appIcon)
            appName.text = appInfo.appName
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(appInfos: List<AppInfo>) {
        appList = appInfos
        notifyDataSetChanged()
    }
}