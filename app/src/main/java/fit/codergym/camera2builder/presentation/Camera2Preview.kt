package fit.codergym.camera2builder.presentation

import android.graphics.SurfaceTexture
import android.os.Build
import android.util.Size
import android.view.TextureView
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fit.codergym.camera2builder.Camera2Builder
@Composable
fun Camera2Preview(
    modifier: Modifier = Modifier,
    cameraId: String,
    previewSize: Size,
    onFrame: (ByteArray, Size) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraBuilder = remember(cameraId) {
        Camera2Builder(context, cameraId, previewSize)
    }

    val textureViewState = remember { mutableStateOf<TextureView?>(null) }

    DisposableEffect(key1 = lifecycleOwner, key2 = cameraId) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> cameraBuilder.release()
                Lifecycle.Event.ON_RESUME -> {
                    textureViewState.value?.let { textureView ->
                        if (textureView.surfaceTexture != null) {
                            cameraBuilder.setTextureView(textureView)
                            cameraBuilder.setOnFrameListener(onFrame)
                            cameraBuilder.start()
                        }
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            cameraBuilder.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    @RequiresApi(Build.VERSION_CODES.P)
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        textureViewState.value = this@apply
                        cameraBuilder.setTextureView(this@apply)
                        cameraBuilder.setOnFrameListener(onFrame)
                        cameraBuilder.start()
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        // Reconfigurar cuando cambia el tamaño
                        if (cameraBuilder.isCameraActive()) {
                            cameraBuilder.restartPreview()
                        }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        cameraBuilder.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        update = { textureView ->
            textureView.surfaceTexture?.release()
        }
    )
}