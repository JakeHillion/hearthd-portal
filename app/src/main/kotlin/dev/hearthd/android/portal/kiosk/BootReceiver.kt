package dev.hearthd.android.portal.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.hearthd.android.portal.MainActivity

/**
 * Brings the kiosk up on its own after a reboot, so an always-on Portal doesn't
 * sit on the launcher waiting for someone to tap the app.
 *
 * Android 9 (Portal's OS) still lets a boot receiver start an activity from the
 * background — the restrictions that would block this only arrived in Android 10.
 * The system only delivers BOOT_COMPLETED to apps that have been launched at
 * least once since install, so the very first run still needs a manual start.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}
