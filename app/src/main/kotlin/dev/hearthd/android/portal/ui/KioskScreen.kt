package dev.hearthd.android.portal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hearthd.android.portal.R
import dev.hearthd.android.portal.wakeword.WakeWordDetection
import kotlinx.coroutines.flow.SharedFlow

/**
 * The kiosk surface — what the Portal shows in normal operation. Deliberately
 * trivial for now (just a label); real content comes later.
 *
 * Kiosk mode hides the system bars, so operator controls can't live behind the
 * nav bar. A long-press in the top-right corner drops a tray — just a Settings
 * entry today, with room to grow. It's a hold rather than a swipe because the
 * hidden system bars are themselves revealed by an edge swipe; a deliberate
 * corner press won't be mistaken for that, or triggered by a stray touch.
 */
@Composable
fun KioskScreen(
    detections: SharedFlow<WakeWordDetection>,
    onOpenSettings: () -> Unit,
) {
    var trayVisible by remember { mutableStateOf(false) }

    // The most recent detection to surface, and whether its popup is showing.
    // A fresh detection resets the auto-hide timer.
    var detection by remember { mutableStateOf<WakeWordDetection?>(null) }
    var popupVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        detections.collect {
            detection = it
            popupVisible = true
        }
    }
    LaunchedEffect(detection) {
        if (detection != null) {
            kotlinx.coroutines.delay(POPUP_MILLIS)
            popupVisible = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.kiosk_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Center),
        )

        // Invisible hold target in the top-right corner. Nothing is drawn,
        // keeping the surface clean; a long-press opens the tray.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(72.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { trayVisible = true })
                },
        )

        WakeWordPopup(visible = popupVisible, detection = detection)

        TopTray(
            visible = trayVisible,
            onDismiss = { trayVisible = false },
            onOpenSettings = {
                trayVisible = false
                onOpenSettings()
            },
        )
    }
}

private const val POPUP_MILLIS = 4000L

/**
 * A popup "blob" that rises from the bottom edge when a wake word is heard and
 * fades away after a few seconds. Detection feedback only — no recording yet.
 */
@Composable
private fun BoxScope.WakeWordPopup(visible: Boolean, detection: WakeWordDetection?) {
    // Keep the last detection so the label survives the exit animation, when
    // `detection` may already have been cleared.
    var shown by remember { mutableStateOf<WakeWordDetection?>(null) }
    if (detection != null) shown = detection

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 56.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PulseDot()
                Column {
                    Text(
                        text = stringResource(R.string.wake_detected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = shown?.model?.label ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/** A softly pulsing dot, a small "I'm listening" flourish beside the label. */
@Composable
private fun PulseDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
    )
    Box(
        modifier = Modifier
            .size(14.dp)
            .scale(scale)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

/**
 * A tray that drops in from the top edge, over a tap-to-dismiss scrim. Material3
 * only ships a bottom sheet, so this is a minimal top-anchored equivalent.
 */
@Composable
private fun BoxScope.TopTray(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Scrim: fades in beneath the tray and dismisses on tap.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }

    // The tray itself, sliding down from above the top edge.
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        Surface(
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.kiosk_open_settings))
                }
            }
        }
    }
}
