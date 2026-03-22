package ca.deltica.contactra.ui.screens

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ExifOrientationTransformTest {

    @Test
    fun mapsExifOrientationsToExpectedTransform() {
        assertTransform(ExifInterface.ORIENTATION_NORMAL, rotation = 0, mirror = false)
        assertTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL, rotation = 0, mirror = true)
        assertTransform(ExifInterface.ORIENTATION_ROTATE_180, rotation = 180, mirror = false)
        assertTransform(ExifInterface.ORIENTATION_FLIP_VERTICAL, rotation = 180, mirror = true)
        assertTransform(ExifInterface.ORIENTATION_TRANSPOSE, rotation = 90, mirror = true)
        assertTransform(ExifInterface.ORIENTATION_ROTATE_90, rotation = 90, mirror = false)
        assertTransform(ExifInterface.ORIENTATION_TRANSVERSE, rotation = 270, mirror = true)
        assertTransform(ExifInterface.ORIENTATION_ROTATE_270, rotation = 270, mirror = false)
        assertTransform(ExifInterface.ORIENTATION_UNDEFINED, rotation = 0, mirror = false)
    }

    private fun assertTransform(orientation: Int, rotation: Int, mirror: Boolean) {
        val transform = exifOrientationTransform(orientation)
        assertEquals(rotation, transform.rotationDegrees)
        assertEquals(mirror, transform.mirrorHorizontally)
    }
}
