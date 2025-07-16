package com.taobao.meta.avatar.widget

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.base.BaseFragment
import com.taobao.meta.avatar.databinding.FragmentAiImageBinding


/**
 * 追随者形象
 * A simple [Fragment] subclass.
 * create an instance of this fragment.
 */
class AiImageFragment : BaseFragment<FragmentAiImageBinding, GuideViewModel>() {
    private val navController: NavController by lazy {  findNavController() }
    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentAiImageBinding {
       return FragmentAiImageBinding.inflate(inflater, container, false)
    }

    override fun initView() {
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

    companion object {
        const val TAG = "AiImageFragment"
    }
}