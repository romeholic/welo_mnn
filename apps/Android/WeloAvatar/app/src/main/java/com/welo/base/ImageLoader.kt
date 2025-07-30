package com.welo.base

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
import java.io.File

/**
 * Glide图片加载工具类
 * 支持普通图片、GIF和Drawable加载，提供播放控制和Adapter便捷调用
 */
class ImageLoader private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ImageLoader? = null

        fun getInstance(context: Context): ImageLoader {
            return instance ?: synchronized(this) {
                instance ?: ImageLoader(context.applicationContext).also { instance = it }
            }
        }

        fun withViewLifecycle(context: Context): ImageLoader = ImageLoader(context)
    }

    // region 加载方法（新增Drawable直接加载）
    fun load(drawable: Drawable, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadDrawableInternal(drawable, imageView, options)

    fun load(url: String, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadImageInternal(url, imageView, options)

    fun load(file: File, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadImageInternal(file, imageView, options)

    fun load(@DrawableRes resId: Int, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadImageInternal(resId, imageView, options)

    fun load(uri: Uri, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadImageInternal(uri, imageView, options)

    // region GIF加载方法
    fun loadGif(url: String, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadGifInternal(url, imageView, options)

    fun loadGif(file: File, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadGifInternal(file, imageView, options)

    fun loadGif(@RawRes resId: Int, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadGifInternal(resId, imageView, options)

    fun loadGif(uri: Uri, imageView: ImageView, options: Options.() -> Unit = {}) =
        loadGifInternal(uri, imageView, options)

    // region 控制方法
    fun playGif(imageView: ImageView) {
        (imageView.drawable as? GifDrawable)?.start()
    }

    fun pauseGif(imageView: ImageView) {
        (imageView.drawable as? GifDrawable)?.stop()
    }

    fun clear(imageView: ImageView) {
        Glide.with(context).clear(imageView)
    }

    fun pauseRequests() {
        Glide.with(context).pauseRequests()
    }

    fun resumeRequests() {
        Glide.with(context).resumeRequests()
    }

    // region 配置选项
    data class Options(
        var placeholder: Drawable? = null,
        var error: Drawable? = null,
        var listener: RequestListener<Drawable>? = null,
        var diskCacheStrategy: DiskCacheStrategy = DiskCacheStrategy.ALL,
        var isCenterCrop: Boolean = false,
        var isFitCenter: Boolean = false,
        var isCircleCrop: Boolean = false,
        var overrideSize: Int = Target.SIZE_ORIGINAL,
        var skipMemoryCache: Boolean = false
    )

    // region 私有实现
    @SuppressLint("CheckResult")
    private fun loadDrawableInternal(
        drawable: Drawable,
        imageView: ImageView,
        options: Options.() -> Unit
    ) {
        val opts = Options().apply(options)
        getRequestBuilder(drawable, opts, imageView)
            .applyConfig(opts)
            .into(imageView)
    }

    @SuppressLint("CheckResult")
    private fun loadImageInternal(
        source: Any,
        imageView: ImageView,
        options: Options.() -> Unit
    ) {
        val opts = Options().apply(options)
        getRequestBuilder(source, opts, imageView)
            .diskCacheStrategy(opts.diskCacheStrategy)
            .applyConfig(opts)
            .into(imageView)
    }

    @SuppressLint("CheckResult")
    private fun loadGifInternal(
        source: Any,
        imageView: ImageView,
        options: Options.() -> Unit
    ) {
        val opts = Options().apply(options)
        getRequestBuilder(source, opts, imageView)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .applyConfig(opts)
            .into(imageView)
    }

    private fun RequestBuilder<Drawable>.applyConfig(options: Options): RequestBuilder<Drawable> {
        return this.apply {
            if (options.isCenterCrop) centerCrop()
            if (options.isFitCenter) fitCenter()
            if (options.isCircleCrop) circleCrop()
            if (options.overrideSize != Target.SIZE_ORIGINAL) {
                override(options.overrideSize)
            }
            options.placeholder?.let { placeholder(it) }
            options.error?.let { error(it) }
            options.listener?.let { addListener(it) }
            skipMemoryCache(options.skipMemoryCache)
        }
    }

    private fun getRequestBuilder(
        source: Any,
        options: Options,
        view: View
    ): RequestBuilder<Drawable> {
        return if (view.isAttachedToWindow) {
            Glide.with(view).load(source)
        } else {
            Glide.with(context).load(source)
        }.apply {
            options.placeholder?.let { placeholder(it) }
            options.error?.let { error(it) }
        }
    }
}