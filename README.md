# hearthd-portal

> [!WARNING]
> **This is for tinkerers, not production.** hearthd-portal is an experimental
> app you sideload onto a Facebook Portal. It's very much in flux, and not well
> reviewed for security.
>
> Once this is production ready it'll be merged into the hearthd core repo. For
> now, it serves as a tech demo, and something I use at home.

## Install

Grab the latest `main` build from R2 (see [Release channels](#release-channels))
and sideload it with the Portal connected over USB in ADB mode:

    url=$(curl -fsSL https://assets.hearthd.dev/android/portal/main.json | jq -r .apkUrl)
    curl -fsSL "$url" -o hearthd-portal.apk
    adb install -r hearthd-portal.apk
    adb shell am start -n dev.hearthd.android.portal/.MainActivity

Or build it yourself (debug-signed): `nix build .#hearthd-portal`.

> [!NOTE]
> The published APK is release-signed with a stable key (the `publish` CI job
> runs `ci/sign-apk.sh`) so the app can update in place later. Locally built
> APKs are debug-signed with a throwaway key. Because Android refuses to update
> an app across signing keys, switching a device between a locally built (debug)
> and a published (release) APK requires uninstalling first:
>
>     adb uninstall dev.hearthd.android.portal

### Run as the kiosk (home app)

To make the Portal boot straight into hearthd and stay there, set it as the
device's home (launcher) app. The Portal exposes no home-app picker in its UI,
but it does honour the app's `HOME` intent-filter, so you can point the system
at it over ADB — no device owner needed:

    adb shell cmd package set-home-activity dev.hearthd.android.portal/.MainActivity

After this the Home button lands on hearthd, and the device launches straight
into it on boot (confirmed on a Portal — no lock screen in the way, at least on
a device with no screen lock set). To hand control back to the stock Portal
launcher:

    adb shell cmd package set-home-activity com.facebook.alohaapps.launcher/com.facebook.aloha.app.home.touch.HomeActivity

## Automatic updates

The app can update itself in place from its release channel (opt in under
**Settings → Updates**). On a stock Portal this needs one extra bit of device
setup, because Portal firmware ships a system app verifier
(`com.facebook.appverifier`) that silently rejects any install whose signing
certificate isn't on Meta's internal allowlist. Our release key isn't, so the
system "confirm install" dialog appears but pressing **Install** does nothing —
the verifier vetoes the `PackageInstaller` session after you confirm.

To allow in-app updates, disable package verification on the device over ADB:

    adb shell settings put global package_verifier_enable 0

This persists across reboots. It only affects installs that go through the
package verifier — ADB sideloads (`adb install`) are already exempt
(`verifier_verify_adb_installs` defaults to `0`), which is why the manual
install above works regardless. To restore the verifier:

    adb shell settings put global package_verifier_enable 1

> [!NOTE]
> This is a per-device change and can't be shipped in the app: Portal blocks
> the device-owner path that would otherwise grant silent installs
> (`dpm set-device-owner` is refused once the device has accounts, which every
> provisioned Portal does). Disabling the verifier is currently the only way to
> let a non-Meta-signed build update itself.

## Release channels

Pushes to `main` and `canary` publish the signed APK and a per-stream manifest to
R2, served at `assets.hearthd.dev`:

    https://assets.hearthd.dev/android/portal/main.json
    https://assets.hearthd.dev/android/portal/canary.json

Each manifest points at a content-addressed APK
(`android/portal/<sha256>.apk`) and carries its `versionCode`, `versionName`,
`sha256`, and originating `gitSha`. The `versionCode` is the git commit count
(`revCount`), and the running build is shown on the app's home screen. Canary
only publishes when it contains everything on `main`. The in-app auto-updater
that consumes these lands in a follow-up.
