package com.tanda.biometrics.verification.mapper

import com.tanda.biometrics.domain.model.Image
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ImageTest {
    @Test
    fun acceptsSdkCaptureShape() {
        val pixels = ByteArray(400 * 500)
        assertContentEquals(
            pixels,
            Image(width = 400, height = 500, data = pixels).mapToByte(),
        )
    }

    @Test
    fun rejectsUnexpectedDimensions() {
        assertFailsWith<IllegalArgumentException> {
            Image(width = 500, height = 400, data = ByteArray(400 * 500)).mapToByte()
        }
    }

    @Test
    fun rejectsUnexpectedBufferLength() {
        assertFailsWith<IllegalArgumentException> {
            Image(width = 400, height = 500, data = ByteArray(1)).mapToByte()
        }
    }
}