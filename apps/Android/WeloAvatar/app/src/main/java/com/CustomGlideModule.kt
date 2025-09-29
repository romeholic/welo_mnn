package com

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.bitmap.Downsampler
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.localebro.okhttpprofiler.OkHttpProfilerInterceptor
import com.welo.base.net.HeaderInterceptor
import java.io.InputStream
import okhttp3.OkHttpClient


@GlideModule
class CustomGlideModule: AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val client: OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(HeaderInterceptor())
            .addInterceptor(OkHttpProfilerInterceptor())
            .build()


        // 替换 Glide 默认的网络组件
        registry.replace(
            GlideUrl::class.java, InputStream::class.java,
            OkHttpUrlLoader.Factory(client)
        )
    }
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // 禁用视频解码器
        builder.setDefaultRequestOptions(
            RequestOptions()
                .set(Downsampler.ALLOW_HARDWARE_CONFIG, true)
        )
    }

    // 禁用视频相关的解码器
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}