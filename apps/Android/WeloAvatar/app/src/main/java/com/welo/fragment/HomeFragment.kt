package com.welo.fragment

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.taobao.meta.avatar.R
import com.welo.base.ImageLoader
import com.welo.callback.IWheelPicker
import com.welo.entity.AppInfo
import com.welo.util.AppUtil
import com.welo.util.LogUtil
import com.welo.widget.WheelPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [HomeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class HomeFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var wheelPicker: WheelPicker
    private lateinit var cardView: CardView
    private lateinit var appIcon: ImageView
    private lateinit var appName: TextView
    private lateinit var appList: List<AppInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        wheelPicker = view.findViewById(R.id.view_pager)
        appIcon = view.findViewById(R.id.app_icon)
        appName = view.findViewById(R.id.app_name)
        cardView = view.findViewById(R.id.app_detail_card)
        cardView.background = resources.getDrawable(R.drawable.out_put_background, context?.theme)

        getAndUpdateAppList()
        return view
    }

    private fun getAndUpdateAppList() {
        lifecycleScope.launch {
            appList = withContext(Dispatchers.IO) {
                AppUtil.getInstalledApps()
            }
            // 准备应用数据
            val appItems: MutableList<Drawable> = ArrayList()
            for (app in appList) {
                appItems.add(app.appIcon)
            }

            wheelPicker.apply {

                setVisibleItemCount(5) // 显示5个项
                // 设置数据是否循环显示
                isCyclic = true
                //设置是否有指示器
                setIndicator(true)
                indicatorColor = Color.BLUE //16进制
                indicatorSize = 6 //单位是px
                // 设置是否有幕布，设置后选中项会被指定的颜色覆盖，默认false
                setCurtain(false)
                curtainColor = Color.CYAN
                // 设置是否有空气感，设置后上下边缘会渐变为透明，默认false
                setAtmospheric(true)
                setIndicatorHighlightColor(context.resources.getColor(R.color.colorPrimary))
                setIndicatorCornerRadius(15f)
                setOnWheelChangeListener(object : WheelPicker.OnWheelChangeListener {
                    override fun onWheelScrolled(offsetY: Int) {
                    }

                    override fun onWheelScrollStateChanged(state: Int) {
                    }

                })
                setOnItemSelectedListener(object : WheelPicker.OnItemSelectedListener {
                    override fun onItemSelected(
                        picker: WheelPicker?,
                        position: Int
                    ) {
                        LogUtil.d(TAG," Item selected: $position")
                        updateSelectedAppInfo(position)
                    }
                })

                imageData = appItems
            }
        }
    }

    private fun updateSelectedAppInfo(position: Int) {
        if (position < 0 || position >= appList.size) return
        val selectedApp = appList[position]
        ImageLoader.getInstance(appIcon.context).load(selectedApp.appIcon,appIcon)
        appName.text = selectedApp.appName
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment HomeFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }

        private const val TAG = "HomeFragment"
    }
}