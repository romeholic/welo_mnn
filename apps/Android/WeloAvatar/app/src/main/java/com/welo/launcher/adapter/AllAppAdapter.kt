package com.welo.launcher.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.databinding.ItemAllAppBinding
import com.welo.entity.AppInfo


class AllAppAdapter(private val onAppClicked: (AppInfo) -> Unit) :
    ListAdapter<AppInfo, AllAppAdapter.AppViewHolder>(AppDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AppViewHolder {
        val binding = ItemAllAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }


    inner class AppViewHolder(
        private val binding: ItemAllAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(appInfo: AppInfo) {
            binding.apply {
                appName.text = appInfo.appName
                appIcon.setImageDrawable(appInfo.appIcon)

                root.setOnClickListener {
                    onAppClicked.invoke(appInfo)
                }
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}