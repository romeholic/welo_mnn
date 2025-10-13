package com.welo.launcher

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.taobao.meta.avatar.databinding.ActivityMediaBinding
import com.welo.base.BaseActivity
import com.welo.base.ImageLoader
import com.welo.launcher.viewmodel.HomeViewModel

class MediaActivity : BaseActivity<ActivityMediaBinding, HomeViewModel>() {

    companion object{
        const val EXTRA_MEDIA_URI = "input_URI"
        const val EXTRA_MEDIA_PATH = "input_path"
    }

    override fun createBinding(): ActivityMediaBinding {
        return ActivityMediaBinding.inflate(layoutInflater)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun initView() {

        val mediaUri = intent.extras?.getParcelable(EXTRA_MEDIA_URI, Uri::class.java)
        val mediaPath = intent.extras?.getString(EXTRA_MEDIA_PATH) ?: ""
        processMedia(mediaUri, mediaPath)

        viewBinding.ivDelete.setOnClickListener {
            finish()
        }
    }
    private fun processMedia(uri: Uri?, path: String) {
        when {
            uri != null -> {
                // 优先使用 URI
                ImageLoader.getInstance(this).load(uri,viewBinding.imageMedia)
            }
            path.isNotEmpty() -> {
                // 使用文件路径
                ImageLoader.getInstance(this).load(path,viewBinding.imageMedia)
            }
            else -> {
                // 没有传递参数的情况
            }
        }
    }

    override fun observeViewModel() {
    }
}