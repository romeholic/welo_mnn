package com.taobao.meta.avatar.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

/**
 * Glide图片加载工具类
 * 支持普通图片和GIF加载，提供播放控制功能
 */
class ImageLoader private constructor(private val context: Context){
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ImageLoader? = null

        /** 使用 Application Context 创建实例（适合全局单例） */
        fun getInstance(context: Context): ImageLoader {
            return instance ?: synchronized(this) {
                instance ?: ImageLoader(context.applicationContext).also { instance = it }
            }
        }

        /** 使用 View 生命周期创建实例（适合需要感知生命周期的场景） */
        fun withViewLifecycle(context: Context): ImageLoader = ImageLoader(context)
    }

    // region 普通图片加载方法
    fun loadImage(url: String, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadImageInternal(url, imageView, options)

    fun loadImage(filePath: java.io.File, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadImageInternal(filePath, imageView, options)

    fun loadImage(@DrawableRes resId: Int, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadImageInternal(resId, imageView, options)

    fun loadImage(uri: Uri, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadImageInternal(uri, imageView, options)


    // region GIF图片加载方法
    fun loadGif(url: String, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadGifInternal(url, imageView, options)

    fun loadGif(filePath: java.io.File, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadGifInternal(filePath, imageView, options)

    fun loadGif(@RawRes resId: Int, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadGifInternal(resId, imageView, options)

    fun loadGif(uri: Uri, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadGifInternal(uri, imageView, options)

    /** 播放GIF */
    fun playGif(imageView: ImageView) {
        val drawable = imageView.drawable
        if (drawable is GifDrawable) {
            if (!drawable.isRunning) {
                drawable.start()
            }
        }
    }

    /** 暂停GIF */
    fun pauseGif(imageView: ImageView) {
        val drawable = imageView.drawable
        if (drawable is GifDrawable) {
            if (drawable.isRunning) {
                drawable.stop()
            }
        }
    }

    /** 清除加载请求并释放资源 */
    fun clear(imageView: ImageView) {
        Glide.with(context).clear(imageView)
    }
    /** 清除所有加载请求并释放资源 */
    fun pauseRequest(){
        Glide.with(context).pauseRequests()
    }
    /** 恢复所有加载请求 */
    fun resumeRequest(){
        Glide.with(context).resumeRequests()
    }

    /** 图片加载配置选项 */
    data class Options(
        var placeholder: Drawable? = null,
        var error: Drawable? = null,
        var listener: RequestListener<Drawable>? = null,
        var diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.ALL,
        var isCenterCrop: Boolean = false,
        var isFitCenter: Boolean = false,
        var isCircleCrop: Boolean = false,
        var overrideWidth: Int = Target.SIZE_ORIGINAL,
        var overrideHeight: Int = Target.SIZE_ORIGINAL
    )

    // 私有内部实现方法
    @SuppressLint("CheckResult")
    private fun loadImageInternal(
        source: Any,
        imageView: ImageView,
        options: Options.() -> Unit,
    ) {
        val opts = Options().apply(options)
        getRequestBuilder(source, opts, imageView)
            .diskCacheStrategy(opts.diskCacheStrategy)
            .apply {
                if (opts.isCenterCrop) centerCrop()
                if (opts.isFitCenter) fitCenter()
                if (opts.isCircleCrop) circleCrop()
                if (opts.overrideWidth != Target.SIZE_ORIGINAL || opts.overrideHeight != Target.SIZE_ORIGINAL) {
                    override(opts.overrideWidth, opts.overrideHeight)
                }
            }
            .into(imageView)
    }

    @SuppressLint("CheckResult")
    private fun loadGifInternal(
        source: Any,
        imageView: ImageView,
        options: Options.() -> Unit,
    ) {
        val opts = Options().apply(options)
        getRequestBuilder(source, opts, imageView)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .apply {
                if (opts.isCenterCrop) centerCrop()
                if (opts.isFitCenter) fitCenter()
                if (opts.isCircleCrop) circleCrop()
                if (opts.overrideWidth != Target.SIZE_ORIGINAL || opts.overrideHeight != Target.SIZE_ORIGINAL) {
                    override(opts.overrideWidth, opts.overrideHeight)
                }
            }
            .into(imageView)
    }

    @SuppressLint("CheckResult")
    private fun getRequestBuilder(
        source: Any,
        options: Options,
        view: View
    ): RequestBuilder<Drawable> {
        val requestManager = if (view.isAttachedToWindow) {
            Glide.with(view) // 使用 View 的生命周期
        } else {
            Glide.with(context) // 使用 Application Context（需要手动清理）
        }

        return requestManager.load(source)
            .apply {
                options.placeholder?.let { placeholder(it) }
                options.error?.let { error(it) }
                options.listener?.let { addListener(it) }
            }
    }
}