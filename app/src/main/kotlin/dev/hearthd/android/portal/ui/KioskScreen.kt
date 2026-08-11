package dev.hearthd.android.portal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.hearthd.android.portal.R

/**
 * The kiosk surface — what the Portal shows in normal operation. Deliberately
 * trivial for now (just a label); real content comes later.
 *
 * Kiosk mode will hide the system bars, so operator controls can't live behind
 * the nav bar. Instead a swipe down from the top edge drops a tray — just a
 * Settings entry today, with room to grow. The gesture comes from the top
 * because the Portal's own system controls own the bottom edge; a bottom grab
 * strip would fight them and lose.
 */
@Composable
fun KioskScreen(onOpenSettings: () -> Unit) {
    var trayVisible by remember { mutableStateOf(false) }
    // How far the drag must travel downward before the tray opens, so a stray
    // touch on the top edge doesn't trigger it.
    val openThresholdPx = with(LocalDensity.current) { 32.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.kiosk_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.Center),
        )

        // Invisible grab strip along the top edge. Nothing is drawn until a
        // drag travels far enough downward, keeping the surface clean.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) {
                    var travel = 0f
                    detectVerticalDragGestures(
                        onDragStart = { travel = 0f },
                        onVerticalDrag = { _, delta -> travel += delta },
                        onDragEnd = { if (travel >= openThresholdPx) trayVisible = true },
                    )
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
