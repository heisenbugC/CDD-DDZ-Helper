package com.example.demo.services

interface CardDetectionService {
    /**
     * Pass in the image byte array or platform-specific image format wrapper.
     */
    fun processImage(imageData: ByteArray): List<String>
}
