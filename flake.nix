{
  description = "hearthd-portal";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        jdk = pkgs.jdk17;
        version = "0.1.0";

        # Android SDK components needed to build the app. Keep these versions in
        # sync with compileSdk / build-tools in app/build.gradle.kts.
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "35" ];
          buildToolsVersions = [ "35.0.0" ];
          includeEmulator = false;
          includeSystemImages = false;
          includeNDK = false;
        };
        androidSdk = androidComposition.androidsdk;
        sdkRoot = "${androidSdk}/libexec/android-sdk";

        # A Gradle build of this project running a single task. The app package
        # and the lint check are two derivations built from this so that neither
        # depends on the other: a lint failure never blocks the APK, and they
        # build/cache independently. Both replay the same deps.json.
        mkGradle =
          { pname, gradleBuildTask, installPhase }:
          pkgs.stdenv.mkDerivation (finalAttrs: {
            inherit pname version installPhase gradleBuildTask;

            src = ./.;

            nativeBuildInputs = [
              pkgs.gradle
              jdk
            ];

            # Reproducible Gradle dependency resolution. Regenerate deps.json with:
            #   nix run .#update-deps
            mitmCache = pkgs.gradle.fetchDeps {
              pkg = finalAttrs.finalPackage;
              data = ./deps.json;
            };

            # Point the Android Gradle Plugin at the Nix-provided SDK.
            ANDROID_HOME = sdkRoot;
            ANDROID_SDK_ROOT = sdkRoot;

            # AGP needs a writable "android home" for its prefs/analytics; the
            # sandbox HOME is read-only, so redirect it into the build tree. This
            # runs for both the dependency-fetch update and the real build.
            preConfigure = ''
              export ANDROID_USER_HOME="$NIX_BUILD_TOP/.android"
              mkdir -p "$ANDROID_USER_HOME"
            '';

            gradleFlags = [
              "-Dorg.gradle.java.home=${jdk}"
              # AGP downloads a prebuilt aapt2 from Maven that can't run on NixOS
              # (wrong ELF interpreter). Use the autopatched aapt2 from the
              # Nix-provided build-tools instead.
              "-Pandroid.aapt2FromMavenOverride=${sdkRoot}/build-tools/35.0.0/aapt2"
            ];

            # nixpkgs' default dependency-fetch task (nixDownloadDeps) resolves
            # every resolvable configuration, which fails on Android projects with
            # variant-ambiguity errors. Instead, record exactly what the real
            # tasks download by running them under the recording proxy. Both the
            # app and lint tasks are resolved so the shared deps.json serves both.
            gradleUpdateTask = "assembleDebug lintDebug";

            # No unit tests yet.
            doCheck = false;
          });

        hearthd-portal = mkGradle {
          pname = "hearthd-portal";
          gradleBuildTask = "assembleDebug";
          installPhase = ''
            runHook preInstall
            mkdir -p "$out"
            cp app/build/outputs/apk/debug/app-debug.apk \
              "$out/hearthd-portal-${version}-debug.apk"
            runHook postInstall
          '';
        };

        hearthd-portal-lint = mkGradle {
          pname = "hearthd-portal-lint";
          gradleBuildTask = "lintDebug";
          installPhase = ''
            runHook preInstall
            mkdir -p "$out"
            # lintDebug fails the build on lint errors, so reaching install means
            # lint passed; keep the report as the check's output.
            cp app/build/reports/lint-results-debug.* "$out/" 2>/dev/null || true
            runHook postInstall
          '';
        };
      in
      {
        packages = {
          default = hearthd-portal;
          hearthd-portal = hearthd-portal;
        };

        # `nix flake check` builds the APK and runs Android Lint. The build check
        # is the same derivation as the package, so a later `nix build
        # .#hearthd-portal` just fetches the already-built store path.
        checks = {
          build = hearthd-portal;
          lint = hearthd-portal-lint;
        };

        # `nix run .#update-deps` regenerates deps.json after changing dependencies.
        apps.update-deps = {
          type = "app";
          program = "${hearthd-portal.mitmCache.updateScript}";
        };

        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.gradle
            jdk
            androidSdk
            pkgs.android-tools # adb, for sideloading
          ];
          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          JAVA_HOME = "${jdk}";
          shellHook = ''
            echo "hearthd-portal dev shell — gradle $(gradle --version | awk '/Gradle/ {print $2; exit}'), JDK 17, Android SDK ready."
            echo "Build the APK with:  nix build .#hearthd-portal"
          '';
        };
      }
    );
}
