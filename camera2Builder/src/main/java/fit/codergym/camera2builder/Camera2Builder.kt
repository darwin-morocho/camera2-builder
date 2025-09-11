package fit.codergym.camera2builder

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresApi
import java.util.concurrent.atomic.AtomicBoolean

class Camera2Builder(
    private val context: Context,
    private val cameraId: String,
    private val previewSize: Size,
    private var textureView: TextureView? = null
) {

    private val lock = Any()
    private var isProcessing = AtomicBoolean(false)
    private var isReleasing = AtomicBoolean(false)

    private var cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var onFrameListener: ((ByteArray, Size) -> Unit)? = null

    private lateinit var backgroundThread: HandlerThread
    private lateinit var backgroundHandler: Handler

    private var cameraDevice: CameraDevice? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private val isRunning = AtomicBoolean(false)

    private var releaseCallback: (() -> Unit)? = null

    private var startCallback: ((success: Boolean) -> Unit)? = null

    @RequiresApi(Build.VERSION_CODES.P)
    fun setTextureView(textureView: TextureView?) {
        this.textureView = textureView
        if (isRunning.get()) {
            restartPreview()
        }
    }

    fun setOnFrameListener(listener: (ByteArray, Size) -> Unit) {
        this.onFrameListener = listener
    }

    fun start(startCallback: ((Boolean) -> Unit)? = null) {
        if (isRunning.getAndSet(true)) {
            return
        }
        this.startCallback = startCallback
        startBackgroundThread()
        openCamera()
    }


    fun release(onComplete: (() -> Unit)? = null) {
        synchronized(lock) {
            if (isReleasing.getAndSet(true)) {
                onComplete?.invoke()
                return
            }

            if (!isRunning.getAndSet(false)) {
                onComplete?.invoke()
                return
            }

            this.releaseCallback = onComplete
            this.startCallback = null

            try {
                // Cerrar recursos de forma segura
                captureSession?.close()
                imageReader?.setOnImageAvailableListener(null, null)
                imageReader?.close()
                cameraDevice?.close()
            } catch (e: Exception) {
                Log.e("Camera2Builder", "Error releasing camera", e)
            } finally {
                captureSession = null
                imageReader = null
                cameraDevice = null
                textureView = null

                try {
                    backgroundThread.quitSafely()
                    backgroundThread.join(1000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    releaseCallback?.invoke()
                    isReleasing.set(false)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun restartPreview() {
        if (!isRunning.get()) return

        try {
            captureSession?.close()
            imageReader?.close()

            setUpImageReader()
            createCameraPreviewSession()
        } catch (e: Exception) {
            Log.e("Camera2Builder", "Error restarting preview", e)
            release()
        }
    }

    private fun openCamera() {
        try {
            if (!isRunning.get()) return

            cameraManager.openCamera(
                cameraId,
                object : CameraDevice.StateCallback() {
                    @RequiresApi(Build.VERSION_CODES.P)
                    override fun onOpened(camera: CameraDevice) {
                        if (!isRunning.get()) {
                            camera.close()
                            return
                        }
                        cameraDevice = camera
                        try {
                            setUpImageReader()
                            createCameraPreviewSession()
                            startCallback?.invoke(true)
                        } catch (e: Exception) {
                            Log.e("Camera2Builder", "Error setting up camera", e)
                            startCallback?.invoke(false)
                            release()
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                        isRunning.set(false)
                        releaseCallback?.invoke()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                        isRunning.set(false)
                        Log.e("Camera2Builder", "Camera error: $error")
                        releaseCallback?.invoke()
                        startCallback?.invoke(false)
                    }
                },
                backgroundHandler
            )
        } catch (e: SecurityException) {
            Log.e("Camera2Builder", "Security exception opening camera", e)
            isRunning.set(false)
        } catch (e: Exception) {
            Log.e("Camera2Builder", "Exception opening camera", e)
            isRunning.set(false)
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createCameraPreviewSession() {
        try {
            val cameraDevice = cameraDevice ?: return
            val imageReader = imageReader ?: return
            val textureView = textureView

            val imageReaderSurface = imageReader.surface

            val captureRequestBuilder = cameraDevice.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )

            val outputs = mutableListOf<OutputConfiguration>()

            val surfaceTexture = textureView?.surfaceTexture
            if (surfaceTexture != null) {
                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                val previewSurface = Surface(surfaceTexture)
                captureRequestBuilder.addTarget(previewSurface)
                outputs.add(OutputConfiguration(previewSurface))
            }

            captureRequestBuilder.addTarget(imageReaderSurface)

            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )

            outputs.add(OutputConfiguration(imageReaderSurface))

            val sessionConfiguration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                context.mainExecutor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!isRunning.get()) {
                            session.close()
                            return
                        }
                        captureSession = session
                        try {
                            session.setRepeatingRequest(
                                captureRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                        } catch (e: Exception) {
                            Log.e("Camera2Builder", "Error starting preview", e)
                            release()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e("Camera2Builder", "Camera configuration failed")
                        captureSession = null
                        release()
                    }
                }
            )

            cameraDevice.createCaptureSession(sessionConfiguration)
        } catch (e: Exception) {
            Log.e("Camera2Builder", "Error creating preview session", e)
            release()
        }
    }


    private var lastFrameTime = 0L
    private val minFrameIntervalMs = 100L // process max 10 fps

    private fun setUpImageReader() {
        imageReader?.close()

        imageReader = ImageReader.newInstance(
            previewSize.width,
            previewSize.height,
            ImageFormat.YUV_420_888,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                if (!isRunning.get()) return@setOnImageAvailableListener

                val now = System.currentTimeMillis()
                if (now - lastFrameTime < minFrameIntervalMs) {
                    reader.acquireLatestImage()?.close() // Release not processed image
                    return@setOnImageAvailableListener
                }
                lastFrameTime = now


                val image = reader.acquireLatestImage()
                image?.let { processImage(it) }
            }, backgroundHandler)
        }
    }

    private fun processImage(image: Image) {
        if (isReleasing.get() || !isRunning.get() || isProcessing.getAndSet(true)) {
            image.close()
            return
        }

        try {
            synchronized(lock) {
                if (isReleasing.get() || !isRunning.get()) {
                    image.close()
                    return
                }
                if (image.format != ImageFormat.YUV_420_888) {
                    return
                }

                val size = Size(image.width, image.height)
                val nv21Data = Nv21Helper.convertYUV420ToNV21(image)
                nv21Data?.let {
                    onFrameListener?.invoke(it, size)
                }
            }

        } catch (e: Exception) {
            Log.e("Camera2Builder", "Error processing image", e)
        } finally {
            image.close()
            isProcessing.set(false)
        }
    }

    fun isCameraActive(): Boolean {
        return isRunning.get()
    }
}
