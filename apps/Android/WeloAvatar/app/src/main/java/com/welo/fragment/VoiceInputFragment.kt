package com.welo.fragment

import android.annotation.SuppressLint
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.taobao.meta.avatar.databinding.FragmentVoiceInputBinding
import com.taobao.meta.avatar.llm.LlmPresenter
import com.welo.base.BaseFragment
import com.welo.base.ImageLoader
import com.welo.util.LoadScreenAnimUtil
import com.welo.viewmodel.MessageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语音输入
 * A simple [Fragment] subclass.
 * create an instance of this fragment.
 */
class VoiceInputFragment : BaseFragment<FragmentVoiceInputBinding, MessageViewModel>() {
    private lateinit var llmPresenter: LlmPresenter
    private var currentChatId: String = ""
    private val animationRes = listOf("animation_1.json","animation_2.json","animation_3.json","animation_4.json")

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentVoiceInputBinding {
        return FragmentVoiceInputBinding.inflate(inflater, container, false)
    }

    @SuppressLint("ResourceType")
    override fun initView() {
        llmPresenter = LlmPresenter(binding.tvOutput)
        binding.tvOutput.movementMethod = ScrollingMovementMethod()
        LoadScreenAnimUtil.instance.init(requireContext(), binding.animationView)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called")
        ImageLoader.Companion.getInstance(requireContext()).pauseGif(binding.animationView)
        binding.tvInput.text = ""   // 清空输入文本
        binding.tvOutput.text = ""  // 清空输出文本
        binding.tvOutput.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        ImageLoader.Companion.getInstance(requireContext()).clear(binding.animationView)
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            //输入的内容
            viewModel.sendData.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            ) .onEach { message ->
                withContext(Dispatchers.Main) {
                    if (message.isNotEmpty()) {
                        if (binding.tvInput.isGone) {
                            binding.tvInput.visibility = View.VISIBLE
                        }
                        if (binding.tvInput.text.isNotEmpty()){
                            binding.tvInput.text = ""
                        }
                        binding.tvInput.text = message
                    }
                }
            }.launchIn(viewLifecycleOwner.lifecycleScope)

            viewModel.collectedText.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            )
                .onEach { message ->
                    delay(100)
                    if (message.isNotEmpty()) {
                        if (binding.tvOutput.isGone) {
                            binding.tvOutput.visibility = View.VISIBLE
                        }
                        llmPresenter.onLlmTextUpdate(message, 0L)
                    }
                }.launchIn(viewLifecycleOwner.lifecycleScope)

            viewModel.collectedText.flowWithLifecycle(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.STARTED
            )
                .onEach { message ->
                    if (currentChatId != message) {
                        currentChatId = message
                        binding.tvOutput.text = ""  // 清空输出文本
                    }
                }.launchIn(viewLifecycleOwner.lifecycleScope)
        }
    }

    companion object {
        const val TAG = "VoiceInputFragment"
    }
}