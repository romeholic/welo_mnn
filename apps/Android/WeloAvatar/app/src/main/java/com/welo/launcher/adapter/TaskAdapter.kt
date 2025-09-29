package com.welo.launcher.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.ItemTaskDefaultBinding
import com.taobao.meta.avatar.databinding.ItemTaskProgressBinding
import com.taobao.meta.avatar.databinding.ItemTaskStepBinding
import com.welo.launcher.entity.TaskBean
import com.welo.launcher.entity.TaskType
import com.welo.room.table.ChatMessage

class TaskAdapter(
    private val onItemClick: ((TaskBean) -> Unit)? = null,
    private val onItemLongClick: ((TaskBean) -> Unit)? = null
) : ListAdapter<TaskBean, TaskAdapter.TaskViewHolder>(TaskDiffCallback) {

    companion object {
        private val TaskDiffCallback = object : DiffUtil.ItemCallback<TaskBean>() {
            override fun areItemsTheSame(oldItem: TaskBean, newItem: TaskBean): Boolean {
                return oldItem.taskId == newItem.taskId
            }

            override fun areContentsTheSame(oldItem: TaskBean, newItem: TaskBean): Boolean {
                return oldItem == newItem
            }

            override fun getChangePayload(oldItem: TaskBean, newItem: TaskBean): Any? {
                return if (oldItem.messagesData != newItem.messagesData) "messages_changed" else null
            }
        }
    }

    sealed class TaskViewHolder(binding: Any) : RecyclerView.ViewHolder(
        when (binding) {
            is ItemTaskDefaultBinding -> binding.root
            is ItemTaskStepBinding -> binding.root
            is ItemTaskProgressBinding -> binding.root
            else -> throw IllegalArgumentException("未知的Binding类型")
        }
    ) {
        abstract fun bind(taskBean: TaskBean)
        open fun bind(taskBean: TaskBean, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                bind(taskBean)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (TaskType.entries[viewType]) {
            TaskType.STEP -> StepViewHolder(
                ItemTaskStepBinding.inflate(inflater, parent, false)
            )

            TaskType.PROGRESS -> ProgressViewHolder(
                ItemTaskProgressBinding.inflate(inflater, parent, false)
            )

            else -> DefaultViewHolder(
                ItemTaskDefaultBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(
        holder: TaskViewHolder,
        position: Int
    ) {
        val task = getItem(position)
        holder.bind(task)

        holder.itemView.setOnClickListener { onItemClick?.invoke(task) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(task)
            true
        }
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            holder.bind(getItem(position), payloads)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return getItem(position).taskType.ordinal
    }

    inner class DefaultViewHolder(
        private val binding: ItemTaskDefaultBinding
    ) : TaskViewHolder(binding) {
        override fun bind(taskBean: TaskBean) {
            binding.apply {
                // 默认视图的绑定逻辑
                defaultImage.setImageResource(taskBean.resId)
            }
        }
    }

    inner class StepViewHolder(
        private val binding: ItemTaskStepBinding
    ) : TaskViewHolder(binding) {
        private val indicators = mutableListOf<ImageView>()
        private var currentPage = 0

        init {
            binding.taskStepVp.addOnPageChangeListener(createPageChangeListener())
        }

        override fun bind(taskBean: TaskBean) {
            val userMessages = taskBean.userMessages.take(4)
            val aiMessages = taskBean.aiMessages.take(4)

            setupViewPager(userMessages, aiMessages)
            initIndicators(userMessages.size)
        }

        override fun bind(taskBean: TaskBean, payloads: List<Any>) {
            if (payloads.any { it == "messages_changed" }) {
                val userMessages = taskBean.userMessages.take(4)
                val aiMessages = taskBean.aiMessages.take(4)

                (binding.taskStepVp.adapter as? TaskStepPageAdapter)?.updateData(
                    userMessages,
                    aiMessages
                )
                updateIndicatorsCount(userMessages.size)
            }
        }

        private fun setupViewPager(userMessages: List<ChatMessage>, aiMessages: List<ChatMessage>) {
            val adapter = TaskStepPageAdapter(binding.root.context, userMessages, aiMessages)
            binding.taskStepVp.adapter = adapter
        }

        private fun initIndicators(count: Int) {
            if (count <= 0) return

            binding.indicatorLayout.removeAllViews()
            indicators.clear()

            val indicatorW = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_23)
            val indicatorH = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_3)
            val dotMargin = ResourceProvider.get().getDimenPixelSize(R.dimen.dp_6)

            repeat(count) { index ->
                ImageView(binding.root.context).apply {
                    layoutParams = if (index > 0) {
                        createIndicatorLayoutParams(indicatorW, indicatorH, dotMargin)
                    } else {
                        LinearLayout.LayoutParams(indicatorW, indicatorH)
                    }
                    if (index == 0) {
                        setImageResource(R.drawable.task_indicator_selected)
                    } else {
                        setImageResource(R.drawable.task_indicator_unselected)
                    }
                    indicators.add(this)
                    binding.indicatorLayout.addView(this)
                }
            }
        }

        private fun createIndicatorLayoutParams(width: Int, height: Int, margin: Int) =
            LinearLayout.LayoutParams(width, height).apply {
                setMargins(margin, 0, 0, 0)
            }

        private fun updateIndicatorsCount(newCount: Int) {
            if (newCount != indicators.size) {
                initIndicators(newCount)
            }
        }

        private fun createPageChangeListener() = object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                currentPage = position
                updateIndicators(position)
            }
        }

        private fun updateIndicators(selectedPosition: Int) {
            indicators.forEachIndexed { index, imageView ->
                imageView.setImageResource(
                    if (index == selectedPosition) R.drawable.task_indicator_selected
                    else R.drawable.task_indicator_unselected
                )
            }
        }
    }

    inner class ProgressViewHolder(
        private val binding: ItemTaskProgressBinding
    ) : TaskViewHolder(binding) {

        override fun bind(taskBean: TaskBean) {
            binding.progressIv.setImageResource(taskBean.resId)
        }
    }

    fun getTaskAtPosition(position: Int): TaskBean? {
        return if (position in 0 until itemCount) getItem(position) else null
    }

    fun submitListWithAnimation(newList: List<TaskBean>?) {
        submitList(newList?.toList())
    }
}