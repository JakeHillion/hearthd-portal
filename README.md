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
