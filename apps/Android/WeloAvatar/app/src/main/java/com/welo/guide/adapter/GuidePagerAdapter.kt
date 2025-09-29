package com.welo.guide.adapter

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.taobao.meta.avatar.R
import com.welo.guide.entity.UserLabelBean

class GuidePagerAdapter(
    private val guidePages: List<UserLabelBean>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var currentPosition = 0

    // 提供外部调用的方法，更新当前选中位置
    fun setCurrentPosition(position: Int) {
        if (currentPosition == position) return
        val oldPosition = currentPosition
        currentPosition = position
        notifyItemChanged(oldPosition) // 刷新旧位置
        notifyItemChanged(currentPosition) // 刷新新位置
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_guide_page, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {
        val deviceBean = guidePages[position]
        if (holder is ViewHolder) {
            holder.bind(deviceBean)
            holder.itemView.setOnClickListener {
            }
        }
    }

    override fun getItemCount(): Int {
        return guidePages.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: LottieAnimationView = itemView.findViewById(R.id.guide_title)
        val desc: TextView = itemView.findViewById(R.id.guide_desc)


        fun bind(page: UserLabelBean) {
            title.setAnimation("chatbot.json")
            title.repeatCount = ValueAnimator.INFINITE
            if (adapterPosition == currentPosition) { // 注意使用adapterPosition
                if (!title.isAnimating) {
                    title.playAnimation()
                }
            } else {
                if (title.isAnimating) {
                    title.pauseAnimation()
                }
            }

            desc.text = page.label
        }
    }
}