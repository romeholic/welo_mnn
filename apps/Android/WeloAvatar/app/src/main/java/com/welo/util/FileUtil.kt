package com.welo.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object FileUtil {
    // 生成照片保存路径（应用私有目录，无需存储权限）
    @Throws(IOException::class)
    fun createPhotoFile(context: Context): File? {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val photoFile = try {
            File.createTempFile(
                "JPEG_${System.currentTimeMillis()}_",
                ".jpg",
                storageDir
            )
        } catch (ex: IOException) {
            null
        }
        return photoFile
    }

    // 获取照片的Content Uri（适配7.0+）
    fun getPhotoUri(context: Context,photoFile: File): Uri {

        val photoURI: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        return photoURI
    }
}