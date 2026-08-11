package dev.hearthd.android.portal.update

import android.app.admin.DeviceAdminReceiver

/**
 * Present only so the app can be promoted to device owner
 * (`adb shell dpm set-device-owner dev.hearthd.android.portal/.update.PortalDeviceAdminReceiver`),
 * which lets [Updater] install silently instead of prompting. No policies are
 * used; it is inert until provisioned.
 */
class PortalDeviceAdminReceiver : DeviceAdminReceiver()
