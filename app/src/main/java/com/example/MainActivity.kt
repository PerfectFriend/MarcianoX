package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crypto.ScreenCapturePreventionManager
import com.example.crypto.SimplexLogger
import com.example.ui.CyberpunkSplash
import com.example.ui.DashboardScreen
import com.example.ui.LockScreen
import com.example.ui.ProfileSelectionScreen
import com.example.ui.keyboard.InAppKeyboardHost
import com.example.ui.keyboard.InAppKeyboardManager
import com.example.ui.keyboard.forceHideSystemIme
import com.example.ui.keyboard.hardenWindowAgainstInputLeaks
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.SimplexViewModel

class MainActivity : FragmentActivity() {
    private var simplexViewModel: SimplexViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize persistent file logger for audit trails
        SimplexLogger.init(applicationContext)
        SimplexLogger.log("MAIN", "MainActivity created. Thread: ${Thread.currentThread().name}")

        // Enforce window-level OS IME suppression, Autofill blocking, and FLAG_SECURE
        hardenWindowAgainstInputLeaks(window, applyScreenCaptureGuard = true)
        ScreenCapturePreventionManager.applyProtection(this)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: SimplexViewModel = viewModel()
                simplexViewModel = viewModel

                val showSplash by viewModel.showSplash.collectAsState()
                val showProfileSelection by viewModel.showProfileSelection.collectAsState()
                val isLocked by viewModel.isLocked.collectAsState()
                val currentSkin by viewModel.currentSkin.collectAsState()

                val currentDensity = LocalDensity.current
                val customDensity = remember(currentDensity) {
                    Density(
                        density = currentDensity.density * 1.20f,
                        fontScale = currentDensity.fontScale * 1.25f
                    )
                }

                CompositionLocalProvider(
                    LocalDensity provides customDensity
                ) {
                    // Wrap entire Activity tree in DisableSoftKeyboard + InAppKeyboardHost:
                    // Completely blocks external Google Keyboard (Gboard) and third-party IMEs.
                    InAppKeyboardHost(
                        hostId = InAppKeyboardManager.MAIN_HOST_ID,
                        skin = currentSkin,
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (showSplash) {
                                CyberpunkSplash(viewModel = viewModel)
                            } else if (showProfileSelection) {
                                ProfileSelectionScreen(viewModel = viewModel)
                            } else if (isLocked) {
                                LockScreen(viewModel = viewModel)
                            } else {
                                DashboardScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        simplexViewModel?.recordUserActivity()
    }

    override fun onPause() {
        super.onPause()
        forceHideSystemIme(window?.decorView)
        simplexViewModel?.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        hardenWindowAgainstInputLeaks(window, applyScreenCaptureGuard = true)
        forceHideSystemIme(window?.decorView)
        simplexViewModel?.onAppForegrounded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hardenWindowAgainstInputLeaks(window, applyScreenCaptureGuard = true)
            forceHideSystemIme(window?.decorView)
        }
    }
}

