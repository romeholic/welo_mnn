package com.welo.launcher.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import com.taobao.meta.avatar.R
import com.welo.room.table.ChatMessage

class TaskStepPageAdapter(
    context: Context,
    private var userMessages: List<ChatMessage>,
    private var aiMessages: List<ChatMessage>
) : PagerAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = userMessages.size.coerceAtMost(aiMessages.size)

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = inflater.inflate(R.layout.item_task_page_content, container, false)

        val title = view.findViewById<TextView>(R.id.task_step_title)
        val content = view.findViewById<TextView>(R.id.task_step_content)

        title.text = userMessages.getOrNull(position)?.content ?: ""
        content.text = aiMessages.getOrNull(position)?.content ?: ""

        container.addView(view)
        return view
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    fun updateData(newUserMessages: List<ChatMessage>, newAiMessages: List<ChatMessage>) {
        userMessages = newUserMessages
        aiMessages = newAiMessages
        notifyDataSetChanged()
    }

    override fun getItemPosition(`object`: Any): Int {
        return POSITION_NONE
    }
}