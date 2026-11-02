package com.example.demo.accessibility

import android.content.Context
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android TalkBack Live Region Coordinator.
 * Coordinates system accessibility announcements (TalkBack) with in-app TTS
 * to prevent audio clashes and ensure polite live region semantics.
 */
class AccessibilityCoordinator(
    private val context: Context
) {
    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

    private val _status = MutableStateFlow(
        AccessibilityStatus(
            isScreenReaderActive = isTouchExplorationEnabled(),
            isTouchExplorationEnabled = isTouchExplorationEnabled()
        )
    )
    val status: StateFlow<AccessibilityStatus> = _status.asStateFlow()

    private val stateChangeListener = AccessibilityManager.AccessibilityStateChangeListener {
        updateStatus()
    }

    private val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener {
        updateStatus()
    }

    init {
        accessibilityManager?.addAccessibilityStateChangeListener(stateChangeListener)
        accessibilityManager?.addTouchExplorationStateChangeListener(touchExplorationListener)
        updateStatus()
    }

    fun isTouchExplorationEnabled(): Boolean {
        val am = accessibilityManager ?: return false
        return am.isEnabled && am.isTouchExplorationEnabled
    }

    private fun updateStatus() {
        val active = isTouchExplorationEnabled()
        _status.value = AccessibilityStatus(
            isScreenReaderActive = active,
            isTouchExplorationEnabled = active
        )
    }

    /**
     * Dispatches a polite accessibility announcement to TalkBack live regions.
     */
    fun announcePolite(view: View?, text: String) {
        if (text.isBlank()) return
        if (view != null) {
            view.announceForAccessibility(text)
        } else {
            val am = accessibilityManager ?: return
            if (am.isEnabled) {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                    className = javaClass.name
                    packageName = context.packageName
                    this.text.add(text)
                }
                am.sendAccessibilityEvent(event)
            }
        }
    }

    /**
     * Determines whether in-app speech should yield to TalkBack exploration.
     */
    fun shouldYieldToTalkBack(): Boolean {
        return isTouchExplorationEnabled()
    }

    fun release() {
        accessibilityManager?.removeAccessibilityStateChangeListener(stateChangeListener)
        accessibilityManager?.removeTouchExplorationStateChangeListener(touchExplorationListener)
    }
}
