# Maintainer handoff

The maintenance build is 0.9.1 / versionCode 12. It retains the full native Admin interface, limited by verified active server membership. The owner explicitly chose complete corresponding Android source under GPL while keeping credentials, signing keys and production data private. Do not publish a partial source archive that cannot build the distributed APK.

Maintained documentation and release notes are in English. GitHub secret scanning, push protection, Dependabot security updates and private reporting are enabled. Build configuration rejects server credentials in the public-key field; tools/audit-source.py scans source/archive inputs without printing credentials. Keep mapping archives/debug APKs private when publishing.

Admin operations remain protected by server UID membership and RLS. Production security probes are read-only; local SQL tests cover forged email/metadata, normal-user restrictions, verification, suspension and publishing authorization. Do not use real-user moderation actions as QA.

Release-only host tests are explicitly enabled using HostTestBuilder.UNIT_TEST_TYPE; PublicReleaseUiTest lives in app/src/testRelease/java. Run its full-Admin configuration and appropriate debug regression tests before release packaging. See VERIFICATION.md for final results rather than counting old-version XML as current evidence.

The previous 0.9.0 GitHub release and Supabase registration already exist. Never overwrite its APK or checksum. Publish new signed assets and complete source, then register actual 0.9.1 metadata through the owner SQL Editor or native Admin panel. Keep the release signing identity unchanged.

The last host check had no ADB device and no installed emulator hypervisor driver. Actual Android upgrade, long playback/background/Bluetooth, native codec/stem/export and two-device cloud/Radio sessions still require device QA. Do not change host security settings or claim JVM tests prove physical execution.
