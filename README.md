# hearthd-portal

> [!WARNING]
> **This is for tinkerers, not production.** hearthd-portal is an experimental
> app you sideload onto a Facebook Portal. It's very much in flux, and not well
> reviewed for security.
>
> Once this is production ready it'll be merged into the hearthd core repo. For
> now, it serves as a tech demo, and something I use at home.

## Install

Download the latest `hearthd-portal-apk` from the
[Actions](https://github.com/JakeHillion/hearthd-portal/actions) tab, or with the
GitHub CLI:

    gh run download -n hearthd-portal-apk

Then, with the Portal connected over USB in ADB mode:

    adb install -r hearthd-portal-*-debug.apk
    adb shell am start -n dev.hearthd.android.portal/.MainActivity

> [!NOTE]
> CI re-signs the APK with a stable release key (`ci/sign-apk.sh`) so the app can
> be updated in place later. Locally built APKs (`nix build .#hearthd-portal`)
> are still debug-signed with a throwaway key. Because Android refuses to update
> an app across signing keys, switching a device between a locally built (debug)
> and a CI (release) APK requires uninstalling first:
>
>     adb uninstall dev.hearthd.android.portal
