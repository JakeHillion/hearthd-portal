package dev.hearthd.android.portal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.hearthd.android.portal.settings.SettingsRepository
import dev.hearthd.android.portal.ui.SettingsScreen
import dev.hearthd.android.portal.update.UpdateController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    SettingsScreen(settingsRepo, controller)
                }
            }
        }
    }
}
