package com.taobao.meta.avatar.widget

import android.os.Bundle
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

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"


/**
 * 语音输入
 * A simple [Fragment] subclass.
 * Use the [VoiceInputFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class VoiceInputFragment : BaseFragment<FragmentVoiceInputBinding, MessageViewModel>() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var llmPresenter: LlmPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: VoiceInputFragment created")
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

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
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment VoiceInputFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            VoiceInputFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }

        const val TAG = "VoiceInputFragment"
    }
}