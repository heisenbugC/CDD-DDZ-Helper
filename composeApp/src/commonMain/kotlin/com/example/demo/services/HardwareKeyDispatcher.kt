package com.example.demo.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Hardware key action events (e.g. Volume Up/Down press).
 */
enum class HardwareKeyEvent {
    VOLUME_DOWN_TRIGGER,
    VOLUME_UP_TRIGGER
}

/**
 * Global hardware key dispatcher to deliver intercepted physical volume key presses
 * directly to the Aiming State Machine and TTS workflow.
 */
object HardwareKeyDispatcher {
    private val _keyEvents = MutableSharedFlow<HardwareKeyEvent>(extraBufferCapacity = 8)
    val keyEvents: SharedFlow<HardwareKeyEvent> = _keyEvents.asSharedFlow()

    fun dispatch(event: HardwareKeyEvent) {
        _keyEvents.tryEmit(event)
    }
}
