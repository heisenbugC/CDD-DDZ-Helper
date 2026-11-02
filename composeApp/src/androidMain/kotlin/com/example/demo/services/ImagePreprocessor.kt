package com.example.demo.services

import java.nio.ByteBuffer

interface ImagePreprocessor {
    /**
     * Preprocesses input raw pixel buffer (YUV or RGBA) into normalized 640x640 RGB float buffer.
     * Applies histogram black-level stretch and 3x3 unsharp mask sharpening.
     */
    fun preprocessToModelInput(
        pixels: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int
    ): ByteBuffer
}
