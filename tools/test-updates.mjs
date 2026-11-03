import {PGlite} from '@electric-sql/pglite';
import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const db=new PGlite();
await db.exec(`create schema auth;create role anon;create role authenticated;create role service_role bypassrls;
create table auth.users(id uuid primary key,email text,created_at timestamptz default now(),email_confirmed_at timestamptz,last_sign_in_at timestamptz,banned_until timestamptz);
create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
grant usage on schema auth to anon,authenticated,service_role;`);
for(const file of ['202611010001_library.sql','202611020002_admin.sql','202611030005_app_updates.sql','202611030005_app_updates.sql'])await db.exec(await readFile(new URL('../supabase/migrations/'+file,import.meta.url),'utf8'));
const admin='00000000-0000-0000-0000-000000000081',user='00000000-0000-0000-0000-000000000082';
await db.query("insert into auth.users(id,email,email_confirmed_at) values($1,'admin@example.test',now()),($2,'user@example.test',now())",[admin,user]);await db.query('insert into public.admin_members(user_id) values($1)',[admin]);
async function as(id,role='authenticated'){await db.exec('reset role;set role '+role);await db.query("select set_config('request.jwt.claim.sub',$1,false)",[id]);}
async function save(code=12,published=true,channel='stable',sdk=26,url='https://github.com/example/iswara/releases/download/v0.10.0/iSwara.apk',sha='a'.repeat(64)) {
 return db.query('select public.save_app_release($1,$2,$3,$4,$5,$6,$7,$8,$9)',[code,'0.10.0',url,sha,174000000,'Release fixture',channel,sdk,published]);
}
async function check(current=11,sdk=36,channel='stable'){return (await db.query('select public.check_app_update($1,$2,$3) data',[current,sdk,channel])).rows[0].data;}
await as(user);await assert.rejects(save(),e=>e.code==='42501');await assert.rejects(db.query("insert into public.app_releases(version_code) values(12)"),e=>e.code==='42501');
await as(admin);await save();await save();await save(13,false);await save(14,true,'beta');await save(15,true,'stable',37);
await assert.rejects(save(20,true,'stable',26,'https://github.com.evil.example/payload.apk'));
await assert.rejects(save(20,true,'stable',26,undefined,'invalid'));
await as('', 'anon');const update=await check();assert.equal(update.version_code,12);assert.equal('updated_by' in update,false);assert.deepEqual(await check(15),{});assert.equal((await check(11,36,'beta')).version_code,14);
await assert.rejects(db.query('select * from public.app_releases'),e=>e.code==='42501');await assert.rejects(save(),e=>e.code==='42501');
await as(user);assert.equal((await db.query('select * from public.app_releases')).rows.length,0);
await as(admin);await save(12,false);await as('', 'anon');assert.deepEqual(await check(),{});
await db.exec('reset role');await db.query("update auth.users set banned_until=now()+interval '1 day' where id=$1",[admin]);await as(admin);await assert.rejects(save(),e=>e.code==='42501');
console.log('PASS: repeatable update migration, admin-only idempotent publishing, draft privacy, anonymous discovery, version/channel/SDK filtering, revoke, suspended-admin denial and unsafe-link/checksum rejection.');
await db.close();
