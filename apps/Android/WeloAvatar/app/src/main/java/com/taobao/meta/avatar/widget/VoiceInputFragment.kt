package com.taobao.meta.avatar.widget

import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.taobao.meta.avatar.base.BaseFragment
import com.taobao.meta.avatar.databinding.FragmentVoiceInputBinding
import com.taobao.meta.avatar.llm.LlmPresenter

/**
 * 语音输入
 * A simple [Fragment] subclass.
 * create an instance of this fragment.
 */
class VoiceInputFragment : BaseFragment<FragmentVoiceInputBinding, MessageViewModel>() {
    private lateinit var llmPresenter: LlmPresenter

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentVoiceInputBinding {
        return FragmentVoiceInputBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        llmPresenter = LlmPresenter(binding.tvOutput)
        binding.tvOutput.movementMethod = ScrollingMovementMethod()
    }

    override fun observeViewModel() {
        viewModel.sendData.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Log.d(TAG, "Received message: $message")
                if (binding.tvInput.text.isNotEmpty()){
                    binding.tvInput.text = ""
                }
                binding.tvInput.text = message
            }
        }
        viewModel.receivedData.observe(viewLifecycleOwner){message ->
            if (message.isNotEmpty()) {
                Log.d(TAG, "Received message: $message")
                if (binding.tvOutput.isGone) {
                    binding.tvOutput.visibility = View.VISIBLE
                }
                llmPresenter.onLlmTextUpdate(message, 0L)
            }
        }
    }

    companion object {
        const val TAG = "VoiceInputFragment"
    }
}