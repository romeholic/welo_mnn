package com.taobao.meta.avatar.a2bs

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.min

class A2BSService {

    private var a2bsServiceNative: Long = 0

    @Volatile
    private var isLoaded = false
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()

    private fun destroy() {
        nativeDestroy(a2bsServiceNative)
        a2bsServiceNative = 0
        isLoaded = false
    }

    suspend fun waitForInitComplete(): Boolean {
        if (isLoaded) return true
        initDeferred?.let {
            return it.await()
        }
        return isLoaded
    }

    private fun loadA2bsResources(resourceDir: String?, tempDir: String): Boolean {
        val loadTime = System.currentTimeMillis()
        Log.d(TAG, "LoadA2bsResourcesFromFile begin ")
        val result =  nativeLoadA2bsResources(a2bsServiceNative, resourceDir, tempDir)
        Log.d(TAG, "LoadA2bsResourcesFromFile end, cost: ${System.currentTimeMillis() - loadTime}")
        return result
    }

    suspend fun init(modelDir: String?, context: Context): Boolean = mutex.withLock {
        if (isLoaded) return true
        val result = withContext(Dispatchers.IO) {
            val tempDir = context.cacheDir.absolutePath + "/a2bs_tmp"
            loadA2bsResources(modelDir, tempDir)
        }
        if (result) {
            isLoaded = true
            if (initDeferred != null) {
                initDeferred?.complete(true)
            }
        }
        return result
    }

/*    fun process(index:Int, audioData: ShortArray, sampleRate: Int): AudioToBlendShapeData {
        return nativeProcessBuffer(a2bsServiceNative, index, audioData, sampleRate)
    }*/

/*    fun process(index: Int, audioData: ShortArray, sampleRate: Int): AudioToBlendShapeData {
        // 打印输入参数
        Log.d(TAG, "===== process() 输入参数 =====")
        Log.d(TAG, "| index: $index")
        Log.d(TAG, "| sampleRate: $sampleRate")
        Log.d(TAG, "| audioData (size: ${audioData.size}) {")

        // 打印audioData前20个元素（可根据需要调整）
        val previewLength = min(20, audioData.size)
        val dataPreview = audioData.take(previewLength).joinToString(", ")
        Log.d(TAG, "|   [${dataPreview}${if (audioData.size > previewLength) ", ...]" else "]"}")
        Log.d(TAG, "| }")

        // 调用原生方法获取结果
        val result = nativeProcessBuffer(a2bsServiceNative, index, audioData, sampleRate)

        // 打印返回值的详细信息
        Log.d(TAG, "===== AudioToBlendShapeData 结果 =====")
        Log.d(TAG, "| frame_num: ${result.frame_num}")

        // 打印各字段
        printFloatArrayList(TAG, "expr", result.expr)
        printFloatArrayList(TAG, "pose", result.pose)
        printFloatArrayList(TAG, "pose_z", result.pose_z)
        printFloatArrayList(TAG, "app_pose_z", result.app_pose_z)
        printFloatArrayList(TAG, "joints_transform", result.joints_transform)
        printFloatArrayList(TAG, "jaw_pose", result.jaw_pose)

        Log.d(TAG, "===== process() 处理完成 =====")
        return result
    }*/

    fun process(index: Int, audioData: ShortArray, sampleRate: Int): AudioToBlendShapeData {
        // 打印输入参数
        Log.d(TAG, "===== process() 输入参数 =====")
        // 调用原生方法获取结果
        val result = nativeProcessBuffer(a2bsServiceNative, index, audioData, sampleRate)
        Log.d(TAG, "===== process() 处理完成 =====")
        return result
    }

    /**
     * 打印Float数组列表的工具方法
     */
    private fun printFloatArrayList(tag: String, fieldName: String, floatArrayList: List<FloatArray>) {
        Log.d(tag, "| $fieldName (size: ${floatArrayList.size}) {")

        // 遍历前5个元素（避免日志过长）
        floatArrayList.take(5).forEachIndexed { index, floatArray ->
            val arrayPreview = floatArray.take(10).joinToString(", ")
            Log.d(
                tag,
                "|   element[$index] (length: ${floatArray.size}): " +
                        "[${arrayPreview}${if (floatArray.size > 10) ", ..." else ""}]"
            )
        }

        if (floatArrayList.size > 5) {
            Log.d(tag, "|   ... (${floatArrayList.size - 5} more elements)")
        }
        Log.d(tag, "| }")
    }

    private external fun nativeCreateA2BS(): Long
    private external fun nativeDestroy(nativePtr: Long)
    private external fun nativeLoadA2bsResources(
        nativePtr: Long,
        resourceDir: String?,
        tempDir: String
    ): Boolean

    private external fun nativeProcessBuffer(
        nativePtr: Long,
        index: Int,
        audioData: ShortArray,
        sampleRate: Int
    ): AudioToBlendShapeData

    init {
        a2bsServiceNative = nativeCreateA2BS()
    }

    companion object {
        private const val TAG = "WELO#A2BSService"

        init {
            System.loadLibrary("mnn_a2bs")
        }
    }
}
