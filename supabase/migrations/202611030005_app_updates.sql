begin;
create table if not exists public.app_releases (
  version_code integer primary key check(version_code>0),
  version_name text not null check(length(version_name) between 1 and 32),
  download_url text not null check(download_url ~ '^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/download/[^?#[:space:]]+\.apk$'),
  sha256 text not null check(sha256 ~ '^[a-f0-9]{64}$'),
  bytes bigint not null check(bytes between 1 and 524288000),
  notes text not null default '' check(length(notes)<=12000),
  channel text not null default 'stable' check(channel in ('stable','beta')),
  min_sdk integer not null default 26 check(min_sdk between 26 and 1000),
  published boolean not null default false,
  released_at timestamptz not null default now(),
  updated_by uuid references auth.users(id) on delete set null
);
alter table public.app_releases enable row level security;
revoke all on public.app_releases from public,anon,authenticated;
grant select on public.app_releases to authenticated;
grant all on public.app_releases to service_role;
drop policy if exists release_admin_read on public.app_releases;
create policy release_admin_read on public.app_releases for select to authenticated using(public.is_admin());
create or replace function public.check_app_update(p_current integer,p_sdk integer,p_channel text default 'stable') returns jsonb
language sql stable security definer set search_path='' as $$
 select coalesce((select to_jsonb(r)-'updated_by' from public.app_releases r
 where r.published and r.released_at<=now() and r.version_code>greatest(0,p_current) and r.min_sdk<=p_sdk
 and r.channel=p_channel order by r.version_code desc limit 1),'{}'::jsonb);
$$;
create or replace function public.save_app_release(p_version_code integer,p_version_name text,p_download_url text,p_sha256 text,p_bytes bigint,p_notes text,p_channel text,p_min_sdk integer,p_published boolean) returns void
language plpgsql security definer set search_path='' as $$
begin
 if not public.is_admin() or not public.account_active() then raise exception 'Khusus admin.' using errcode='42501'; end if;
 insert into public.app_releases(version_code,version_name,download_url,sha256,bytes,notes,channel,min_sdk,published,updated_by)
 values(p_version_code,trim(p_version_name),trim(p_download_url),lower(trim(p_sha256)),p_bytes,p_notes,p_channel,p_min_sdk,p_published,auth.uid())
 on conflict(version_code) do update set version_name=excluded.version_name,download_url=excluded.download_url,sha256=excluded.sha256,bytes=excluded.bytes,
 notes=excluded.notes,channel=excluded.channel,min_sdk=excluded.min_sdk,published=excluded.published,updated_by=auth.uid();
end $$;
revoke all on function public.check_app_update(integer,integer,text) from public,anon,authenticated;
grant execute on function public.check_app_update(integer,integer,text) to anon,authenticated;
revoke all on function public.save_app_release(integer,text,text,text,bigint,text,text,integer,boolean) from public,anon,authenticated;
grant execute on function public.save_app_release(integer,text,text,text,bigint,text,text,integer,boolean) to authenticated;
notify pgrst,'reload schema';
commit;
