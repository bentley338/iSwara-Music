# Publishing application updates

Use a public GitHub Release for APK files and Supabase for small version metadata. Keep signing keys, server keys, and GitHub tokens outside the application and repository.

## Prepare a build

1. Increase versionCode and versionName in app/build.gradle.kts. An installed version only discovers a strictly higher versionCode.
2. Build with the established release signing key. Run the appropriate tests, lint, APK verification, source secret audit, and device checks.
3. Produce the release APK, corresponding source ZIP, and a checksum file covering both assets. Keep debug APKs and R8 mapping archives private.
4. Create a GitHub draft release with a matching tag, attach all assets, and verify uploaded sizes/digests. Publish only the reviewed release.

The APK link must be a real public HTTPS GitHub release asset, for example:

```text
https://github.com/OWNER/REPO/releases/download/v0.9.1/iSwara-0.9.1.apk
```

Repository blob URLs, tag pages, source archives, link shorteners, authenticated/private assets, and mismatched APK metadata are not accepted by the updater. GitHub's documented release-asset limit is [2 GiB per file](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases).

## Register the release

Apply the app_updates migration once. A verified active server administrator can use Space -> Admin panel -> App releases in the full release APK. The project owner's SQL Editor is an alternative. Enter the actual published APK URL, version, SHA-256, byte count, channel and notes, review them, then publish.

Use real values from tools/release-info.py. For an owner SQL session:

```sql
insert into public.app_releases
(version_code,version_name,download_url,sha256,bytes,notes,channel,min_sdk,published)
values
(YOUR_VERSION_CODE,'YOUR_VERSION','YOUR_GITHUB_APK_URL','YOUR_64_CHARACTER_SHA256',YOUR_EXACT_BYTE_COUNT,
 'Your release notes','stable',26,true);
```

Replace every placeholder before running. Save an unpublished row first if the asset is not ready. To withdraw a version, set published=false for that version_code; do not delete an installed user's data or silently replace an APK asset. The native Admin form uses save_app_release, which checks server admin authorization.

## User experience

Settings -> App updates offers manual checks, periodic checks, stable/beta channels, Wi-Fi preferences, download progress, cancellation, and installation. Background checks may be delayed by Android battery/network policy. Automatic checks do not download an APK without a user action.

The updater verifies bytes, SHA-256, package identity/version, minimum Android version, and the original release certificate. WorkManager downloads into private storage and the system installer asks for confirmation. An initial unknown-source permission may also be needed. This is not silent installation.

Versions through 0.8 do not contain the updater and need one manual update without uninstalling. Version 0.9 and later can find future compatible releases. Registering 0.9.1 does not offer it to an installation already on 0.9.1. Stable and beta channels are separate.

## Verification

Check public discovery using check_app_update for an older/current version and confirm draft privacy. Test an actual upgrade with library/account data preserved, denied installer permissions, a corrupt APK, cancellation, and a reconnect. Local transport tests and uploaded-asset checks do not prove the Android installer flow.

GitHub publishing and Supabase registration are separate actions. A successful upload does not mean update metadata has been registered; record and verify both steps.
