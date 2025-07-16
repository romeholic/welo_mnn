package com.taobao.meta.avatar.widget

import FeatureTour
import android.annotation.SuppressLint
import android.util.Log
import com.taobao.meta.avatar.MainActivity
import com.taobao.meta.avatar.databinding.ActivityMainWeLoBinding

object FeatureTourUtil {
    private const val FEATURE_TOUR_KEY = "feature_tour_key"
    @SuppressLint("StaticFieldLeak")
    private lateinit var featureTour: FeatureTour

    fun featureTour(activity: MainActivity, viewBinding: ActivityMainWeLoBinding) {
        if (PreferenceUtil.get().getBoolean(FEATURE_TOUR_KEY)){
            Log.d("FeatureTourUtil", "功能引导已显示，跳过")
            return
        }
        FeatureTour(activity).addStep(
            FeatureTour.Step(
                targetView = viewBinding.multifunctionalIv,
                message = "多功能按钮，点击查看功能",
                onClick = {
                    featureTour.next()
                }
            )
        )
            .addStep(
                FeatureTour.Step(
                    targetView = viewBinding.waveFormView,
                    message = "语音输入模式，提示语音输入状态",
                    onClick = {
                        featureTour.next()
                    }
                )
            ).addStep(
                FeatureTour.Step(
                    targetView = viewBinding.inputModelChange,
                    message = "此按钮进行文本、语音输入模式切换",
                    onClick = {
                        featureTour.next()
                    }
                )
            )
            .addStep(
                FeatureTour.Step(
                    targetView = viewBinding.inputStateChange,
                    message = "长按此按钮，可进行语音输入",
                    onClick = {
                        featureTour.next()
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