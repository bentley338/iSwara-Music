begin;

create table if not exists public.recognition_quota (
  scope text primary key,
  bucket timestamptz not null,
  used integer not null check (used >= 0)
);
alter table public.recognition_quota enable row level security;
revoke all on public.recognition_quota from public, anon, authenticated;

create or replace function public.reserve_recognition(p_client text)
returns boolean language plpgsql security definer set search_path = '' as $$
declare
  stamp timestamptz := clock_timestamp();
  month_at timestamptz := date_trunc('month', stamp, 'UTC');
  day_at timestamptz := date_trunc('day', stamp, 'UTC');
  minute_at timestamptz := date_trunc('minute', stamp);
  client_scope text := 'client:' || left(p_client, 3);
begin
  if p_client is null or p_client !~ '^[a-f0-9]{64}$' then return false; end if;
  perform pg_advisory_xact_lock(1057632990, 1);
  if exists (
    select 1 from public.recognition_quota as q
    where (q.scope = 'global-month' and q.bucket = month_at and q.used >= 200)
       or (q.scope = 'global-day' and q.bucket = day_at and q.used >= 20)
       or (q.scope = client_scope and q.bucket = day_at and q.used >= 8)
       or (q.scope = client_scope || ':minute' and q.bucket = minute_at and q.used >= 2)
  ) then return false; end if;
  insert into public.recognition_quota as q (scope, bucket, used)
    values ('global-month', month_at, 1), ('global-day', day_at, 1),
           (client_scope, day_at, 1), (client_scope || ':minute', minute_at, 1)
  on conflict (scope) do update set used = case when q.bucket = excluded.bucket then q.used + 1 else 1 end,
    bucket = excluded.bucket;
  return true;
end $$;

revoke all on function public.reserve_recognition(text) from public, anon, authenticated;
grant execute on function public.reserve_recognition(text) to service_role;
commit;
