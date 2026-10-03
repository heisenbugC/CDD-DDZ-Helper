package com.example.demo.services

interface TextToSpeechService {
    fun speak(text: String)
    fun stop()
}
