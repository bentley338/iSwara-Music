create sequence public.document_revision;

create table public.documents (
  owner uuid not null references auth.users(id) on delete cascade default auth.uid(),
  id text not null check (length(id) between 1 and 300),
  kind text not null check (kind in ('playlist', 'favorite', 'history')),
  payload jsonb not null check (jsonb_typeof(payload) = 'object' and octet_length(payload::text) <= 2000000),
  revision bigint not null default nextval('public.document_revision'),
  deleted boolean not null default false,
  operation uuid not null,
  modified bigint not null default (extract(epoch from clock_timestamp()) * 1000)::bigint,
  primary key (owner, id)
);
create index documents_owner_revision on public.documents(owner, revision);
alter table public.documents enable row level security;
revoke all on public.documents from anon, authenticated;
grant select on public.documents to authenticated;
create policy own_documents_select on public.documents for select to authenticated using (owner = (select auth.uid()));

create table public.sync_receipts (
  owner uuid not null references auth.users(id) on delete cascade,
  operation uuid not null,
  response jsonb not null,
  created_at timestamptz not null default now(),
  primary key(owner, operation)
);
alter table public.sync_receipts enable row level security;
revoke all on public.sync_receipts from anon, authenticated;

create function public.sync_document(p_id text, p_kind text, p_payload jsonb, p_base_revision bigint, p_deleted boolean, p_operation uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  caller uuid := auth.uid();
  existing public.documents;
  answer jsonb;
begin
  if caller is null then raise exception 'Authentication required' using errcode = '42501'; end if;
  if p_kind not in ('playlist', 'favorite', 'history') or length(p_id) not between 1 and 300 then raise exception 'Invalid document'; end if;
  if jsonb_typeof(p_payload) <> 'object' or octet_length(p_payload::text) > 2000000 then raise exception 'Invalid payload'; end if;
  perform pg_advisory_xact_lock(hashtextextended(caller::text || ':' || p_operation::text, 0));
  select response into answer from public.sync_receipts where owner = caller and operation = p_operation;
  if found then return answer; end if;
  perform pg_advisory_xact_lock(hashtextextended(caller::text || ':' || p_id, 0));
  select * into existing from public.documents where owner = caller and id = p_id;
  if (existing.id is null and p_base_revision <> 0) or (existing.id is not null and existing.revision <> p_base_revision) then
    if existing.id is null then raise exception 'Missing document'; end if;
    answer := jsonb_build_object('conflict', true, 'document', to_jsonb(existing));
  else
    if existing.id is not null and existing.kind <> p_kind then raise exception 'Document kind cannot change'; end if;
    insert into public.documents(owner,id,kind,payload,deleted,operation)
      values(caller,p_id,p_kind,p_payload,p_deleted,p_operation)
    on conflict(owner,id) do update set payload = excluded.payload, deleted = excluded.deleted,
      operation = excluded.operation, revision = nextval('public.document_revision'),
      modified = (extract(epoch from clock_timestamp()) * 1000)::bigint
    returning * into existing;
    answer := jsonb_build_object('conflict', false, 'document', to_jsonb(existing));
  end if;
  insert into public.sync_receipts(owner,operation,response) values(caller,p_operation,answer);
  return answer;
end;
$$;
revoke all on function public.sync_document(text,text,jsonb,bigint,boolean,uuid) from public, anon;
grant execute on function public.sync_document(text,text,jsonb,bigint,boolean,uuid) to authenticated;
