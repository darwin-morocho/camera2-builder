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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fit.codergym.camera2builder.Camera2Builder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Camera2Preview(
    modifier: Modifier = Modifier,
    cameraId: String,
    previewSize: Size,
    onFrame: (ByteArray, Size) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Usamos remember con key para recrear el cameraBuilder cuando cambia la cámara
    val cameraBuilder = remember(cameraId) {
        Camera2Builder(context, cameraId, previewSize)
    }

    // Variable para gestionar el estado de la TextureView
    val textureViewState = remember { mutableStateOf<TextureView?>(null) }

    // Efecto para manejar el ciclo de vida
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
            // Limpiar la TextureView cuando cambia la cámara
            textureView.surfaceTexture?.let {
                it.release()
            }
        }
    )
}