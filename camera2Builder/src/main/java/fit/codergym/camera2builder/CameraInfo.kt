package fit.codergym.camera2builder

data class CameraInfo(
    val cameraId: String,
    val sensorOrientation: Int,
    val isFrontFacing: Boolean
)