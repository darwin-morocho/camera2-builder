package fit.codergym.camera2builder

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Helper object to retrieve device information such as available cameras.
 */
object DeviceHelper {

    /**
     * Retrieves a list of available cameras on the device.
     *
     * @param context The application context.
     * @return A list of CameraInfo objects representing the available cameras.
     */
    fun getAvailableCameras(context: Context): List<CameraInfo> {
        val cameraList = mutableListOf<CameraInfo>()

        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraIds = cameraManager.cameraIdList

            for (cameraId in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val sensorOrientation = characteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION
                ) ?: 0
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
                cameraList.add(
                    CameraInfo(
                        cameraId = cameraId,
                        sensorOrientation = sensorOrientation,
                        isFrontFacing = lensFacing == CameraCharacteristics.LENS_FACING_FRONT
                    )
                )
            }
            // Order cameras by id
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return cameraList
    }
}