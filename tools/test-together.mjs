import { PGlite } from '@electric-sql/pglite';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const db = new PGlite();
await db.exec(`create schema auth; create role anon; create role authenticated; create role service_role bypassrls;
  create table auth.users(id uuid primary key,email text,created_at timestamptz default now(),email_confirmed_at timestamptz,last_sign_in_at timestamptz,banned_until timestamptz);
  create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
  grant usage on schema auth to authenticated,anon,service_role;`);
for (const path of ['202611010001_library.sql','202611020002_admin.sql','202611020003_listen_together.sql','202611020003_listen_together.sql'])
  await db.exec(await readFile(new URL('../supabase/migrations/'+path,import.meta.url),'utf8'));

const host='00000000-0000-0000-0000-000000000011',friend='00000000-0000-0000-0000-000000000012',outsider='00000000-0000-0000-0000-000000000013',unverified='00000000-0000-0000-0000-000000000014';
await db.query("insert into auth.users(id,email,email_confirmed_at) values ($1,'host@example.test',now()),($2,'friend@example.test',now()),($3,'other@example.test',now()),($4,'unverified@example.test',null)",[host,friend,outsider,unverified]);
async function as(id,role='authenticated') {await db.exec('reset role; set role '+role);await db.query("select set_config('request.jwt.claim.sub',$1,false)",[id]);}
const id=()=>crypto.randomUUID();
async function state(room) {return (await db.query('select public.listening_state($1) as data',[room])).rows[0].data;}
async function command(room,action,operation,tracks=[],index=0,position=0,playing=false,expected=-1) {return (await db.query('select public.listening_command($1,$2,$3,$4::jsonb,$5,$6,$7,$8) as data',[room,action,operation,JSON.stringify(tracks),index,position,playing,expected])).rows[0].data;}
const track={id:'abcdefghijk',title:'Fixture',artist:'Test',duration:180,url:'https://untrusted.example/ignored',local:false};
await as(host);const createOp=id();
const room=(await db.query('select public.create_listening_room($1,$2) as data',['Room fixture',createOp])).rows[0].data;
assert.equal(room.host_id,host);assert.equal(room.members,1);assert.equal(room.code.length,12);
assert.equal((await db.query('select public.create_listening_room($1,$2) as data',['Retry',createOp])).rows[0].data.id,room.id);
await as(outsider);assert.equal((await db.query('select * from public.listening_rooms')).rows.length,0);
await assert.rejects(state(room.id),/anggota/);
await assert.rejects(db.query('update public.listening_rooms set playing=true'),/permission denied/);
await assert.rejects(db.query('select public.join_listening_room($1)',['NOT-A-REAL-CODE']),/tidak ditemukan/);
await as(unverified);await assert.rejects(db.query('select public.join_listening_room($1)',[room.code]),/terverifikasi/);
await as(friend);const joined=(await db.query('select public.join_listening_room($1) as data',[room.code])).rows[0].data;
assert.equal(joined.members,2);const enqueueOp=id();
const added=await command(room.id,'enqueue',enqueueOp,[track]);assert.equal(added.queue.length,1);
assert.equal(added.queue[0].url,'https://www.youtube.com/watch?v=abcdefghijk');
assert.equal((await command(room.id,'enqueue',enqueueOp,[track])).queue.length,1);
await assert.rejects(command(room.id,'state',id(),[track]),/host/);
await assert.rejects(command(room.id,'enqueue_next',id(),[track]),/host/);
await assert.rejects(command(room.id,'enqueue',id(),[{...track,local:true}]),/streaming/);
await as(host);const stale=await command(room.id,'state',id(),[],0,0,false,0);assert.equal(stale.conflict,true);assert.equal(stale.state.queue.length,1);
const synced=await command(room.id,'state',id(),[track],0,12345,true,added.revision);assert.equal(synced.position_ms,12345);assert.equal(synced.playing,true);
await as(friend);await db.query('select public.leave_listening_room($1)',[room.id]);assert.equal((await db.query('select * from public.listening_rooms')).rows.length,0);await assert.rejects(state(room.id),/anggota/);
await as(host);const closed=await command(room.id,'close',id());assert.equal(closed.closed,true);
await as(friend);await assert.rejects(db.query('select public.join_listening_room($1)',[room.code]),/tidak ditemukan/);
await as('', 'anon');await assert.rejects(db.query('select public.create_listening_room($1,$2)',['Anon',id()]),/permission denied/);
await assert.rejects(db.query('select * from public.listening_rooms'),/permission denied/);
console.log('PASS: repeatable room migration, verified identities, code join, member-only RLS, host authority, canonical tracks, idempotent enqueue, conflict protection, leave revocation, close and anonymous denial.');
await db.close();
