# Install from Jitpack

Step 1. Add the JitPack repository to your `./project-dir/build.gradle` file

**gradle groovy DSL**
```grovy
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenCentral()
		maven { url 'https://jitpack.io' }
	}
}
```


**gradle kotlin DSL**
```grovy Kotlin
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
			mavenCentral()
			maven { url = uri("https://jitpack.io") }
	}
}
```

Step 2. Add the dependency in your `.project-dir/app/build.gradle` file

```grovy
dependencies {
    implementation("com.github.darwin-morocho:camera2-builder:v0.0.1")
}
```


# Example
* Before rendering the camera preview, you need to ensure that the camera permission has been granted.


* Once the permission is granted, you can initialize the camera preview using the Camera2Builder library as follows:

```kotlin
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

    // Use remember to create the Camera2Builder instance
    val cameraBuilder = remember(cameraId) {
        Camera2Builder(context, cameraId, previewSize)
    }

    // Var to hold the TextureView reference
    val textureViewState = remember { mutableStateOf<TextureView?>(null) }

    // Effect to manage the lifecycle
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
                        // Restart the preview if the size changes
                        if (cameraBuilder.isCameraActive()) {
                            cameraBuilder.restartPreview()
                        }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        cameraBuilder.release() // Release the camera resources
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        update = { textureView ->
            // Clear the TextureView when the camera changes
            textureView.surfaceTexture?.let {
                it.release()
            }
        }
    )
}
```

> None you can use the `setOnFrameListener` method to receive the nv21 byte array frames and the image size.

Check the example in the `./project-dir/app` folder for more details.