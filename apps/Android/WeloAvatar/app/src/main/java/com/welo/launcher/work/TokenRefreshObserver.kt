package com.welo.launcher.work

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager

// 可以添加一个观察WorkManager状态的类
class TokenRefreshObserver(context: Context) {
    private val workManager = WorkManager.Companion.getInstance(context)

    fun observeTokenRefreshStatus(callback: (Result) -> Unit) {
        workManager.getWorkInfosByTagLiveData("token_refresh")
            .observeForever { workInfos ->
                workInfos.forEach { info ->
                    when (info.state) {
                        WorkInfo.State.FAILED -> {
                            val errorCode = info.outputData.getString("ERROR_CODE")
                            callback(Result.Failure(errorCode))
                        }
                        WorkInfo.State.SUCCEEDED -> callback(Result.Success)
                        else -> {} // 其他状态忽略
                    }
                }
            }
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val errorCode: String?) : Result()
    }
}