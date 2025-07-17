package com.welo.fragment

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.taobao.meta.avatar.R
import com.welo.base.BaseFragment
import com.taobao.meta.avatar.databinding.FragmentAiTypeBinding
import com.welo.viewmodel.GuideViewModel
import com.welo.base.setColorText

/**
 * 追随者类型
 * create an instance of this fragment.
 */
class AiTypeFragment : BaseFragment<FragmentAiTypeBinding, GuideViewModel>() {

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
    private val navController: NavController by lazy {  findNavController() }
    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentAiTypeBinding {
        return FragmentAiTypeBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        binding.aiTypeText.setColorText(resources.getString(R.string.ai_type_title),"追随者类型", Color.BLUE)

        resources.getStringArray(R.array.ai_type_list).forEachIndexed { index, item ->
            val chip =
                layoutInflater.inflate(R.layout.ai_type_chip_item, binding.chipGroup, false) as Chip
            chip.apply {
                text = item
                setChipIconResource(iconRes[index])
                setOnCheckedChangeListener { _, isChecked ->
                    updateSelectedChipCount()
                }
            }
            binding.chipGroup.addView(chip)
        }
    }

    override fun observeViewModel() {

    }

    override fun showToolBar(): Boolean = true
    override fun onToolbarClickListener(): ToolbarClickListener? {
        return object : ToolbarClickListener{
            override fun onToolBarClick() {
                navController.navigateUp()
            }
        }
    }

    /**
     * 获取选中的个数与对应的信息
     */
    private fun updateSelectedChipCount() {
        val checkedChipIds = binding.chipGroup.checkedChipIds
        val count = checkedChipIds.size

        Log.d(TAG, "已选中: $count 个")

        // 打印选中的 Chip 文本
        checkedChipIds.forEach { chipId ->
            val chip = binding.chipGroup.findViewById<Chip>(chipId)
            Log.d(TAG, "选中的 Chip: ${chip.text}")
        }
        if (count >= 3){
            navController.navigate(R.id.action_aiTypeFragment_to_aiImageFragment)
        }
    }

    companion object {
        const val TAG = "AiTypeFragment"
    }
}