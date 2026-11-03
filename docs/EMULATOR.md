# Android device and emulator testing

Use Android Studio's SDK Manager to install Android SDK Platform 36, Build Tools 36.0.0, platform-tools, Android Emulator, and a compatible system image. Create an AVD in Device Manager and confirm it boots completely before running instrumentation tests.

```powershell
adb devices -l
emulator -accel-check
.\gradlew.bat :app:connectedDebugAndroidTest
```

An offline/unauthorized ADB entry is not a usable test device. For a physical device, unlock it, enable developer USB debugging, connect a data-capable cable, and accept the device's debugging authorization prompt yourself. Do not post device identifiers or authentication material in public issues.

The current build host last reported no connected ADB device and emulator acceleration status 6: the hypervisor driver is not installed. It has not provided evidence of running the APK on Android. Install/configure the appropriate official hypervisor support through normal owner-controlled Windows/Android Studio setup if needed. Do not disable security protections to claim a successful test.

Robolectric tests run Android components on the JVM, including native rendering in selected tests. Compiling instrumentation tests is different from executing them. Neither proves native codecs, Bluetooth focus, system overlays, prolonged background playback, real installer permissions, or two-device Realtime behavior.
