# Building iSwara Music

Use JDK 17, Android SDK Platform 36, and Build Tools 36.0.0. JVM tests for recent Android versions use a JDK 21 toolchain. Gradle Wrapper and dependency locks are included.

## Configuration

Set ANDROID_HOME or create an ignored local.properties file with your SDK path. Copy secrets.properties.example to secrets.properties and fill in your own Supabase URL and public key if cloud features are needed. Offline/local features do not require a private backend key.

The current client uses the legacy public anon-key configuration. Do not put a service-role key, secret key, database password, SMTP credential, or GitHub token in BuildConfig. New key types need compatible client/header handling before replacing a deployed legacy key; do not simply paste a server key into the existing field.

## Build and test

From the project root on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:testReleaseUnitTest --tests id.iswara.music.PublicReleaseUiTest
```

On Linux/macOS, make gradlew executable and use ./gradlew. Enable optional provider network tests explicitly with ISWARA_LIVE_TEST=1. Use connectedDebugAndroidTest only with a booted emulator or attached authorized device.

Release and debug builds set ADMIN_TOOLS_ENABLED=true to retain the complete native Admin interface. Navigation requires the current account's server-assigned role; cached/forged client claims do not authorize server requests. Modified clients are untrusted, so RLS and authenticated server checks remain mandatory. Release verification checks that the requested native tools are retained.

## Signing a release

Use your own signing key for a fork. Keep the official release key offline and backed up outside the repository. Fill the ignored configuration using the names in secrets.properties.example:

```properties
ISWARA_KEYSTORE=/absolute/path/to/your-release.jks
ISWARA_STORE_PASSWORD=YOUR_LOCAL_PASSWORD
ISWARA_KEY_ALIAS=YOUR_KEY_ALIAS
ISWARA_KEY_PASSWORD=YOUR_LOCAL_PASSWORD
```

Then build:

```powershell
.\gradlew.bat :app:assembleRelease :app:lintDebug :app:compileDebugAndroidTestKotlin
```

The release APK is app/build/outputs/apk/release/app-release.apk. A key created for a fork cannot update an existing official installation. Never upload keystores or release mapping files as public release assets. Keep R8 mapping files privately for crash diagnosis.

## Verification and packaging

Run tools/verify-apk.py with test results for the version being built. It checks version/SDK, signing identity, alignment/native libraries, bundled models, test results, lint, and native Admin retention and release configuration. Package only after docs/VERIFICATION.md records the current result.

```powershell
python tools/verify-apk.py --test-results docs/verification/unit-VERSION
python tools/release-info.py --url https://github.com/OWNER/REPO/releases/download/TAG/FILE.apk
python tools/audit-source.py
python tools/package-release.py
python tools/audit-source.py --archive
```

Review every source archive before upload: exclude private properties, credentials, keystores, caches, build outputs, and local tooling. Include source, build scripts, dependency locks, model/license notices, and the GPL license. Packaging checks are not a replacement for a secret scan.
