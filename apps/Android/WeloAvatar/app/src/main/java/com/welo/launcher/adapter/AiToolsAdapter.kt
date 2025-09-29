import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.taobao.meta.avatar.R
import com.welo.launcher.entity.AIOptionItem

class AiToolsAdapter(
    private val options: List<AIOptionItem>,
    private val onItemClick: (optionItem: AIOptionItem, position: Int) -> Unit
) : RecyclerView.Adapter<AiToolsAdapter.ViewHolder>() {

    private val disabledPositions = setOf(3, 6, 7)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_tools, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(options[position])
    }

    override fun getItemCount(): Int = options.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val toolIcon: ImageView = itemView.findViewById(R.id.ai_tool_icon)
        val toolName: TextView = itemView.findViewById(R.id.ai_tool_title)
        val toolDesc: TextView = itemView.findViewById(R.id.ai_tool_desc)
        fun bind(option: AIOptionItem) {
            option.apply {
                toolIcon.setImageResource(iconResId)
                toolName.text = title
                toolDesc.text = desc
            }
            val isDisabled = disabledPositions.contains(adapterPosition)
            // 设置点击状态
            itemView.isClickable = !isDisabled
            itemView.isEnabled = !isDisabled
            if (isDisabled) {
                // 降低整个Item的透明度
                itemView.alpha = 0.5f
                // 也可以使用颜色滤镜使图标变灰
                toolIcon.setColorFilter(
                    android.graphics.Color.argb(150, 150, 150, 150),
                    android.graphics.PorterDuff.Mode.MULTIPLY
                )
            } else {
                // 恢复正常状态
                itemView.alpha = 1.0f
                toolIcon.clearColorFilter()
            }
                // 绑定数据到itemView
            if (!isDisabled) {
                itemView.setOnClickListener {
                    onItemClick(option, adapterPosition)
                }
            } else {
                itemView.setOnClickListener(null)
            }
        }
    }
}