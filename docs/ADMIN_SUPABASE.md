# Server administration

Release APKs retain the complete native Admin panel. Only a valid, verified, active account assigned a role by the server is authorized. No hidden tap sequence, email override, or modified client grants server authority. The Supabase owner dashboard remains another management option.

## Assign a role

Apply the admin migration after the library migration. In the owner dashboard, identify the intended user's UUID and confirm email verification. Grant membership only from an owner/service-controlled SQL session:

```sql
insert into public.admin_members(user_id)
select id from auth.users
where id = 'REPLACE_WITH_VERIFIED_USER_UUID'::uuid
  and email_confirmed_at is not null
  and (banned_until is null or banned_until <= now())
on conflict do nothing;
```

Replace the placeholder before running. A user cannot insert their own membership through the public API. is_admin checks UID membership, verified email, and suspension status. Changing the email in an Android build does not create membership.

To revoke access, delete only the intended membership through the owner dashboard. Recheck subsequent requests; hiding a button is not a revocation mechanism. Protect owner accounts with MFA and restrict collaborators.

## Content and users

Published announcements and featured collections are readable by public clients. Draft content is restricted by RLS. Admin content changes generate audit records. Developer tools support content review, drafts, publishing, duplication and audit export; public builds only read published content.

User search and moderation are handled by the admin-users Edge Function. It validates the caller with Auth and is_admin before using its server credential. Self-action and actions against protected administrator accounts are rejected. User deletion requires explicit target confirmation. Audits are recorded; user passwords/session tokens are not exposed in the UI.

Do not use service-role credentials in an Android build. Public users cannot call admin_user_search directly; this function is restricted to the server role. The owner dashboard remains authoritative even if the client is modified.

## Publishing application updates

Use the server-authorized native Admin release form or an owner-controlled SQL session to register a verified GitHub APK, following [Publishing updates](PEMBARUAN_APLIKASI.md). Do not change version metadata to advertise an APK with a different internal version, checksum, or signer.

## Verification

Run node tools/test-admin.mjs and node tools/test-updates.mjs for local role/RLS tests. Read-only production checks can verify anonymous denial without reading private users or making moderation changes. The current report distinguishes local tests from authenticated production/device tests.
