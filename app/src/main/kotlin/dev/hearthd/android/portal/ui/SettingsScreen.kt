package dev.hearthd.android.portal.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearthd.android.portal.BuildConfig
import dev.hearthd.android.portal.R
import dev.hearthd.android.portal.settings.Channel
import dev.hearthd.android.portal.settings.INTERVAL_STOPS
import dev.hearthd.android.portal.settings.SettingsRepository
import dev.hearthd.android.portal.settings.UpdateSettings
import dev.hearthd.android.portal.settings.formatInterval
import dev.hearthd.android.portal.update.UpdateController
import dev.hearthd.android.portal.update.UpdateStatus
import dev.hearthd.android.portal.update.UpdateUiState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

/** Root settings screen: a navigation rail with sections, "Updates" for now. */
@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository,
    controller: UpdateController,
    onClose: () -> Unit,
) {
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = UpdateSettings())
    val ui by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            // Back to the kiosk surface. With the system bars hidden in kiosk
            // mode this is the way out of settings.
            NavigationRailItem(
                selected = false,
                onClick = onClose,
                icon = { Text("←") },
                label = { Text(stringResource(R.string.settings_close)) },
            )
            NavigationRailItem(
                selected = true,
                onClick = {},
                icon = { Text("↻") },
                label = { Text(stringResource(R.string.settings_updates)) },
            )
        }
        UpdatesPane(
            settings = settings,
            ui = ui,
            onEnabledChange = { scope.launch { settingsRepo.setEnabled(it) } },
            onChannelChange = { scope.launch { settingsRepo.setChannel(it) } },
            onIntervalChange = { scope.launch { settingsRepo.setIntervalMinutes(it) } },
            onCheckNow = { scope.launch { controller.check(settings.channel) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesPane(
    settings: UpdateSettings,
    ui: UpdateUiState,
    onEnabledChange: (Boolean) -> Unit,
    onChannelChange: (Channel) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onCheckNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        // Opt-in toggle.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.update_auto), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.update_auto_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = settings.enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(Modifier.height(24.dp))

        // Channel.
        Text(stringResource(R.string.update_channel), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Channel.entries.forEach { channel ->
                FilterChip(
                    selected = settings.channel == channel,
                    onClick = { onChannelChange(channel) },
                    label = { Text(channel.label) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Interval slider over the discrete stops.
        Text(
            "${stringResource(R.string.update_interval)} ${formatInterval(settings.intervalMinutes)}",
            style = MaterialTheme.typography.titleMedium,
        )
        val index = INTERVAL_STOPS.indexOf(settings.intervalMinutes).coerceAtLeast(0)
        Slider(
            value = index.toFloat(),
            onValueChange = {
                onIntervalChange(INTERVAL_STOPS[it.roundToInt().coerceIn(0, INTERVAL_STOPS.lastIndex)])
            },
            valueRange = 0f..INTERVAL_STOPS.lastIndex.toFloat(),
            steps = (INTERVAL_STOPS.size - 2).coerceAtLeast(0),
        )
        Spacer(Modifier.height(24.dp))

        // Current build + last check + status.
        Text(
            stringResource(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(statusLine(ui), style = MaterialTheme.typography.bodySmall)
        if (!ui.deviceOwner) {
            Text(
                "Installs need on-screen confirmation. Set device owner via ADB for silent updates.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onCheckNow) {
            Text(stringResource(R.string.update_check_now))
        }
    }
}

@Composable
private fun statusLine(ui: UpdateUiState): String {
    val status = when (ui.status) {
        UpdateStatus.IDLE -> "Idle"
        UpdateStatus.CHECKING -> "Checking…"
        UpdateStatus.DOWNLOADING -> "Downloading…"
        UpdateStatus.INSTALLING -> "Installing…"
        UpdateStatus.UP_TO_DATE -> "Up to date"
        UpdateStatus.FAILED -> "Failed: ${ui.message ?: "unknown error"}"
    }
    val checked = ui.lastCheckedEpochMs?.let {
        " · checked ${DateUtils.getRelativeTimeSpanString(it)}"
    } ?: ""
    return status + checked
}
