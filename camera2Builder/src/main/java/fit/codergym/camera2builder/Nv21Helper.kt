package fit.codergym.camera2builder


import android.media.Image

object Nv21Helper {

    fun convertYUV420ToNV21(image: Image): ByteArray? {
        try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            val width = image.width
            val height = image.height
            val nv21 = ByteArray(width * height * 3 / 2)

            // Copy Y plane
            yBuffer.get(nv21, 0, width * height)

            // Interleave U and V planes
            val uvOffset = width * height
            val uvHalfSize = width * height / 4

            for (i in 0 until uvHalfSize) {
                nv21[uvOffset + i * 2] = vBuffer.get(i * vPlane.pixelStride)
                nv21[uvOffset + i * 2 + 1] = uBuffer.get(i * uPlane.pixelStride)
            }

            return nv21
        } catch (e: Exception) {
            return null
        }
    }
}