package com.welo.launcher.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.welo.base.net.ApiResult
import com.welo.repository.LoginRepository
import com.welo.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TokenRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val loginRepository = LoginRepository()

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {

                val result = loginRepository.refreshToken().first()
                LogUtil.d("TokenRefreshWorker", "Starting token refresh result:$result")

                when (result) {
                    is ApiResult.Success -> {
                        LogUtil.d("TokenRefreshWorker", "Token refreshed successfully")
                        Result.success()
                    }

                    is ApiResult.Error -> {
                        LogUtil.e("TokenRefreshWorker", "Token refresh failed: ${result.message}")

                        // 可以根据错误码决定是否重试
                        if (shouldRetry(result.code)) {
                            Result.retry()
                        } else {
                            // 需要重新登录
                            Result.failure(workDataOf("ERROR_CODE" to result.code))
                        }
                    }

                    else -> Result.retry()
                }
            } catch (e: Exception) {
                LogUtil.e("TokenRefreshWorker", "Token refresh exception: ${e.message}")
                Result.retry()
            }
        }
    }

    private fun shouldRetry(errorCode: String?): Boolean {
        // 定义哪些错误码应该重试
        return errorCode !in listOf("TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN")
    }

    companion object {
        fun createRequest(delayMillis: Long) = OneTimeWorkRequestBuilder<TokenRefreshWorker>()
            .setInitialDelay(delayMillis, TimeUnit.SECONDS)
            .addTag("token_refresh")
            .build()
    }
}