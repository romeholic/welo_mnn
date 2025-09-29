package com.welo.launcher.fragment

import android.animation.Animator
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.FragmentMapMemoryBinding
import com.welo.base.BaseFragment
import com.welo.launcher.viewmodel.HomeViewModel
import com.welo.widget.DashedLineView
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class MemoryMapFragment : BaseFragment<FragmentMapMemoryBinding, HomeViewModel>() {
    private var param1: String? = null
    private var param2: String? = null
    private val random = Random()
    private val icons = listOf(
        R.drawable.m_city to "城市",
        R.drawable.m_sport to "运动",
        R.drawable.m_character to "性格",
        R.drawable.m_favorate to "爱好",
        R.drawable.m_show to "演出",
        R.drawable.m_relationship to "关系"
    )
    private val random_button_text = mutableListOf(
        "棒球",
        "登山",
        "骑行",
        "户外",
        "徒步",
        "游泳",
        "跑步",
        "武术"
    )
    private val excludedAreaRatio = 0.4f
    private lateinit var excludedRect: Rect
    private lateinit var rootLayout: ConstraintLayout
    private lateinit var rootframe_bg: FrameLayout
    private lateinit var rootframe: FrameLayout


    private val menu_list: MutableList<ConstraintLayout> = mutableListOf()
    private val random_menu_list: MutableList<TextView> = mutableListOf()
    private var select_item: Int = -1
    private var view_is_moving: Boolean = false
    private val random_button_count = 8
    private lateinit var back: RelativeLayout
    private var move_item_x: Float = 0f
    private var move_item_y: Float = 0f


    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMapMemoryBinding {
        return FragmentMapMemoryBinding.inflate(layoutInflater)
    }

    override fun initView() {
        binding.rootLayout
        rootLayout = binding.rootLayout
        rootframe = binding.rootframe
        rootframe_bg = binding.rootframeBg
        back = binding.mBack
        back.visibility = View.INVISIBLE
        setupCircularMenu()
        setAnimate()
        setUpback()
    }

    override fun observeViewModel() {

    }

    private fun setUpback() {
        back.setOnClickListener { view ->
            //移动圆环页面
            smoothMoveRootframe(0f, 0f, true)
            //显示虚线
            var l: DashedLineView = binding.line
            l.updateLine(-1)
            //按钮复位
            val movetotop_targetX: Float = 140.dpToPx().toFloat()
            val movetotop_targetY: Float = -100.dpToPx().toFloat()
            menu_list.forEachIndexed { idx, constraintLayout ->
                constraintLayout.visibility = View.VISIBLE
                if (select_item == idx) {
                    constraintLayout.translationX = move_item_x
                    constraintLayout.translationY = move_item_y
                }

                constraintLayout.animate()
                    .setListener(object : Animator.AnimatorListener {
                        override fun onAnimationCancel(animation: Animator) {
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            view_is_moving = false
                            select_item = -1
                        }

                        override fun onAnimationRepeat(animation: Animator) {

                        }

                        override fun onAnimationStart(animation: Animator) {
                            view_is_moving = true
                        }

                    })
                    .x(constraintLayout.x - movetotop_targetX)
                    .y(constraintLayout.y - movetotop_targetY)
                    .setDuration(2000)
                    .setInterpolator(OvershootInterpolator(0f))
                    .start()

            }
            //random按钮不可见
            for (i in 0 until random_menu_list.size) {
                back.visibility = View.INVISIBLE
                random_menu_list[i].visibility = View.INVISIBLE
            }
        }
    }

    private fun smoothMoveRootframe(targetX: Float, targetY: Float, back: Boolean) {
        rootframe.animate().setListener(object : Animator.AnimatorListener {
            override fun onAnimationCancel(animation: Animator) {
            }

            override fun onAnimationEnd(animation: Animator) {
                if (back) {
                    view_is_moving = false
                }
            }

            override fun onAnimationRepeat(animation: Animator) {

            }

            override fun onAnimationStart(animation: Animator) {
                view_is_moving = true
            }

        })
            .x(targetX)
            .y(targetY)
            .setDuration(2000)
            .setInterpolator(OvershootInterpolator(0f))
            .start()
    }

    private fun doUpdateViewAction(view: View) {
        menu_list.forEachIndexed { idx, constraintLayout ->
            if (select_item == idx) constraintLayout.visibility =
                View.VISIBLE else constraintLayout.visibility = View.INVISIBLE
            if (idx < menu_list.size) {
                //按需求固定显示在2号位，不用abs取setp，因为可能是反向转角
                var move_step: Int = select_item - 2
                rootframe.rotation += move_step * 60
                rootframe.rotation %= 360f

                if (select_item == idx) {
                    move_item_x = constraintLayout.x + 140.dpToPx().toFloat()
                    move_item_y = constraintLayout.y - 100.dpToPx().toFloat()
                    constraintLayout.translationX = menu_list[2].x
                    constraintLayout.translationY = menu_list[2].y
                }
            }
        }
        var l: DashedLineView = binding.line
        l.updateLine(2)
    }

    private fun setAnimate() {
        val animation_view_list = listOf(
            binding.centerAvatar,
            binding.circle3,
            binding.circleYellowBg,
            binding.circle2,
            binding.circle1,
            binding.line
        )
        var fadeIn: Animation = AnimationUtils.loadAnimation(requireActivity(), R.anim.fade_in)
//        var fadeOut: Animation = AnimationUtils.loadAnimation(this, R.anim.fade_out)
        animation_view_list.forEachIndexed { index, view ->
            view.visibility = View.VISIBLE
            view.startAnimation(fadeIn)
            fadeIn.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationEnd(animation: Animation?) {
//                usr.startAnimation(fadeOut)
                    if (index < animation_view_list.size - 1) {
                        animation_view_list[index + 1]
                            .startAnimation(fadeIn)
                    }
                }

                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
    }

    private fun updateCircularMenu(view: View) {
        val movetotop_targetX: Float = 140.dpToPx().toFloat()
        val movetotop_targetY: Float = -100.dpToPx().toFloat()

        doUpdateViewAction(rootframe)
        smoothMoveRootframe(movetotop_targetX, movetotop_targetY, false)
        smoothMoveSelectedMenu(select_item)
    }

    private fun smoothMoveSelectedMenu(select_menu_idx: Int) {
        val movetotop_targetX: Float = 140.dpToPx().toFloat()
        val movetotop_targetY: Float = -100.dpToPx().toFloat()
        menu_list.forEachIndexed { idx, constraintLayout ->
//            if (select_menu_idx == idx) {
            constraintLayout.animate()
                .setListener(object : Animator.AnimatorListener {
                    override fun onAnimationCancel(animation: Animator) {
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        view_is_moving = false
                    }

                    override fun onAnimationRepeat(animation: Animator) {

                    }

                    override fun onAnimationStart(animation: Animator) {
                        view_is_moving = true
                    }

                })
                .x(constraintLayout.x + movetotop_targetX)
                .y(constraintLayout.y + movetotop_targetY)
                .setDuration(2000)
                .setInterpolator(OvershootInterpolator(0f))
                .start()
//            }
        }
    }

    private fun setupCircularMenu() {
        val centerX = 207.dpToPx()
        val centerY = 220.dpToPx()
        val radius = 130.dpToPx() // 图标距离中心的半径（像素）
        select_item = -1
        menu_list.clear()
        icons.forEachIndexed { index, (iconRes, text) ->
            // 计算每个图标的位置（绕中心点均匀分布）
            val angle = Math.toRadians((index * 60).toDouble()) // 6个图标，每个间隔60度
            val x = centerX + radius * cos(angle)
            val y = centerY + radius * sin(angle)
            var iconImageFadeIn: Animation =
                AnimationUtils.loadAnimation(requireActivity(), R.anim.fade_in)
//            var iconTextfadeOut:Animation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
            // 创建图标视图
            var iconView: ConstraintLayout =
                layoutInflater.inflate(R.layout.memory_main_menu, null) as ConstraintLayout

            var main = iconView.findViewById<ConstraintLayout>(R.id.menu_main)
            val iconImage = iconView.findViewById<ImageView>(R.id.iconImage)
            val iconText = iconView.findViewById<TextView>(R.id.iconText)

            iconImage.setImageResource(iconRes)
            iconText.text = text

// 设置位置

            iconView.x = x.toFloat() - iconView.width / 2
            iconView.y = y.toFloat() - iconView.height / 2
            iconImage.visibility = View.VISIBLE
            iconText.visibility = View.VISIBLE
            iconImageFadeIn.duration = 2000
            main.startAnimation(iconImageFadeIn)
            main.setBackgroundResource(R.drawable.m_label_bg)
            rootLayout.addView(iconView)
            menu_list.add(main)
//           设置menu item点击
            menu_list[index].setOnClickListener { view ->
                if (select_item == -1 && view_is_moving == false) {
                    back.visibility = View.VISIBLE
                    select_item = index
                    updateCircularMenu(iconView)
                    setupAroundMenu()
                }
            }
        }
    }

    private fun setupAroundMenu() {
        // 创建并显示按钮
        rootframe_bg.post {
            val screenWidth = rootframe_bg.width
            val screenHeight = rootframe_bg.height
            val excludedWidth = (screenWidth * excludedAreaRatio).toInt()
            val excludedHeight = (screenHeight * excludedAreaRatio).toInt()

            excludedRect = Rect(
                screenWidth - excludedWidth,
                0,
                screenWidth,
                excludedHeight
            )
            // 开始创建按钮
            createRandomButtons(rootframe_bg)
        }
    }

    private fun createRandomButtons(container: FrameLayout) {

        val buttonCount = random_button_count
        val buttonSize = 70.dpToPx()
        val margin = 8.dpToPx()
        val placedButtons = mutableListOf<Rect>()

        for (i in 0 until buttonCount) {
            createNonOverlappingButton(container, i, buttonSize, margin, placedButtons)
        }
    }

    private fun createNonOverlappingButton(
        container: FrameLayout,
        index: Int,
        buttonSize: Int,
        margin: Int,
        placedButtons: MutableList<Rect>
    ) {
        val delay = random.nextInt(2000).toLong()

        container.postDelayed({
            val containerWidth = container.width
            val containerHeight = container.height

            val maxAttempts = 100
            var attempts = 0
            var positionFound = false
            var left = 0
            var top = 0

            while (attempts < maxAttempts && !positionFound) {
                // 生成随机位置
                left = random.nextInt(containerWidth - buttonSize)
                top = random.nextInt(containerHeight - buttonSize)

                // 检查是否在排除区域内
                val buttonRect = Rect(left, top, left + buttonSize, top + buttonSize)
                if (Rect.intersects(buttonRect, excludedRect)) {
                    attempts++
                    continue
                }

                // 检查是否与其他按钮重叠
                val buttonRectWithMargin = Rect(
                    left - margin,
                    top - margin,
                    left + buttonSize + margin,
                    top + buttonSize + margin
                )

                var overlaps = false
                for (rect in placedButtons) {
                    if (Rect.intersects(buttonRectWithMargin, rect)) {
                        overlaps = true
                        break
                    }
                }

                if (!overlaps) {
                    positionFound = true
                    placedButtons.add(buttonRectWithMargin)
                }

                attempts++
            }

            if (positionFound) {
                createButton(container, index, left, top)
            } else {
                // 如果找不到合适位置，尝试在非排除区域强制放置
                val safeLeft = random.nextInt(containerWidth - buttonSize - excludedRect.width())
                val safeTop = random.nextInt(containerHeight - buttonSize)
                createButton(container, index, safeLeft, safeTop)
            }
        }, delay)
    }

    private fun createButton(
        container: FrameLayout,
        index: Int,
        left: Int,
        top: Int,
    ) {
        val button = TextView(requireActivity()).apply {
            text = random_button_text[index]//button和文本内容的list size一样
            setBackgroundResource(R.drawable.m_radombutton_bg)
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(12f)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.leftMargin = left
                this.topMargin = top
            }
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
            gravity = Gravity.CENTER
        }
        random_menu_list.add(button)
        container.addView(button)

        button.setOnClickListener { view ->
            val menuFragment = MemoryMapBottomFragment.newInstance()
            menuFragment.show(requireActivity().supportFragmentManager, "bottom_menu")
        }
        button.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator())
            .start()
    }


    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()

        rootframe.animate().cancel()

        menu_list.forEach {
            it.animate().cancel()
        }
        random_menu_list.forEach {
            it.animate().cancel()
        }

        rootLayout.removeAllViews()
        rootframe_bg.removeAllViews()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MemoryMapFragment.
         */
        // TODO: Rename and change types and number of parameters
        private const val REQUEST_CAMERA_PERMISSION = 100
        private const val TAG = "Mapmemory"

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MemoryMapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}