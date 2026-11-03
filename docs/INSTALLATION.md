# Installation and invalid-package errors

Download a release APK from the official Releases page. The additional iSwara-0.9.1-verified.apk asset is byte-identical to the original official 0.9.1 build, with the same features, package version and signing identity. It provides a fresh download URL; it is not a modified or reduced application.

For 0.9.1, the expected size is 173592244 bytes and SHA-256 is:

```text
4a71818141190a0a9ecff460380a1961e4acc78be3522c667714c6b0b7af2841
```

A correct filename and file size are insufficient to prove a complete download. In the 3 November 2026 investigation, a device-side file had the expected size but two regions contained missing data replaced by zeros. Its APK signature and ZIP CRC checks failed. A clean official file was subsequently installed successfully on Android 16 using a same-version reinstall, without uninstalling or clearing app data.

## Check a downloaded file

1. Use a fresh download, rather than reopening or resuming a known failed copy. The verified asset and its checksum are attached to release v0.9.1.
2. Compare SHA-256 with the published value. On Windows, use:

   ```powershell
   Get-FileHash -Algorithm SHA256 .\iSwara-0.9.1-verified.apk
   ```

3. If the hash differs, the file is not the official intact APK. Keep any working installation and replace only the failed download. Sharing the correctly verified APK as a file preserves the intended release; verify again if a recipient still reports a failure.
4. If the hash matches and installation still fails, obtain the actual Android installer error. With an authorized USB-debugging device, the Android SDK command below preserves existing app data and returns a diagnostic code:

   ```powershell
   adb install -r --no-streaming .\iSwara-0.9.1-verified.apk
   ```

An incompatible existing signer, unsupported Android version, storage problem or explicit system-verification refusal requires its own diagnosis. Do not assume a signing-key conflict, downgrade, disable security checks, or uninstall an existing application just from the generic invalid-package message. Back up/sync data before any deliberately chosen migration that requires removal.

## Evidence limits

The current verified result is a same-version reinstall on one Android 16 / arm64 device. It does not establish fresh installation or all devices. The friend's device mentioned in the report was not connected or inspected. No application code, native feature, Admin permission or release-signing key was changed for this repair. See verification/installation-0.9.1.json.
