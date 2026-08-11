package dev.hearthd.android.portal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hearthd.android.portal.R

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
fun KioskScreen(onOpenSettings: () -> Unit) {
    var trayVisible by remember { mutableStateOf(false) }

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
