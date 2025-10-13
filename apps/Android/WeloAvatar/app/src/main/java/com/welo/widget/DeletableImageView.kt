package com.welo.widget

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import com.google.android.material.imageview.ShapeableImageView
import com.taobao.meta.avatar.R
import com.welo.base.ImageLoader
import com.welo.util.JumpUtil
import com.welo.util.LogUtil

class DeletableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val imageView: ShapeableImageView
    private val deleteButton: ImageView
    private val progressBar: ProgressBar
    private val overlay: View
    private var currentUri: Uri? = null
    private var currentUrl: String? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_deletable_image, this, true)
        imageView = findViewById(R.id.imageView)
        deleteButton = findViewById(R.id.closeBtn)
        progressBar = findViewById(R.id.progressBar)
        overlay = findViewById(R.id.overlay)

        deleteButton.setOnClickListener {
            ImageLoader.getInstance(context).clear(imageView)
            // 可选：移除自身视图
            (parent as? android.view.ViewGroup)?.removeView(this)
        }

        imageView.setOnClickListener {
            JumpUtil.jumpMediaActivity(context,currentUri,currentUrl)
        }
    }

    fun setImageResource(resId: Int) {
        imageView.setImageResource(resId)
    }

    fun setImageUri(uri: Uri) {
        currentUri = uri
        ImageLoader.getInstance(context).load(uri, imageView)
    }

    fun getImageUri(): Uri? = currentUri

    fun setUploadInProgress(showProgress: Boolean) {
        progressBar.visibility = if (showProgress) VISIBLE else GONE
        overlay.visibility = if (showProgress) VISIBLE else GONE
        if (showProgress) {
            progressBar.progress = 0
        }
    }

    fun setUploadProgress(progress: Int) {
        progressBar.progress = progress
        if (progress >= 100) {
            // 上传完成，隐藏进度条
            progressBar.visibility = GONE
            overlay.visibility = GONE
        }
    }

    fun setImageUrl(url: String, showDeleteIcon: Boolean = true) {
        currentUrl = url
        deleteButton.visibility = if (showDeleteIcon) VISIBLE else GONE
        ImageLoader.getInstance(context).load(url, imageView)
        LogUtil.d("DeletableImageView", "Loading image from URL: $url")
    }

    fun setOnDeleteClickListener(listener: OnClickListener) {
        deleteButton.setOnClickListener(listener)
    }
}