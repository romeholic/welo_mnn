package com.welo.launcher.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.WeLoApplication
import com.taobao.meta.avatar.databinding.FragmentCameraBinding
import com.welo.base.BaseFragment
import com.welo.launcher.HomeActivity

import com.welo.launcher.viewmodel.HomeViewModel
import com.welo.util.JumpUtil
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class CameraFragment : BaseFragment<FragmentCameraBinding, HomeViewModel>() {
    private var param1: String? = null
    private var param2: String? = null
    private var context: Context = WeLoApplication.getInstance()

    // 相机相关变量
    private lateinit var textureView: TextureView
    private lateinit var wordButton: TextView
    private lateinit var soundButton: TextView
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var imageReader: ImageReader

    // 线程处理
    private lateinit var backgroundHandler: Handler
    private lateinit var backgroundThread: HandlerThread

    // 相机状态控制
    private val cameraStateLock = Semaphore(1)
    private var isCapturing = false

    // 相机ID和特性
    private var cameraId: String = ""
    private lateinit var cameraCharacteristics: CameraCharacteristics

    // 预览尺寸
    private lateinit var previewSize: Size
    private lateinit var captureSize: Size

    // 方向处理
    private val ORIENTATIONS = SparseIntArray()

    init {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)

    override fun initView() {
        initViews()
        checkCameraPermission()
    }

    override fun observeViewModel() {

    }

    private fun initViews() {
        textureView = binding.textureView
        soundButton = binding.qaSound
        wordButton = binding.qaWord

        wordButton.setOnClickListener {
            takePicture()
        }
        soundButton.setOnClickListener {
            takePicture()
        }
    }

    private fun checkCameraPermission() {

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            startCamera()
        }
    }
    // 处理权限请求结果
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "requestCode= :$requestCode")
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
    }

    private fun startCamera() {
        try {
            startBackgroundThread()

            if (textureView.isAvailable) {
                openCamera()
            } else {
                textureView.surfaceTextureListener = surfaceTextureListener
            }
        }catch (e: Exception) {
            Log.d(TAG, "startCamera: $e")
        }

    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            // 获取相机ID列表
            val cameraIds = manager.cameraIdList
            cameraId = cameraIds.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraIds[0]

            cameraCharacteristics = manager.getCameraCharacteristics(cameraId)

            // 获取支持的尺寸
            val map =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map != null) {
                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java))
            }
            if (map != null) {
                captureSize = chooseLargestSize(map.getOutputSizes(ImageFormat.JPEG))
            }

            // 设置TextureView尺寸
            textureView.surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)

            // 创建ImageReader用于拍照
            imageReader =
                ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 2)
            imageReader.setOnImageAvailableListener(imageListener, backgroundHandler)

            // 打开相机
            if (cameraStateLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                manager.openCamera(cameraId, cameraStateCallback, backgroundHandler)
            } else {
                throw RuntimeException("等待相机超时")
            }
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开相机", Toast.LENGTH_SHORT).show()
//            requireActivity().finish()
            (activity as HomeActivity).viewPagerToHome()
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreviewSession()
            cameraStateLock.release()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
            cameraStateLock.release()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraDevice?.close()
            cameraDevice = null
            cameraStateLock.release()
            Toast.makeText(context, "相机错误: $error", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
    }

    private fun createCameraPreviewSession() {
        val texture = textureView.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val surface = Surface(texture)

        try {
            captureRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)
            cameraDevice?.createCaptureSession(
                listOf(surface, imageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (cameraDevice == null) return

                        cameraCaptureSession = session
                        try {
                            captureRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            session.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            Toast.makeText(context, "创建预览失败", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(context, "配置失败", Toast.LENGTH_SHORT).show()
                    }
                },
                backgroundHandler
            )
        } catch (e: CameraAccessException) {
            Toast.makeText(context, "相机访问异常", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takePicture() {
            Log.d(TAG, "isCapturing= :$isCapturing,")
        if (isCapturing) return

        isCapturing = true


        try {
            // 锁定自动对焦
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START
            )
            cameraCaptureSession?.capture(
                captureRequestBuilder.build(),
                captureCallback,
                backgroundHandler
            )
        } catch (e: Exception) {
            isCapturing = false
            Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            Log.d(TAG,"result.cameraId=${result.cameraId}")
            captureStillPicture()
            Toast.makeText(context, "拍照成功", Toast.LENGTH_SHORT).show()
            isCapturing =  false
//            viewModel.updateCaptrueState(true)
        }
    }

    private fun captureStillPicture() {
        try {
            val captureBuilder =
                cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)?.apply {
                    addTarget(imageReader.surface)
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    set(CaptureRequest.JPEG_ORIENTATION, getJpegOrientation())
                }

            cameraCaptureSession?.stopRepeating()
            captureBuilder?.build()?.let {
                cameraCaptureSession?.capture(it, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        // 拍照完成后恢复预览
                        createCameraPreviewSession()
                        isCapturing = false
                    }
                }, null)
            }
        } catch (e: Exception) {
            isCapturing = false
            Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
        }
    }

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        Log.d(TAG,"imageListener")
        saveImageToGallery(image)
        image.close()
    }

    private fun saveImageToGallery(image: Image) {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Welo_${timeStamp}.jpg"
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val imageFile = File(storageDir, fileName)

            FileOutputStream(imageFile).use { outputStream ->
                outputStream.write(bytes)
                outputStream.flush()
            }
            Log.d(TAG, "absolute= :${imageFile.absolutePath}")
            // 通知系统更新相册
            MediaScannerConnection.scanFile(
                context,
                arrayOf(imageFile.absolutePath),
                null,
                null
            )

            requireActivity().runOnUiThread {
                Toast.makeText(context, "照片已保存", Toast.LENGTH_SHORT).show()
                // 跳转到对话页面
                JumpUtil.jumpToChatActivity(context = context,uris = arrayListOf(getUriFromAbsolutePath(context,imageFile.absolutePath))  )
            }
        } catch (e: Exception) {
            requireActivity().runOnUiThread {
                Toast.makeText(context, "保存照片失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun getUriFromAbsolutePath(context: Context, absolutePath: String): Uri {
        val file = File(absolutePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider", // 与manifest中配置一致
            file
        )
    }
    private fun getJpegOrientation(): Int {
        val rotation = requireActivity().windowManager.defaultDisplay.rotation
        val sensorOrientation =
            cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        return (sensorOrientation + ORIENTATIONS.get(rotation)) % 360
    }

    private fun chooseOptimalSize(choices: Array<Size>): Size {
        val displaySize = Point()
        requireActivity().windowManager.defaultDisplay.getSize(displaySize)

        return choices.minByOrNull {
            Math.abs(it.width - displaySize.x) + Math.abs(it.height - displaySize.y)
        } ?: choices[0]
    }

    private fun chooseLargestSize(choices: Array<Size>): Size {
        return choices.maxByOrNull { it.width * it.height } ?: choices[0]
    }

    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = requireActivity().windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        }

        textureView.setTransform(matrix)
    }


    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread.quitSafely()
        try {
            backgroundThread.join()
        } catch (e: InterruptedException) {
            // 忽略中断异常
        }
    }

    private fun closeCamera() {
        runCatching {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
        }.onFailure {

        }
    }

    override fun onResume() {
        super.onResume()
        startCamera()
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CameraFragment.
         */
        // TODO: Rename and change types and number of parameters
        private const val REQUEST_CAMERA_PERMISSION = 100
        private const val TAG = "CameraFragment"
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            CameraFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}