package com.welo.guide.fragment

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexboxLayout
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.databinding.FragmentGuideBinding
import com.welo.guide.adapter.GuidePagerAdapter
import com.welo.base.BaseFragment
import com.welo.base.gone
import com.welo.base.visible
import com.welo.guide.entity.UserLabelBean
import com.welo.util.LogUtil
import com.welo.guide.viewmodel.GuideViewModel
import kotlinx.coroutines.launch

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [GuideFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class GuideFragment : BaseFragment<FragmentGuideBinding, GuideViewModel>() {
    // TODO: Rename and change types of parameters
    private var param1: Int = 0
    private var param2: String? = null

    val iconRes = listOf(
        R.drawable.emoji_1,
        R.drawable.emoji_2,
        R.drawable.emoji_3,
        R.drawable.emoji_4,
        R.drawable.emoji_5,
        R.drawable.emoji_6,
        R.drawable.emoji_7,
        R.drawable.emoji_8,
        R.drawable.emoji_9
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getInt(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentGuideBinding = FragmentGuideBinding.inflate(inflater, container, false)

    override fun initView() {
        LogUtil.d(TAG, "newInstance param1:$param1 param2:$param2")
        viewModel.getCurrentPageData(param1)
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.currentPageData.collect { guidePageBean ->
                guidePageBean?.let {
                    when (it.type) {
                        0 -> {
                            binding.flexBox.gone()
                            binding.recyclerViewChat.gone()
                        }

                        1 -> {
                            setupViewPager(guidePageBean.userLabelBean)
                            binding.flexBox.gone()
                        }

                        2 -> {
                            setupLabel()
                            binding.recyclerViewChat.gone()
                        }
                    }
                    binding.guideTitle.text = it.title
                    binding.guideDesc.text = it.content
                }

            }
        }
    }

    /**
     * 设置大模型输出状态
     */
    private fun setupViewPager(pages: List<UserLabelBean>?) {
        if (pages == null) {
            return
        }
        binding.recyclerViewChat.visible()
        val adapter = GuidePagerAdapter(pages)
        binding.recyclerViewChat.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
            this.adapter = adapter

            onFlingListener = null

            val snapHelper = LinearSnapHelper()
            snapHelper.attachToRecyclerView(this)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    // 滚动停止时
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        // 获取当前居中的Item位置
                        val centerView = snapHelper.findSnapView(layoutManager)
                        centerView?.let {
                            val position = layoutManager?.getPosition(it) ?: -1
                            if (position != -1) {
                                adapter.setCurrentPosition(position)
                            }
                        }
                    }
                }
            })
        }
        adapter.setCurrentPosition(0)
    }

    /**
     * 设置用户标签
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setupLabel() {
        binding.flexBox.visible()
        resources.getStringArray(R.array.ai_type_list).forEachIndexed { index, item ->
            val labelView =
                layoutInflater.inflate(R.layout.item_user_label, binding.flexBox, false)
            val labelIcon = labelView.findViewById<ImageView>(R.id.label_icon)
            val labelName = labelView.findViewById<TextView>(R.id.label_name)
            val itemView = labelView.findViewById<LinearLayout>(R.id.item_bg_layout)
            labelName.text = item
            labelIcon.setImageResource(iconRes[index])

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                itemView.setRenderEffect(
                    RenderEffect.createBlurEffect(
                        15f,
                        15f,
                        Shader.TileMode.MIRROR
                    )
                )
            }
            val layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                FlexboxLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(0, 0, 0, resources.getDimensionPixelSize(R.dimen.dp_36))
            labelView.layoutParams = layoutParams

            binding.flexBox.addView(labelView)

            labelView.setOnClickListener {
//                Settings.System.putString(context?.contentResolver,"","")
            }
        }
    }

    companion object {
        private const val TAG = "GuideFragment"

        @JvmStatic
        fun newInstance(param1: Int, param2: String) =
            GuideFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}