package com.example.crypto

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ScreenCapturePreventionManager enforces OS-level screen capture prevention
 * by managing WindowManager.LayoutParams.FLAG_SECURE on the host Activity window.
 *
 * This prevents:
 * 1. OS Screenshots (hardware button combinations, system gestures)
 * 2. Screen recording software (system recorders, third-party apps, MediaProjection)
 * 3. Recent tasks switcher (Overview thumbnail capture of sensitive PINs or messages)
 */
object ScreenCapturePreventionManager {

    private val _isCaptureBlocked = MutableStateFlow(true)
    val isCaptureBlocked: StateFlow<Boolean> = _isCaptureBlocked.asStateFlow()

    private val _isEnforced = MutableStateFlow(true)
    val isEnforced: StateFlow<Boolean> = _isEnforced.asStateFlow()

    /**
     * Checks if the device is running in an emulator or test container.
     */
    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT ?: ""
        val model = Build.MODEL ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val hardware = Build.HARDWARE ?: ""
        val product = Build.PRODUCT ?: ""
        val brand = Build.BRAND ?: ""
        val device = Build.DEVICE ?: ""

        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("Emulator")
                || model.contains("Android SDK built for x86")
                || manufacturer.contains("Genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || "google_sdk" == product
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || model.contains("sdk_gphone")
                || product.contains("sdk_gphone")
    }

    /**
     * Enforces FLAG_SECURE on the Activity's window.
     */
    fun applyProtection(activity: Activity?, enforceOnEmulator: Boolean = false) {
        if (activity == null) return
        applyProtectionToWindow(activity.window, enforceOnEmulator)
    }

    /**
     * Enforces FLAG_SECURE on any Android Window (Activity or Dialog).
     */
    fun applyProtectionToWindow(window: android.view.Window?, enforceOnEmulator: Boolean = false) {
        if (window == null) return
        try {
            val shouldApply = _isEnforced.value && (!isEmulator() || enforceOnEmulator)
            if (shouldApply) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                _isCaptureBlocked.value = true
                SimplexLogger.log("SCREEN_GUARD", "FLAG_SECURE applied to window: OS screen capture blocked")
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                _isCaptureBlocked.value = false
                SimplexLogger.log("SCREEN_GUARD", "FLAG_SECURE bypassed (emulator preview safety or disabled)")
            }
        } catch (e: Exception) {
            SimplexLogger.log("SCREEN_GUARD", "Failed to apply FLAG_SECURE: ${e.message}")
        }
    }

    /**
     * Explicitly enables or disables FLAG_SECURE on the window.
     */
    fun setScreenCaptureProtection(activity: Activity?, enabled: Boolean) {
        if (activity == null) return
        _isEnforced.value = enabled
        try {
            if (enabled) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                _isCaptureBlocked.value = true
                SimplexLogger.log("SCREEN_GUARD", "FLAG_SECURE manually enabled")
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                _isCaptureBlocked.value = false
                SimplexLogger.log("SCREEN_GUARD", "FLAG_SECURE cleared")
            }
        } catch (e: Exception) {
            SimplexLogger.log("SCREEN_GUARD", "Error toggling FLAG_SECURE: ${e.message}")
        }
    }

    /**
     * Checks if FLAG_SECURE is currently present on the window attributes.
     */
    fun isWindowSecure(activity: Activity?): Boolean {
        if (activity == null) return false
        val flags = activity.window.attributes.flags
        return (flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    }
}

/**
 * Extension to safely resolve the Activity instance from any Context.
 */
fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
