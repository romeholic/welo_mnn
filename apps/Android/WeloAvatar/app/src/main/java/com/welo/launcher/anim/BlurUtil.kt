package com.welo.launcher.anim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import androidx.core.graphics.createBitmap

object BlurUtil {
    /**
     * 将View转为模糊后的Bitmap
     * @param view 目标View（如lottieAnimationView）
     * @param radius 模糊半径（0-25，值越大越模糊）
     */
    fun blurView(view: View, radius: Float): Bitmap {
        // 1. 将View绘制到Bitmap上
        val bitmap = createBitmap(view.width, view.height)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        // 2. 使用RenderScript执行高斯模糊
        val rs = RenderScript.create(view.context)
        val input = Allocation.createFromBitmap(rs, bitmap)
        val output = Allocation.createTyped(rs, input.type)
        val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

        script.setRadius(radius)
        script.setInput(input)
        script.forEach(output)

        output.copyTo(bitmap)
        rs.destroy() // 释放资源
        return bitmap
    }
}