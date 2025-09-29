package com.welo.manager

import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.WeLoApplication
import com.welo.base.net.ApiResponse
import com.welo.base.net.NetworkManager
import com.welo.constant.Constants
import com.welo.storage.TokenManager
import com.welo.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 图片上传管理器
 * 支持多图并发上传，进度回调，结果回调
 */
class ImageUploadManager {
    companion object{
        private const val TAG = "ImageUploadManager"
    }
    private val uploadJobs = mutableListOf<Job>()

    @RequiresApi(Build.VERSION_CODES.P)
    suspend fun uploadImages(
        uris: List<Uri>,
        onProgress: (Uri, Int) -> Unit
    ): List<ImageUploadResult> = coroutineScope {
        val results = mutableListOf<ImageUploadResult>()
        // 并发上传所有图片
        val deferredResults = uris.map { uri ->
            async(Dispatchers.IO) {
                try {
                    uploadSingleImage(uri, onProgress).also {
                        results.add(it)
                    }
                } catch (e: Exception) {
                    ImageUploadResult.Failed(uri, e.message ?: "Unknown error")
                }
            }
        }

        deferredResults.awaitAll()
        results
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun uploadSingleImage(
        uri: Uri,
        onProgress: (Uri, Int) -> Unit
    ): ImageUploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val uploadedUrl = uploadImageToServer(uri,onProgress)
                if (uploadedUrl == null){
                    return@withContext ImageUploadResult.Failed(uri, "Upload failed")
                }
                ImageUploadResult.Success(uri, uploadedUrl)
            } catch (e: Exception) {
                ImageUploadResult.Failed(uri, e.message ?: "Upload failed")
            }
        }
    }

    /**
     * 上传图片到服务器
     * @param imageUri 图片的Uri
     * @return 服务器返回的图片相对地址，如果上传失败返回null
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun uploadImageToServer(imageUri: Uri, onProgress: (Uri, Int) -> Unit): String? {
        return withContext(Dispatchers.IO) {
            try {
                var imageUrl: String?=null
                val context = WeLoApplication.getInstance().applicationContext
                val inputStream = context.contentResolver.openInputStream(imageUri)
                if (inputStream == null) {
                    LogUtil.e(TAG, "无法打开图片输入流")
                    return@withContext null
                }
                // 获取原始文件信息
                val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
                val fileExtension = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mimeType) ?: "jpg"

                // 使用原始格式创建临时文件
                val tempFile = File.createTempFile("upload_image", ".$fileExtension")

                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                val uploadUrl = Constants.getImageUploadPath()
                NetworkManager.instance.uploadImageForProgress(
                    requestId = UUID.randomUUID().toString(),
                    url = uploadUrl,
                    file = tempFile,
                    fileName = "image_${System.currentTimeMillis()}.$fileExtension",
                    mimeType = mimeType,
                    headers =  {
                        append("Authorization", "Bearer ${TokenManager.getToken()}")
                    },
                    onProgress = { bytesSent, totalBytes ->
                        val progress = if (totalBytes > 0) {
                            (bytesSent * 100 / totalBytes).toInt()
                        } else {
                            0
                        }
                        context.mainExecutor.execute {
                            onProgress(imageUri, progress)
                        }
                    }
                ).collect { response ->
                    when (response) {
                        is ApiResponse.Loading -> {}
                        is ApiResponse.Success -> {
                            imageUrl = parseImageUrlFromResponse(response.data)
                        }
                        is ApiResponse.Error -> {
                            LogUtil.d(TAG, "上传图片失败: ${response.message} imageUri:$imageUri")
                            imageUrl = null
                        }
                    }
                }
                // 清理临时文件
                tempFile.delete()
                return@withContext  imageUrl
            } catch (e: Exception) {
                LogUtil.e(TAG, "上传图片异常: ${e.message}")
                return@withContext null
            }
        }
    }

    /**
     * 从服务器响应中解析图片地址
     * 根据你的实际API响应格式调整这个解析逻辑
     */
    private fun parseImageUrlFromResponse(response: String): String? {
        return try {
            //{"flowId":"e3e07c37-49d7-44b7-be70-1ed17ea44851","file_path":"e3e07c37-49d7-44b7-be70-1ed17ea44851/2025-09-02_05-28-43_image_1756790926658.jpg"}
            val jsonObject = JSONObject(response)
            val flowId = jsonObject.getString("flowId")
            val filePath = jsonObject.getString("file_path")
            LogUtil.d(TAG, "parseImageUrlFromResponse flowId:$flowId filePath:$filePath")
            return filePath
        } catch (e: Exception) {
            LogUtil.e(TAG, "解析图片地址失败: ${e.message}")
            null
        }
    }

    fun cancelAll() {
        uploadJobs.forEach { it.cancel() }
        uploadJobs.clear()
    }
}

sealed class ImageUploadResult {
    data class Success(val uri: Uri, val imageUrl: String) : ImageUploadResult()
    data class Failed(val uri: Uri, val error: String) : ImageUploadResult()
}