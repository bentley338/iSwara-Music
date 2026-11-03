begin;
create table if not exists public.listening_rooms (
  id uuid primary key default gen_random_uuid(), host_id uuid not null references auth.users(id) on delete cascade,
  creation_op uuid not null, code text not null unique, title text not null check(length(title) between 1 and 80),
  queue jsonb not null default '[]' check(jsonb_typeof(queue)='array' and jsonb_array_length(queue)<=200),
  current_index integer not null default 0, position_ms bigint not null default 0 check(position_ms between 0 and 86400000),
  playing boolean not null default false, revision bigint not null default 0,
  changed_at timestamptz not null default clock_timestamp(), host_seen_at timestamptz not null default clock_timestamp(),
  expires_at timestamptz not null default (now()+interval '6 hours'), closed_at timestamptz,
  unique(host_id,creation_op)
);
create table if not exists public.listening_members (
  room_id uuid not null references public.listening_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  joined_at timestamptz not null default now(), primary key(room_id,user_id)
);
create table if not exists public.listening_receipts (
  room_id uuid not null references public.listening_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade, operation uuid not null,
  created_at timestamptz not null default now(), primary key(room_id,user_id,operation)
);
alter table public.listening_rooms enable row level security;
alter table public.listening_members enable row level security;
alter table public.listening_receipts enable row level security;
revoke all on public.listening_rooms,public.listening_members,public.listening_receipts from public,anon,authenticated;
grant select on public.listening_rooms,public.listening_members to authenticated;
grant all on public.listening_rooms,public.listening_members,public.listening_receipts to service_role;

create or replace function public.listening_identity() returns uuid language plpgsql stable security definer set search_path='' as $$
begin
  if not public.account_active() or not exists(select 1 from auth.users where id=auth.uid() and email_confirmed_at is not null) then
    raise exception 'Masuk dengan akun yang sudah terverifikasi.' using errcode='42501';
  end if;
  return auth.uid();
end $$;
create or replace function public.listening_member(p_room uuid) returns boolean language sql stable security definer set search_path='' as $$
  select public.account_active() and exists(select 1 from public.listening_members where room_id=p_room and user_id=auth.uid());
$$;
drop policy if exists room_members_read on public.listening_rooms;
create policy room_members_read on public.listening_rooms for select to authenticated using(public.listening_member(id));
drop policy if exists members_read on public.listening_members;
create policy members_read on public.listening_members for select to authenticated using(public.listening_member(room_id));

create or replace function public.listening_tracks(p_tracks jsonb) returns jsonb language plpgsql immutable set search_path='' as $$
declare t jsonb; result jsonb := '[]'; duration integer;
begin
  if p_tracks is null or jsonb_typeof(p_tracks) <> 'array' or jsonb_array_length(p_tracks)>200 then raise exception 'Antrean maksimal 200 lagu.'; end if;
  for t in select value from jsonb_array_elements(p_tracks) loop
    if coalesce(t->>'id','') !~ '^[A-Za-z0-9_-]{11}$' or coalesce(t->>'title','')='' or coalesce(t->>'local','false')<>'false' then raise exception 'Room hanya menerima lagu streaming.'; end if;
    if coalesce(t->>'duration','0') !~ '^[0-9]{1,6}$' then raise exception 'Durasi lagu tidak valid.'; end if;
    duration := least(86400,coalesce(t->>'duration','0')::integer);
    result := result || jsonb_build_array(jsonb_build_object('id',t->>'id','title',left(t->>'title',200),'artist',left(coalesce(t->>'artist',''),200),
      'artwork',case when t->>'artwork' like 'https://%' then left(t->>'artwork',2000) else '' end,
      'duration',duration,'url','https://www.youtube.com/watch?v='||(t->>'id'),'local',false,'device',''));
  end loop;
  return result;
end $$;
create or replace function public.listening_state(p_room uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare r public.listening_rooms;
begin
  perform public.listening_identity();
  if not public.listening_member(p_room) then raise exception 'Kamu bukan anggota room ini.' using errcode='42501'; end if;
  select * into strict r from public.listening_rooms where id=p_room;
  return (to_jsonb(r)-'creation_op') || jsonb_build_object('server_ms',floor(extract(epoch from clock_timestamp())*1000)::bigint,
    'changed_ms',floor(extract(epoch from r.changed_at)*1000)::bigint,'host_seen_ms',floor(extract(epoch from r.host_seen_at)*1000)::bigint,
    'expires_ms',floor(extract(epoch from r.expires_at)*1000)::bigint,
    'closed',r.closed_at is not null or r.expires_at<=now(),
    'members',(select count(*) from public.listening_members where room_id=p_room));
end $$;
create or replace function public.create_listening_room(p_title text,p_operation uuid) returns jsonb language plpgsql security definer set search_path='' as $$
declare uid uuid := public.listening_identity(); rid uuid;
begin
  perform pg_advisory_xact_lock(hashtextextended(uid::text,0));
  select id into rid from public.listening_rooms where host_id=uid and creation_op=p_operation;
  if rid is not null then return public.listening_state(rid); end if;
  delete from public.listening_rooms where expires_at<now()-interval '24 hours';
  if (select count(*) from public.listening_rooms where host_id=uid and closed_at is null and expires_at>now())>=3 then raise exception 'Tutup room lama dahulu; maksimal 3 room aktif.'; end if;
  if length(trim(p_title)) not between 1 and 80 then raise exception 'Nama room diperlukan, maksimal 80 karakter.'; end if;
  insert into public.listening_rooms(host_id,creation_op,code,title) values(uid,p_operation,upper(substr(replace(gen_random_uuid()::text,'-',''),1,12)),trim(p_title)) returning id into rid;
  insert into public.listening_members(room_id,user_id) values(rid,uid);
  return public.listening_state(rid);
end $$;
create or replace function public.join_listening_room(p_code text) returns jsonb language plpgsql security definer set search_path='' as $$
declare uid uuid := public.listening_identity(); r public.listening_rooms;
begin
  if length(p_code)>40 then raise exception 'Kode room tidak valid.'; end if;
  select * into r from public.listening_rooms where code=regexp_replace(upper(p_code),'[^A-Z0-9]','','g') and closed_at is null and expires_at>now() for update;
  if r.id is null then raise exception 'Room tidak ditemukan atau sudah berakhir.'; end if;
  if not exists(select 1 from public.listening_members where room_id=r.id and user_id=uid) then
    if (select count(*) from public.listening_members where room_id=r.id)>=32 then raise exception 'Room penuh; maksimal 32 anggota.'; end if;
    insert into public.listening_members(room_id,user_id) values(r.id,uid);
    update public.listening_rooms set revision=revision+1 where id=r.id;
  end if;
  return public.listening_state(r.id);
end $$;
create or replace function public.listening_command(p_room uuid,p_action text,p_operation uuid,p_tracks jsonb default '[]',p_index integer default 0,p_position bigint default 0,p_playing boolean default false,p_expected bigint default -1)
returns jsonb language plpgsql security definer set search_path='' as $$
declare uid uuid:=public.listening_identity(); r public.listening_rooms; tracks jsonb; stamp timestamptz:=clock_timestamp(); pos bigint;
begin
  select * into r from public.listening_rooms where id=p_room for update;
  if r.id is null or not public.listening_member(p_room) then raise exception 'Kamu bukan anggota room ini.' using errcode='42501'; end if;
  if exists(select 1 from public.listening_receipts where room_id=p_room and user_id=uid and operation=p_operation) then return public.listening_state(p_room); end if;
  if r.closed_at is not null or r.expires_at<=now() then raise exception 'Room sudah berakhir.'; end if;
  if p_action not in ('state','enqueue','enqueue_next','close') then raise exception 'Perintah tidak valid.'; end if;
  if p_action<>'enqueue' and r.host_id<>uid then raise exception 'Kontrol ini hanya untuk host.' using errcode='42501'; end if;
  if p_action='state' and p_expected<>r.revision then return jsonb_build_object('conflict',true,'state',public.listening_state(p_room)); end if;
  pos:=least(86400000,r.position_ms+case when r.playing then greatest(0,floor(extract(epoch from stamp-r.changed_at)*1000)::bigint) else 0 end);
  if p_action in ('enqueue','enqueue_next') then
    tracks:=public.listening_tracks(p_tracks);
    if jsonb_array_length(tracks) not between 1 and 20 or jsonb_array_length(r.queue)+jsonb_array_length(tracks)>200 then raise exception 'Tambahkan 1-20 lagu; room maksimal 200 lagu.'; end if;
    if p_action='enqueue' or jsonb_array_length(r.queue)=0 then tracks:=r.queue||tracks;
    else
      tracks:=coalesce((select jsonb_agg(value order by ordinality) from jsonb_array_elements(r.queue) with ordinality where ordinality<=r.current_index+1),'[]') || tracks ||
        coalesce((select jsonb_agg(value order by ordinality) from jsonb_array_elements(r.queue) with ordinality where ordinality>r.current_index+1),'[]');
    end if;
    update public.listening_rooms set queue=tracks,position_ms=pos,changed_at=stamp,revision=revision+1 where id=p_room;
  elsif p_action='state' then
    tracks:=public.listening_tracks(p_tracks);
    if p_index<0 or (jsonb_array_length(tracks)>0 and p_index>=jsonb_array_length(tracks)) then raise exception 'Posisi antrean tidak valid.'; end if;
    update public.listening_rooms set queue=tracks,current_index=case when tracks='[]' then 0 else p_index end,
      position_ms=greatest(0,least(86400000,p_position)),playing=p_playing and tracks<>'[]',changed_at=stamp,host_seen_at=stamp,revision=revision+1 where id=p_room;
  else update public.listening_rooms set closed_at=stamp,position_ms=pos,playing=false,changed_at=stamp,revision=revision+1 where id=p_room;
  end if;
  insert into public.listening_receipts(room_id,user_id,operation) values(p_room,uid,p_operation);
  return public.listening_state(p_room);
end $$;
create or replace function public.leave_listening_room(p_room uuid) returns void language plpgsql security definer set search_path='' as $$
declare uid uuid:=public.listening_identity(); r public.listening_rooms;
begin
  select * into r from public.listening_rooms where id=p_room for update;
  if r.id is null then return; end if;
  if r.host_id=uid then update public.listening_rooms set closed_at=clock_timestamp(),playing=false,revision=revision+1 where id=p_room;
  else delete from public.listening_members where room_id=p_room and user_id=uid;
    if found then update public.listening_rooms set revision=revision+1 where id=p_room; end if;
  end if;
end $$;
revoke all on function public.listening_identity(),public.listening_member(uuid),public.listening_tracks(jsonb),public.listening_state(uuid),public.create_listening_room(text,uuid),public.join_listening_room(text),public.listening_command(uuid,text,uuid,jsonb,integer,bigint,boolean,bigint),public.leave_listening_room(uuid) from public,anon,authenticated;
grant execute on function public.listening_identity(),public.listening_member(uuid),public.listening_state(uuid),public.create_listening_room(text,uuid),public.join_listening_room(text),public.listening_command(uuid,text,uuid,jsonb,integer,bigint,boolean,bigint),public.leave_listening_room(uuid) to authenticated;
alter table public.listening_rooms replica identity full;
do $$ begin
  if exists(select 1 from pg_publication where pubname='supabase_realtime') and not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='listening_rooms') then
    alter publication supabase_realtime add table public.listening_rooms;
  end if;
end $$;
notify pgrst,'reload schema';
commit;
