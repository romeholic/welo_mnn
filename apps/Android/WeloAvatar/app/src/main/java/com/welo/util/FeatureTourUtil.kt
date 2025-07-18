package com.welo.util

import FeatureTour
import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import com.taobao.meta.avatar.MainActivity
import com.taobao.meta.avatar.databinding.ActivityMainWeLoBinding

object FeatureTourUtil {
    /**
     * 功能引导是否完成
     */
    private const val FEATURE_TOUR_KEY = "feature_tour_key"
    @SuppressLint("StaticFieldLeak")
    private var featureTour: FeatureTour? = null

    fun featureTour(activity: Activity, viewBinding: ActivityMainWeLoBinding) {
        if (PreferenceUtil.get().getBoolean(FEATURE_TOUR_KEY)){
            Log.d("FeatureTourUtil", "功能引导已显示，跳过")
            return
        }
        if (featureTour != null) {
            Log.d("FeatureTourUtil", "功能引导已存在，跳过")
            return
        }
        featureTour = FeatureTour(activity)
        featureTour?.let {
            it.addStep(
                FeatureTour.Step(
                    targetView = viewBinding.multifunctionalIv,
                    message = "多功能按钮，点击查看功能",
                    onClick = {
                        it.next()
                    }
                )
            )
                .addStep(
                    FeatureTour.Step(
                        targetView = viewBinding.waveFormView,
                        message = "语音输入模式，提示语音输入状态",
                        onClick = {
                            it.next()
                        }
                    )
                ).addStep(
                    FeatureTour.Step(
                        targetView = viewBinding.inputModelChange,
                        message = "此按钮进行文本、语音输入模式切换",
                        onClick = {
                            it.next()
                        }
                    )
                )
                .addStep(
                    FeatureTour.Step(
                        targetView = viewBinding.inputStateChange,
                        message = "长按此按钮，可进行语音输入",
                        onClick = {
                            it.next()
                        }
                    )
                )
                .setOnTourCompletedListener {
                    PreferenceUtil.get().putBoolean(FEATURE_TOUR_KEY, true)
                }
                .setOnStepChangedListener { index, step ->
                    Log.d("", "当前步骤: ${index + 1}")
                }.start()
        }

    }
}