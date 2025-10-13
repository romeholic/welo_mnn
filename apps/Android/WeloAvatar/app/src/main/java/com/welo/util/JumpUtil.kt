package com.welo.util

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import com.taobao.meta.avatar.R
import com.welo.launcher.ByteDanceCallActivity
import com.welo.launcher.ChatActivity
import com.welo.launcher.MediaActivity
import com.welo.launcher.entity.AIOptionItem
import java.io.File
import java.io.IOException


object JumpUtil {
    /**
     * 跳转到聊天界面
     * @param context Context 上下文
     * @param sessionId Long? 会话ID，默认为null
     * @param inputText String? 输入文本，默认为null
     * @param uris ArrayList<String>? 传输的URI列表，默认为null
     */
    fun jumpToChatActivity(
        context: Context,
        sessionId: Long? = null,
        inputText: String? = null,
        uris: ArrayList<Uri>? = null,
        aiOptionPosition: Int? = null,
        clickType: String = "EditText"
    ) {
        val options = ActivityOptions.makeCustomAnimation(
            context,
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
        val intent = Intent(context, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(ChatActivity.EXTRA_INPUT_TEXT, inputText)
            putExtra(ChatActivity.EXTRA_INPUT_URIS, uris)
            putExtra(ChatActivity.EXTRA_AI_OPTION_POSITION, aiOptionPosition)
            putExtra(ChatActivity.EXTRA_CLICK_TYPE, clickType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent, options.toBundle())
    }

    fun openAppByPackageName(context: Context, packageName: String) {
        val packageManager: PackageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) { // 检查应用是否存在
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "应用未安装", Toast.LENGTH_SHORT).show()
        }
    }

    const val CAMERA_REQUEST_CODE = 1001
    fun startCamera(activity: Activity, cameraLauncher: ActivityResultLauncher<Intent>) : String{
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val photoFile = try {
            File.createTempFile(
                "JPEG_${System.currentTimeMillis()}_",
                ".jpg",
                storageDir
            )
        } catch (ex: IOException) {
            null
        }

        photoFile?.also {file ->
            val photoURI: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            // 添加权限标志
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 授予临时权限
            val resInfoList = activity.packageManager.queryIntentActivities(
                takePictureIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                activity.grantUriPermission(
                    packageName,
                    photoURI,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            cameraLauncher.launch(takePictureIntent)
        }
        return photoFile?.absolutePath?:""
    }

    fun jumpAudioCall(context: Context){
        val intent = Intent(context, ByteDanceCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun jumpMediaActivity(context: Context,uri: Uri? = null,stringPath: String? = null){
        val intent = Intent(context, MediaActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MediaActivity.EXTRA_MEDIA_URI,uri)
            putExtra(MediaActivity.EXTRA_MEDIA_PATH, stringPath)
        }
        context.startActivity(intent)
    }
}