package dev.hearthd.android.portal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.hearthd.android.portal.settings.SettingsRepository
import dev.hearthd.android.portal.ui.KioskScreen
import dev.hearthd.android.portal.ui.SettingsScreen
import dev.hearthd.android.portal.update.UpdateController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiosk: draw edge to edge and hide the status and navigation bars so
        // the native back/home bar never intrudes. Bars stay hidden until an
        // operator swipes from an edge, then auto-hide again.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        val settingsRepo = SettingsRepository(applicationContext)
        val controller = UpdateController(applicationContext)

        // The update loop lives here, scoped to the foreground: it only runs
        // while the app is at least STARTED and the user has opted in. Off
        // screen or disabled, it does nothing and never touches the network.
        // collectLatest restarts the loop whenever settings change.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsRepo.settings.collectLatest { settings ->
                    if (!settings.enabled) return@collectLatest
                    while (true) {
                        controller.check(settings.channel)
                        delay(settings.intervalMinutes.toLong() * 60_000L)
                    }
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The kiosk surface is the root; Settings is reachable from
                    // its swipe-up tray and returns here on close.
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    if (showSettings) {
                        SettingsScreen(
                            settingsRepo = settingsRepo,
                            controller = controller,
                            onClose = { showSettings = false },
                        )
                    } else {
                        KioskScreen(onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The system restores the bars after transient reveals, dialogs, or
        // returning to the foreground. Re-hide them whenever we regain focus.
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
