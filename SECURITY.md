# Security policy

## Report a vulnerability privately

Use [GitHub private vulnerability reporting](https://github.com/bentley338/iSwara-Music/security/advisories/new). Include the affected version, a minimal reproduction, and the expected security boundary. Redact tokens, passwords, personal data, raw playback URLs, and signing material. Do not post account or data-access vulnerabilities in a public issue. Response times are not guaranteed.

## Security boundaries

- Android releases retain native Admin tools, with navigation scoped to the current account's server-assigned role. Source and modified clients are untrusted; a visible or copied panel is not authorization.
- Supabase RLS isolates library documents by authenticated user. Administrative privileges are assigned by server-side UID membership and require a verified active account; an email string or client flag does not confer a role.
- Privileged user management runs in an authenticated Edge Function. Server keys remain on the server. The public key embedded in an APK is not a secret and must not bypass RLS.
- Account sessions and offline audio use Android Keystore-backed encryption. Android backups of private application data are disabled; cleartext network traffic is disabled.
- The APK updater checks the release hash, byte count, package identity/version, and signing certificate. Only approved GitHub HTTPS asset locations are accepted. Android still confirms installation.
- Public source distribution excludes private configuration, signing keys, credentials, and local build tooling. GitHub secret scanning, push protection, dependency alerts, and private vulnerability reporting are enabled and checked separately in the current verification record.

## Maintainer practices

Enable two-factor authentication for GitHub and Supabase owner accounts. Use the minimum necessary collaborators; review repository access and security alerts regularly. Keep release signing material outside source control and backed up securely. Review dependency update pull requests and regenerate lockfiles deliberately; automated security PRs are not auto-merged.

Grant administrator membership only through the owner-controlled database/dashboard. Do not accept user-editable metadata, email text, or a client claim as authorization. Keep SMTP, database credentials and service-role/secret keys out of Android, web bundles, and GitHub assets. If a server credential or signing key leaks, rotate/revoke it through the owning service and assess affected releases; hiding or deleting a file does not undo disclosure.

Forks should configure their own backend and signing key. Do not direct a fork at the production backend or impersonate the official application. Read [LICENSE](LICENSE) for copying and redistribution rights. GPL licensing permits compliant forks; neither obfuscation nor a hidden menu can prevent source copying or APK inspection.

## Supported release and limits

Security changes are made against the latest maintained release. Earlier releases and source snapshots remain historical; updating documentation does not change already-installed APKs. Consult [the verification report](docs/VERIFICATION.md) before relying on device behavior. No claim of perfect security, complete penetration testing, or copy prevention is made.
