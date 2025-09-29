package com.welo.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.InputStream

/**
 * 相册图片选取工具类（仅支持多选）
 */
object PhotoPickerUtils {
    private const val TAG = "PhotoPickerUtils"

    // 权限请求码
    const val REQUEST_PERMISSION_CODE = 1001

    // 相册选取请求码
    const val REQUEST_PICK_MULTIPLE_IMAGES = 1003

    private var mListener: OnPhotosSelectedListener? = null
    private var minSelectionCount: Int = 1 // 最小选择数量
    private var maxSelectionCount: Int = 9 // 最大选择数量

    /**
     * 检查是否有读取存储的权限
     */
    fun checkStoragePermission(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 可以使用新的照片选择器，不需要 READ_EXTERNAL_STORAGE
            true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                activity,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 请求存储权限
     */
    fun requestStoragePermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
            REQUEST_PERMISSION_CODE
        )
    }

    /**
     * 打开相册选取多张图片
     * @param minCount 最小选择数量（默认1）
     * @param maxCount 最大选择数量（默认9）
     */
    @SuppressLint("IntentReset")
    fun openGallery(
        activity: Activity,
        listener: OnPhotosSelectedListener,
        mediaLauncher: ActivityResultLauncher<Intent>? = null,
        minCount: Int = 1,
        maxCount: Int = 9
    ) {
        mListener = listener
        minSelectionCount = minCount
        maxSelectionCount = maxCount

        val permission = checkStoragePermission(activity)
        LogUtil.d(TAG, "openGallery permission:$permission")
        // 检查权限
        if (!permission) {
            requestStoragePermission(activity)
            return
        }
        openGalleryInternal(activity, mediaLauncher)
    }

    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray,
        activity: Activity,
        mediaLauncher: ActivityResultLauncher<Intent>? = null
    ): Boolean {
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，打开相册
                openGalleryInternal(activity, mediaLauncher)
                return true
            } else {
                // 权限被拒绝
                mListener?.onPhotosError("存储权限被拒绝")
            }
        }
        return false
    }

    /**
     * 内部打开相册方法
     */
    private fun openGalleryInternal(
        activity: Activity,
        mediaLauncher: ActivityResultLauncher<Intent>? = null
    ) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用新的照片选择器
                Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxSelectionCount)
                    type = "image/*"
                }
            } else {
                Intent(Intent.ACTION_PICK).apply {
                    type = "image/*"
                    setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")

                    // 设置多选
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
            }

            // 添加选择器标题
            val chooserIntent = Intent.createChooser(intent, "选择图片（可多选）")
            if (mediaLauncher != null) {
                mediaLauncher.launch(chooserIntent)
            } else {
                activity.startActivityForResult(chooserIntent, REQUEST_PICK_MULTIPLE_IMAGES)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "打开相册失败: ${e.message}")
            mListener?.onPhotosError("无法打开相册: ${e.message}")
        }
    }

    /**
     * 处理从相册返回的结果
     */
    fun handleImageResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        activity: Activity
    ): Boolean {
        if (requestCode == REQUEST_PICK_MULTIPLE_IMAGES && resultCode == Activity.RESULT_OK && data != null) {
            return handleMultipleImagesResult(data, activity)
        } else if (resultCode == Activity.RESULT_CANCELED) {
            // 用户取消了选择
            mListener?.onSelectionCancelled()
        }
        return false
    }

    /**
     * 处理多张图片选择结果
     */
    fun handleMultipleImagesResult(data: Intent, activity: Activity): Boolean {
        if (mListener == null) {
            return false
        }

        val selectedUris = mutableListOf<Uri>()
        val selectedBitmaps = mutableListOf<Bitmap>()

        try {
            // 处理多选结果 - 优先检查新的照片选择器API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 新的照片选择器
                data.data?.let { uri ->
                    selectedUris.add(uri)
                    addBitmapFromUri(uri, activity, selectedBitmaps)
                }
                data.clipData?.let { clipData ->
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        selectedUris.add(uri)
                        addBitmapFromUri(uri, activity, selectedBitmaps)
                    }
                }
            } else {
                val clipData: ClipData? = data.clipData
                // 旧版本处理方式
                if (clipData != null) {
                    // 选择了多张图片
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        selectedUris.add(uri)
                        addBitmapFromUri(uri, activity, selectedBitmaps)
                    }
                } else if (data.data != null) {
                    // 只选择了一张图片
                    val uri = data.data
                    if (uri != null) {
                        selectedUris.add(uri)
                        addBitmapFromUri(uri, activity, selectedBitmaps)
                    }
                }
            }

            // 检查选择数量是否符合要求
            if (selectedUris.isNotEmpty()) {
                if (selectedUris.size < minSelectionCount) {
                    mListener?.onSelectionIncomplete(minSelectionCount, selectedUris.size)
                    return true
                } else if (selectedUris.size > maxSelectionCount) {
                    mListener?.onSelectionExceeded(maxSelectionCount, selectedUris.size)
                    return true
                } else {
                    mListener?.onPhotosSelected(selectedBitmaps, selectedUris)
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mListener?.onPhotosError("处理图片失败: ${e.message}")
        }

        return false
    }

    /**
     * 从URI添加Bitmap到列表
     */
    private fun addBitmapFromUri(uri: Uri, activity: Activity, bitmapList: MutableList<Bitmap>) {
        try {
            val inputStream: InputStream? = activity.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                bitmapList.add(bitmap)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 显示图片到ImageView
     */
    fun displayImage(imageView: ImageView?, bitmap: Bitmap?) {
        if (imageView != null && bitmap != null) {
            imageView.setImageBitmap(bitmap)
        }
    }

    /**
     * 清除监听器引用，避免内存泄漏
     */
    fun clearListener() {
        mListener = null
    }

    /**
     * 图片选择回调接口
     */
    interface OnPhotosSelectedListener {
        fun onPhotosSelected(bitmaps: List<Bitmap>, uris: List<Uri>)
        fun onSelectionIncomplete(minRequired: Int, actualSelected: Int) // 选择数量不足
        fun onSelectionExceeded(maxAllowed: Int, actualSelected: Int) // 选择数量超出
        fun onPhotosError(errorMsg: String?)
        fun onSelectionCancelled() // 选择取消回调
    }
}