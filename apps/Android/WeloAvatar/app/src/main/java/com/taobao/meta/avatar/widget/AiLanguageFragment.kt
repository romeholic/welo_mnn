package com.taobao.meta.avatar.widget

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.base.BaseFragment
import com.taobao.meta.avatar.databinding.FragmentAiLanguageBinding


/**
 * 语言设置
 * A simple [Fragment] subclass.
 * create an instance of this fragment.
 */
class AiLanguageFragment : BaseFragment<FragmentAiLanguageBinding, GuideViewModel>() {

    private val navController: NavController by lazy {  findNavController() }
    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentAiLanguageBinding {
        return FragmentAiLanguageBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        binding.aiLanguageText.setColorText(resources.getString(R.string.ai_language),"追随者语言", Color.BLUE)
        val options = resources.getStringArray(R.array.ai_language_list)
        binding.languagePicker.apply {
            displayedValues = options
            value = 0
            minValue = 0
            maxValue = options.size - 1
            textColor = Color.WHITE
            dividerDrawable = null
            selectionDividerHeight = 0
            setOnScrollFinishedListener(500L) {selectedValue ->
                val selectedOption = options[selectedValue]
                Toast.makeText(context, "选择了: $selectedOption", Toast.LENGTH_SHORT).show()
                navController.navigate(R.id.action_languageSettingFragment_to_aiTypeFragment)
            }
        }
    }

    override fun observeViewModel() {
    }

    companion object {
        const val TAG = "LanguageSettingFragment"
    }
}