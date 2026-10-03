package com.example.demo.services

interface InferenceService {
    /**
     * Receives pre-processed tensor from openCV,
     * Returns identified suit/rank strings.
     */
    fun runInference(tensorData: FloatArray): String
}
