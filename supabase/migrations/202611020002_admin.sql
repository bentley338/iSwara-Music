-- Roles are assigned by the project owner, never by an Android email check.
begin;
create table if not exists public.admin_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.admin_members enable row level security;
revoke all on public.admin_members from public, anon, authenticated;
grant select on public.admin_members to authenticated;
grant all on public.admin_members to service_role;
drop policy if exists read_own_admin_membership on public.admin_members;
create policy read_own_admin_membership on public.admin_members for select to authenticated using (user_id = (select auth.uid()));

create or replace function public.account_active() returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from auth.users u where u.id = auth.uid() and (u.banned_until is null or u.banned_until <= now()));
$$;
create or replace function public.is_admin() returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.admin_members a join auth.users u on u.id = a.user_id
    where a.user_id = auth.uid() and u.email_confirmed_at is not null and (u.banned_until is null or u.banned_until <= now()));
$$;
revoke all on function public.account_active() from public, anon;
revoke all on function public.is_admin() from public, anon;
grant execute on function public.account_active(), public.is_admin() to authenticated;

create table if not exists public.app_announcements (
  id uuid primary key default gen_random_uuid(),
  title text not null check(length(title) between 1 and 80),
  body text not null check(length(body) <= 600),
  published boolean not null default false,
  position integer not null default 0 check(position between 0 and 1000),
  created_by uuid references auth.users(id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  modified_at timestamptz not null default now()
);
create table if not exists public.featured_collections (
  id uuid primary key default gen_random_uuid(),
  title text not null check(length(title) between 1 and 100),
  subtitle text not null default '' check(length(subtitle) <= 200),
  url text not null check(length(url) <= 1000 and url ~ '^https://((www|music|m)\.)?(youtube\.com|youtu\.be)/'),
  artwork text not null default '' check(length(artwork) <= 2000 and (artwork = '' or artwork like 'https://%')),
  published boolean not null default false,
  position integer not null default 0 check(position between 0 and 1000),
  created_by uuid references auth.users(id) on delete set null default auth.uid(),
  created_at timestamptz not null default now(),
  modified_at timestamptz not null default now()
);
create table if not exists public.admin_audit (
  id uuid primary key default gen_random_uuid(),
  actor uuid references auth.users(id) on delete set null,
  target_user uuid,
  action text not null check(length(action) between 1 and 100),
  entity text,
  entity_id text,
  detail jsonb not null default '{}'::jsonb check(octet_length(detail::text) <= 4096),
  created_at timestamptz not null default now()
);
alter table public.app_announcements enable row level security;
alter table public.featured_collections enable row level security;
alter table public.admin_audit enable row level security;
revoke all on public.app_announcements, public.featured_collections, public.admin_audit from public, anon, authenticated;
grant select on public.app_announcements, public.featured_collections to anon;
grant select, insert, update, delete on public.app_announcements, public.featured_collections to authenticated;
grant select on public.admin_audit to authenticated;
grant all on public.app_announcements, public.featured_collections, public.admin_audit to service_role;

drop policy if exists public_announcements on public.app_announcements;
create policy public_announcements on public.app_announcements for select to anon, authenticated using (published);
drop policy if exists admin_announcements on public.app_announcements;
create policy admin_announcements on public.app_announcements for all to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
drop policy if exists public_featured on public.featured_collections;
create policy public_featured on public.featured_collections for select to anon, authenticated using (published);
drop policy if exists admin_featured on public.featured_collections;
create policy admin_featured on public.featured_collections for all to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
drop policy if exists admin_read_audit on public.admin_audit;
create policy admin_read_audit on public.admin_audit for select to authenticated using ((select public.is_admin()));

create or replace function public.stamp_admin_content() returns trigger language plpgsql set search_path = '' as $$
begin
  new.modified_at := now();
  if tg_op = 'INSERT' then new.created_by := auth.uid(); end if;
  return new;
end;
$$;
create or replace function public.audit_admin_content() returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.admin_audit(actor,action,entity,entity_id) values(auth.uid(),lower(tg_op),tg_table_name,coalesce(new.id,old.id)::text);
  return coalesce(new,old);
end;
$$;
revoke all on function public.stamp_admin_content(), public.audit_admin_content() from public, anon, authenticated;
drop trigger if exists stamp_content on public.app_announcements;
create trigger stamp_content before insert or update on public.app_announcements for each row execute function public.stamp_admin_content();
drop trigger if exists stamp_content on public.featured_collections;
create trigger stamp_content before insert or update on public.featured_collections for each row execute function public.stamp_admin_content();
drop trigger if exists audit_content on public.app_announcements;
create trigger audit_content after insert or update or delete on public.app_announcements for each row execute function public.audit_admin_content();
drop trigger if exists audit_content on public.featured_collections;
create trigger audit_content after insert or update or delete on public.featured_collections for each row execute function public.audit_admin_content();

create or replace function public.admin_summary() returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'Admin required' using errcode = '42501'; end if;
  return jsonb_build_object(
    'accounts',(select count(*) from auth.users),
    'verified',(select count(*) from auth.users where email_confirmed_at is not null),
    'suspended',(select count(*) from auth.users where banned_until > now()),
    'playlists',(select count(*) from public.documents where kind = 'playlist' and not deleted),
    'favorites',(select count(*) from public.documents where kind = 'favorite' and not deleted),
    'history',(select count(*) from public.documents where kind = 'history' and not deleted));
end;
$$;
revoke all on function public.admin_summary() from public, anon;
grant execute on function public.admin_summary() to authenticated;

-- Only the authenticated Edge Function, using its server secret, can list Auth users.
create or replace function public.admin_user_search(p_query text default '', p_page integer default 1) returns jsonb language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb) from (
    select u.id,u.email,u.created_at,u.email_confirmed_at,u.last_sign_in_at,u.banned_until,
      exists(select 1 from public.admin_members a where a.user_id=u.id) as protected_admin
    from auth.users u where coalesce(u.email,'') ilike '%' || left(p_query,100) || '%'
    order by u.created_at desc,u.id limit 50 offset (greatest(1,least(p_page,100000))-1)*50
  ) t;
$$;
revoke all on function public.admin_user_search(text,integer) from public, anon, authenticated;
grant execute on function public.admin_user_search(text,integer) to service_role;

-- Block suspended accounts even while an already issued access token is valid.
drop policy if exists active_account_documents on public.documents;
create policy active_account_documents on public.documents as restrictive for all to authenticated using ((select public.account_active())) with check ((select public.account_active()));
do $$ begin
  if to_regprocedure('public.sync_document_unchecked(text,text,jsonb,bigint,boolean,uuid)') is null then
    alter function public.sync_document(text,text,jsonb,bigint,boolean,uuid) rename to sync_document_unchecked;
  end if;
end $$;
revoke all on function public.sync_document_unchecked(text,text,jsonb,bigint,boolean,uuid) from public, anon, authenticated;
create or replace function public.sync_document(p_id text,p_kind text,p_payload jsonb,p_base_revision bigint,p_deleted boolean,p_operation uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
begin
  if not public.account_active() then raise exception 'Account inactive' using errcode = '42501'; end if;
  return public.sync_document_unchecked(p_id,p_kind,p_payload,p_base_revision,p_deleted,p_operation);
end;
$$;
revoke all on function public.sync_document(text,text,jsonb,bigint,boolean,uuid) from public, anon;
grant execute on function public.sync_document(text,text,jsonb,bigint,boolean,uuid) to authenticated;
notify pgrst, 'reload schema';
commit;
