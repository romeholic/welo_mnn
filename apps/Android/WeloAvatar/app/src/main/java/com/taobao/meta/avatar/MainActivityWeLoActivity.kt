package com.taobao.meta.avatar

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.alibaba.mls.api.ApplicationProvider
import com.taobao.meta.avatar.base.BaseActivity
import com.taobao.meta.avatar.databinding.ActivityMainWeLoBinding
import com.taobao.meta.avatar.download.DownloadCallback
import com.taobao.meta.avatar.download.DownloadModule
import com.taobao.meta.avatar.llm.LlmService
import com.taobao.meta.avatar.widget.MessageViewModel
import kotlinx.coroutines.launch
import java.io.File

class MainActivityWeLoActivity : BaseActivity<ActivityMainWeLoBinding, MessageViewModel>(), DownloadCallback{

    /**
     * 底部导航栏高度
     */
    private val navBarHeight: Int
        get() {
            val insets = ViewCompat.getRootWindowInsets(viewBinding.root)
            return insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        }
    private lateinit var navController: NavController
    private var isVoiceInput = true
    private lateinit var downloadManager: DownloadModule

    companion object {
        private const val TAG = "WeLo#MainActivityWeLoActivity"
        init {
            System.loadLibrary("taoavatar")
        }
    }

    override fun createBinding(): ActivityMainWeLoBinding {
        return ActivityMainWeLoBinding.inflate(layoutInflater)
    }

    private fun loadLLMModel() {
        lifecycleScope.launch {
        }
    }

    override fun initView() {
        ApplicationProvider.set(application)

        downloadManager = DownloadModule(this)
        downloadManager.setDownloadCallback(this)
        MHConfig.BASE_DIR = downloadManager.getDownloadPath()

        loadLLMModel()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.input_container) as NavHostFragment
        navController = navHostFragment.navController

        initListener()
    }

    override fun observeViewModel() {

    }

    private fun processAsrText(inputText: String){
        lifecycleScope.launch {
        }
    }

    private fun initListener() {
       /* viewBinding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = Rect()
            viewBinding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = viewBinding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val marginBottom = viewBinding.buttonEndCall.marginBottom

            if (keypadHeight > screenHeight * 0.15) {
                // 键盘弹出，编辑框上移
                viewBinding.root.translationY = -keypadHeight.toFloat() + marginBottom + navBarHeight/2
            } else {
                // 键盘收起，恢复原位
                viewBinding.root.translationY = 0f
            }

        }
        viewBinding.buttonToggleText.setOnClickListener {
            if (isVoiceInput){
                viewBinding.waveFormView.startAnimation()
            }else{
                navController.navigate(R.id.action_text_to_voice)
                viewBinding.waveFormView.visibility = View.VISIBLE
                viewBinding.keyBordInput.visibility = View.GONE
                isVoiceInput = true
            }
        }
        viewBinding.buttonEndCall.setOnClickListener {
            if (isVoiceInput){
                navController.navigate(R.id.action_voice_to_text)
                viewBinding.waveFormView.stopAnimation()
                viewBinding.waveFormView.visibility = View.GONE
                viewBinding.keyBordInput.visibility = View.VISIBLE
                isVoiceInput = false
            }else{
                val inputMessage = viewBinding.keyBordInput.text.trim().toString()
                if (inputMessage.isEmpty()) return@setOnClickListener
                viewBinding.keyBordInput.apply {
                    text.clear()
                    isEnabled = false
                    postDelayed({
                        isEnabled = true
                    },5000)
                }
                viewModel.sendMessage(inputMessage)

            }
        }*/
        viewBinding.waveFormView.setOnClickListener {
            viewModel.sendMessage("这是一个测试消息")
            it.postDelayed({
                viewModel.receivedMessage("反馈给第一条的消息")
            },2000)
        }
    }

    override fun onDownloadStart() {
        Log.d(TAG, "onDownloadStart: Download started")
    }

    override fun onDownloadProgress(
        progress: Double,
        currentBytes: Long,
        totalBytes: Long,
        speedInfo: String
    ) {
        Log.d(TAG, "onDownloadProgress: progress=$progress, currentBytes=$currentBytes, totalBytes=$totalBytes, speedInfo=$speedInfo")
    }

    override fun onDownloadComplete(success: Boolean, file: File?) {
        Log.d(TAG, "onDownloadComplete: success=$success, file=${file?.absolutePath}")
    }

    override fun onDownloadError(error: Exception?) {
        Log.d(TAG, "onDownloadError: Download error:${error?.message}")
    }
}