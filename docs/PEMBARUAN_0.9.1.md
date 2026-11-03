# iSwara Music 0.9.1

The full application retains the native Admin panel for verified active server administrators. Regular users do not receive administration privileges. All Android source corresponding to the APK remains available under GPL; production credentials, signing keys and user data are private.

- Build configuration rejects secret/service-role keys accidentally supplied as the mobile public key. Local/unconfigured builds can leave the public key empty.
- Release tests check that native tools remain enabled while a guest/forged local role cannot expose account Admin navigation. Server RLS, UID membership and authenticated operations enforce actual authority.
- README and current build/user/cloud/privacy/admin/release guides are maintained in English. Earlier Indonesian release notes remain historical.
- Repository protection includes secret scanning, push protection, dependency security updates, private vulnerability reporting and an owner-review file. Local source/archive auditing checks configured values, credential/private-key patterns and server-role JWTs.

Install as an update without uninstalling. VersionCode is 12. Compatible 0.9.0 installations can discover 0.9.1 after GitHub publication and Supabase registration. Android still confirms installation. Existing 0.9.0 APK assets are not overwritten.

Read USER_GUIDE.md, BUILD.md, ../SECURITY.md and VERIFICATION.md for usage, build steps, security boundaries and actual test limits. Neither obfuscation nor visible/hidden client code can prevent compliant GPL copying or replace server authorization.
