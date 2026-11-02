package com.example.demo.accessibility

/**
 * Screen reader and accessibility service status.
 */
data class AccessibilityStatus(
    val isScreenReaderActive: Boolean = false,
    val isTouchExplorationEnabled: Boolean = false
)
