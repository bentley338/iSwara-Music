import {PGlite} from '@electric-sql/pglite';
import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const db=new PGlite();
await db.exec(`create schema auth; create role anon; create role authenticated; create role service_role bypassrls;
create table auth.users(id uuid primary key,email text,created_at timestamptz default now(),email_confirmed_at timestamptz,last_sign_in_at timestamptz,banned_until timestamptz);
create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
grant usage on schema auth to authenticated,anon,service_role;`);
for(const file of ['202611010001_library.sql','202611020002_admin.sql','202611020003_listen_together.sql','202611030004_radio_lounge.sql','202611030004_radio_lounge.sql'])await db.exec(await readFile(new URL('../supabase/migrations/'+file,import.meta.url),'utf8'));
const host='00000000-0000-0000-0000-000000000021',friend='00000000-0000-0000-0000-000000000022',outsider='00000000-0000-0000-0000-000000000023',unverified='00000000-0000-0000-0000-000000000024';
await db.query("insert into auth.users(id,email,email_confirmed_at) values($1,'host@example.test',now()),($2,'friend@example.test',now()),($3,'other@example.test',now()),($4,'new@example.test',null)",[host,friend,outsider,unverified]);
async function as(id,role='authenticated'){await db.exec('reset role;set role '+role);await db.query("select set_config('request.jwt.claim.sub',$1,false)",[id]);}
const op=()=>crypto.randomUUID();
async function discover(query='',mood='all'){return (await db.query('select public.discover_radio($1,$2) data',[query,mood])).rows[0].data;}
async function create(visibility,operation=op()){return (await db.query('select public.create_radio($1,$2,$3,$4,$5,$6) data',['Radio fixture','Deskripsi fixture',visibility,'chill','Host fixture',operation])).rows[0].data;}
async function state(room){return (await db.query('select public.radio_state($1) data',[room])).rows[0].data;}
async function post(room,body,operation=op(),kind='comment'){return (await db.query('select public.radio_post($1,$2,$3,$4) id',[room,kind,body,operation])).rows[0].id;}
await as(host);const createOp=op();const room=await create('public',createOp);assert.equal(room.radio,true);assert.equal(room.host_id,host);
assert.equal((await create('public',createOp)).id,room.id);const secret=await create('private');
assert.equal((await state(room.id)).visibility,'public');
await as('', 'anon');const publicList=await discover();assert.equal(publicList.items.length,1);assert.equal(publicList.items[0].id,room.id);
assert.equal(JSON.stringify(publicList).includes('host@example.test'),false);assert.equal(JSON.stringify(publicList).includes(host),false);
assert.equal((await discover('not found')).items.length,0);assert.equal((await discover('fixture','focus')).items.length,0);
await assert.rejects(db.query('select * from public.radio_channels'),/permission denied/);
await assert.rejects(state(room.id),/permission denied/);await assert.rejects(post(room.id,'Anon'),/permission denied/);
await as(unverified);await assert.rejects(db.query('select public.join_radio($1,$2)',[room.code,'New']),/terverifikasi/);
await as(outsider);assert.equal((await db.query('select * from public.radio_messages')).rows.length,0);await assert.rejects(state(secret.id),/Bergabung/);
await assert.rejects(db.query('select public.radio_update($1,$2,$3,$4,$5)',[room.id,'Hijack','','public','all']),/host/);
await as(friend);const joined=(await db.query('select public.join_radio($1,$2) data',[room.code,'Listener fixture'])).rows[0].data;assert.equal(joined.members,2);
const messageOp=op();const message=await post(room.id,'Halo lounge',messageOp);assert.equal(await post(room.id,'duplicate',messageOp),message);
await assert.rejects(post(room.id,'too fast'),/Tunggu/);let chat=await state(room.id);assert.equal(chat.messages.length,1);assert.equal(chat.messages[0].nickname,'Listener fixture');assert.equal(chat.can_moderate,false);
await assert.rejects(db.query('select public.radio_moderate($1,$2,$3,$4)',[room.id,'ban',null,host]),/berhak/);
await as(host);await db.query('select public.radio_moderate($1,$2,$3,$4)',[room.id,'remove',message,null]);
chat=await state(room.id);assert.equal(chat.messages[0].removed,true);assert.equal(chat.messages[0].body,'');
await db.query('select public.radio_moderate($1,$2,$3,$4)',[room.id,'ban',null,friend]);
await as(friend);await assert.rejects(state(room.id),/Bergabung/);await assert.rejects(post(room.id,'banned'),/Bergabung/);
await assert.rejects(db.query('select public.join_listening_room($1)',[room.code]),/dibatasi/);
await assert.rejects(db.query('select public.join_radio($1,$2)',[room.code,'Again']),/dibatasi/);
await as(host);await db.query('select public.radio_update($1,$2,$3,$4,$5)',[room.id,'Private now','desc','private','night']);
assert.equal((await discover()).items.length,0);
await db.query('select public.radio_update($1,$2,$3,$4,$5)',[room.id,'Live now','desc','public','night']);
assert.equal((await discover('live','night')).items.length,1);
const reactionOp=op();const reaction=await post(room.id,'heart',reactionOp,'reaction');assert.ok(reaction);
await as('', 'service_role');await db.query('delete from public.radio_messages where id=$1',[reaction]);
await as(host);assert.equal(await post(room.id,'heart',reactionOp,'reaction'),reaction);
assert.equal((await state(room.id)).messages.some(message=>message.id===reaction),false);
await as('', 'service_role');await db.query("update public.listening_rooms set host_seen_at=now()-interval '2 minutes' where id=$1",[room.id]);
await as('', 'anon');assert.equal((await discover()).items.length,0);
await as(outsider);const legacy=(await db.query('select public.create_listening_room($1,$2) data',['Private room fixture',op()])).rows[0].data;
assert.equal(legacy.radio,false);
await as(friend);const legacyJoined=(await db.query('select public.join_listening_room($1) data',[legacy.code])).rows[0].data;
assert.equal(legacyJoined.members,2);assert.equal(legacyJoined.radio,false);
await as(host);await assert.rejects(db.query('select public.listening_state($1)',[legacy.id]),error=>error.code==='42501');
console.log('PASS: repeatable Radio migration, public directory privacy, private isolation, verified join, RLS, host settings, idempotent messages including pruned history, rate limits, reactions, moderation, ban bypass prevention, stale-host filtering and existing private Listen Together compatibility.');
await db.close();
