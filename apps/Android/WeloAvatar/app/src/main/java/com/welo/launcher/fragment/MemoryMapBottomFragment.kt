import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.taobao.meta.avatar.R

class MemoryMapBottomFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.memory_menu_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenuItems()
        setupClickListeners()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)

        // 设置从底部弹出
        dialog.window?.setWindowAnimations(R.style.BottomSheetAnimation)
        dialog.window?.setBackgroundDrawableResource(R.drawable.bottom_sheet_background)
        // 点击外部可关闭
        dialog.setCanceledOnTouchOutside(true)



        return dialog
    }

    private fun setupMenuItems() {
        val recyclerView = view?.findViewById<RecyclerView>(R.id.rv_menu_items)
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())

        val menuItems = listOf(
            "李文目前在一家做AI硬件产品的公司担任交互设计师",
            "李文目前在一家做AI硬件产品的公司担任交互设计师，负责名为“Nexa”的产品交互设计。李文目前在一家做AI硬件产品的公司担任交互设计师，负责名为“Nexa”的产品交互设计",
            "李文目前在一家做AI硬件产品的公司担任交互设计师，负责名为“Nexa”的产品交互设",
            "李文目前在一家做AI硬件产品的公司担任交互设计师，负责名为“Nexa”的产品交互设计",
            "李文目前在一家做AI硬件产品的公司担任交互设计师，负责名为“Nexa”的产品交互设计和其他产品的设计"
        )

        recyclerView?.adapter = object : RecyclerView.Adapter<MenuViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.memory_menu_item, parent, false)
                return MenuViewHolder(view)
            }

            override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
                holder.bind(menuItems[position])
            }

            override fun getItemCount() = menuItems.size
        }
    }

    private fun setupClickListeners() {
        // 点击顶部关闭区域关闭菜单
        view?.findViewById<View>(R.id.drag_handle)?.setOnClickListener {
            dismiss()
        }

        // 点击顶部背景也可关闭
        view?.setOnClickListener {
            dismiss()
        }
    }

    class MenuViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView as TextView

        fun bind(item: String) {
            textView.text = item
            textView.setOnClickListener {
                // 处理菜单项点击
                onMenuItemClicked(item)
            }
        }

        private fun onMenuItemClicked(item: String) {
            // 这里处理菜单项点击事件
//            dismiss()
        }
    }

    companion object {
        fun newInstance(): MemoryMapBottomFragment {
            return MemoryMapBottomFragment()
        }
    }
}