# Verification: iSwara 0.9.1

Date: 3 November 2026. VersionCode 12, minimum Android 8/API 26, target/compile API 36. Full native Admin functionality is retained at the owner's explicit request. Complete corresponding Android source remains GPL; production credentials, private signing material and user data are excluded from public distribution.

## Build and package

Release/debug assembly, lint, release-specific unit tests and instrumentation compilation completed successfully in 11 minutes 29 seconds. The universal release is 173592244 bytes; SHA-256 4a71818141190a0a9ecff460380a1961e4acc78be3522c667714c6b0b7af2841. Signature v2 is valid and matches the previous release certificate: 6903592161d5a611c0748f4d9b38802b879053927e8c4b84ca7bd64f5c901dbe. Native Admin remains present in the optimized APK. Instrumentation was compiled, not executed on Android.

Lint: 0 fatal/error and 128 warnings. Warnings remain recorded rather than treated as resolved. The final verifier checks version/SDK, non-debuggable release configuration, original signing identity, native Admin retention, updater worker names, ZIP/native 16 KB alignment, four ABIs, model hashes and dictionary resources/notices.

## Tests and counting

12 targeted tests pass with 0 failures/errors/skips in verification/unit-0.9.1. This is a merged final result per class, not a claim that the entire previous 126-test suite was rerun for this version. Current coverage includes native Admin content callbacks, admin tool utilities, phone/tablet navigation, live mix preview, Space pin/search rules, updater/import/queue entry points, and the release-specific Admin role/navigation test.

The first targeted debug run skipped an opt-in live navigation case. It was rerun with ISWARA_LIVE_TEST=1 and its class XML replaced. A release-only test initially validated the earlier proposed admin-disabled configuration; that proposal was superseded by the owner's full-APK choice. The final release XML instead checks ADMIN_TOOLS_ENABLED=true, native Admin availability for authorized use, guest navigation and a forged local role with no matching authenticated account. It replaces the earlier result; tests are not counted twice.

AGP 9.2 exposes host-test configuration through HostTestBuilder. The release unit-test task was enabled explicitly; obsolete/missing task/property attempts are not passing evidence. Build-key validation rejects a synthetic server/secret key with a generic error that does not print the supplied value. Source pattern probes detect synthetic GitHub tokens, Supabase secret keys and private-key headers without treating public placeholders as secrets.

## Backend and repository security

Read-only production requests with the public anonymous client role return 401 for library documents, admin membership, audits, release table access, admin summary, server-only user search and admin-users Edge Function. No production user, membership, content or release was modified by these probes. See backend-security-0.9.1.json.

Local PGlite admin/update tests pass. They cover idempotent migrations, verified UID roles, rejection of forged email/client metadata and self-promotion, draft/RLS isolation, protected audit, server-only user search, suspension/sync bypass, and active-admin-only release publishing with URL/checksum/version/channel/SDK validation. These are local authorization tests, not authenticated destructive production tests or a penetration test.

GitHub API verification confirms secret scanning, push protection, Dependabot security updates and private vulnerability reporting are enabled. CODEOWNERS identifies the repository owner; it is not described as enforced branch approval. No new collaborator, server credential or public write permission was granted. Operator MFA was recommended but not configured/inspected by this session. See github-security-0.9.1.json.

## Source, docs and privacy

Maintained README, user/build/cloud/admin/update/privacy/room/emulator guides and current release notes are English. Historical Indonesian release/test records remain identified as history. Thirty-two local documentation links resolved before final packaging. Application language/settings and the existing Indonesian in-app legal notice are unchanged.

Source audits check actual configured values without printing them, common credential/private-key patterns and server-role JWTs; private properties/keys/build caches/local tools are excluded. The final ZIP is checked for CRC, unique entries and equality to audited workspace bytes. Complete corresponding source and dependency notices accompany the APK; copying allowed by GPL is not treated as credential theft or prevented by UI hiding.

## Device and publication limits

The latest host probe found 0 ADB devices and emulator acceleration status 6. The APK was not executed on a physical device/emulator, and no real installer upgrade was performed. Native encoder/stem processing, prolonged playback/background/Bluetooth/audio focus, overlay/launcher behavior and two-device cloud/Radio/Listen Together flows still require Android QA. Existing earlier component/network results are not presented as a new physical test.

The existing 0.9.0 release is preserved with its original assets; its GitHub release notes were translated to English. Publication and Supabase registration of 0.9.1 are separate steps recorded in the distribution publication proof after packaging. The Android installer still requests confirmation. No claim of complete production acceptance, perfect security or copy prevention is made.

## Post-release installation investigation

An Android 16 / SDK 36 / arm64 device became available after publication. The device already had the non-debuggable 0.9.1 release installed through its in-app updater, according to the owner. Its separate browser-downloaded iSwara-0.9.1.apk had the expected 173592244-byte size but SHA-256 d0deae308344caa3f4580ad37bb4aec954dfc788c76ea1a5f3dd2846081a0f72, instead of the official 4a71818141190a0a9ecff460380a1961e4acc78be3522c667714c6b0b7af2841.

The copied device file failed APK v2 integrity verification and ZIP CRC at lib/x86/libonnxruntime.so. Byte comparison identified two zero-filled damaged regions. The exact size therefore did not establish file integrity. The creating software/network cause was not identified; the report does not blame a particular browser or prove that another user's file had the same corruption.

A clean official APK was copied to Download/iSwara-0.9.1-verified.apk and its on-device SHA-256 matched the official release. ADB install -r --no-streaming returned Success. No uninstall or clear-data command was performed. This is a verified same-version reinstall, not a fresh installation, cross-version upgrade test, playback test or validation on every Android device. The earlier pre-publication device-limit section remains historical; this new evidence supersedes installation coverage only.

The verified-named GitHub asset is byte-identical to the original 0.9.1 APK, and its uploaded digest/size and anonymous accessibility were checked. Existing APK, source and checksum assets were not replaced. Application code and signing identity are unchanged. Installation instructions are in INSTALLATION.md, with machine-readable evidence in verification/installation-0.9.1.json.
