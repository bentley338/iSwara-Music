# Cloud setup

Cloud accounts and sync use a Supabase project controlled by the operator. Configure your own project for a fork. Never ship server keys or SMTP/database credentials in Android.

## Database and functions

Install the official Supabase CLI, authenticate as the project owner, and link your project:

```powershell
supabase login
supabase link --project-ref YOUR_PROJECT_REFERENCE
supabase db push
supabase functions deploy delete-account
supabase functions deploy admin-users
```

Alternatively, run the SQL migrations in order through the owner dashboard's SQL Editor. A successful migration does not need to be repeated. The migrations cover library documents and idempotent receipts, server admin roles/content, private listening rooms, Radio lounge, and application release metadata.

Deploy the Edge Function source from supabase/functions. The functions validate the caller through Supabase Auth; admin-users also checks the server-assigned admin role. The service-role key is a server-side environment secret. Do not paste it into Android, documentation, public SQL, or release assets. Gateway JWT settings must be compatible with your project's signing configuration; keep the functions' own user verification intact.

## Authentication and email

Enable email/password sign-up and email confirmation. Set a suitable password policy and Auth rate limits. Add exactly iswara://auth to the allowed redirect list. Keep PKCE/S256 confirmation/recovery links on the installation that initiated the flow; unsolicited implicit token callbacks are not accepted by the client.

Configure production SMTP with a verified sender/domain and the necessary DNS records before inviting public users. Use Supabase's dashboard for SMTP credentials. The default email sender is not a public production mail service. See [Supabase SMTP documentation](https://supabase.com/docs/guides/auth/auth-smtp).

Enable MFA on the operator's GitHub and Supabase dashboard accounts. The application's existing email/password flow does not implement an administrator MFA challenge, so dashboard MFA must not be described as an Android login feature.

## Android configuration

Copy secrets.properties.example to the ignored secrets.properties file:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_ANON_KEY=YOUR_PUBLIC_ANON_KEY
```

This client currently expects a legacy anon key in its existing request headers. A public mobile key is extractable from the APK; RLS and server authorization are the data boundary. Never substitute a secret/service-role key. See the [build guide](BUILD.md) before changing key formats or signing configuration.

Rebuild the signed release after changing backend configuration. Unconfigured builds keep local library functions and show the cloud-unavailable state.

## Data and authorization

Room stores library changes locally first. Sync sends playlist/favorite/history metadata through sync_document using stable IDs, server revisions, tombstones, and idempotent operation IDs. Concurrent playlist edits preserve a conflict copy. Files and encrypted offline audio remain on the device.

Library access is scoped to auth.uid(). Suspended accounts are blocked even with a previously issued access token. Local account scopes are separate from guest storage. Server role assignment is explained in [Administration](ADMIN_SUPABASE.md).

## Verify before public operation

Run local SQL tests from tools after npm ci: test-database/admin/radio/update scripts listed in tools/package.json and the standalone test-admin.mjs, test-radio.mjs, and test-updates.mjs. Supabase CLI with a local database can also run supabase test db.

Use disposable accounts on two Android devices to test confirmation, password recovery, offline edits, conflict copies, sync retry, account switching, and account deletion. Keep a backup before destructive tests. Do not delete or suspend real users to test an implementation. Local SQL/Robolectric results do not prove SMTP delivery or two-device production behavior.
