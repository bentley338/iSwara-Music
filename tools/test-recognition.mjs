import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { PGlite } from '@electric-sql/pglite';
import { handler, supportedClip } from '../supabase/functions/recognize-song/index.ts';

const db = new PGlite();
await db.exec('create role anon; create role authenticated; create role service_role;');
await db.exec(readFileSync(new URL('../supabase/migrations/202611040006_recognition_quota.sql', import.meta.url), 'utf8'));
const reserve = async (id) => (await db.query('select public.reserve_recognition($1) as allowed', [id])).rows[0].allowed;
await db.exec('set role anon');
await assert.rejects(reserve('a'.repeat(64)), /permission denied/i);
await db.exec('set role service_role');
assert.equal(await reserve('not-a-hash'), false);
assert.equal(await reserve('a'.repeat(64)), true);
assert.equal(await reserve('a'.repeat(64)), true);
assert.equal(await reserve('a'.repeat(64)), false);
for (let i = 0; i < 18; i++) assert.equal(await reserve(i.toString(16).padStart(3, '0').padEnd(64, '0')), true);
assert.equal(await reserve('f'.repeat(64)), false);
await db.close();

const rate = 8_000;
const wave = Buffer.alloc(rate * 2 + 44);
wave.write('RIFF'); wave.writeUInt32LE(wave.length - 8, 4); wave.write('WAVEfmt ', 8);
wave.writeUInt32LE(16, 16); wave.writeUInt16LE(1, 20); wave.writeUInt16LE(1, 22);
wave.writeUInt32LE(rate, 24); wave.writeUInt32LE(rate * 2, 28); wave.writeUInt16LE(2, 32);
wave.writeUInt16LE(16, 34); wave.write('data', 36); wave.writeUInt32LE(rate * 2, 40);
assert.equal(supportedClip(wave, 'audio/wav'), true);
assert.equal(supportedClip(wave.subarray(0, 512), 'audio/wav'), false);

const origin = 'https://edge.invalid';
globalThis.Deno = { env: new Map() };
assert.equal((await handler(new Request(origin, { method: 'HEAD' }))).status, 503);
Deno.env.set('AUDD_API_TOKEN', 'test-token-only');
Deno.env.set('SUPABASE_URL', 'https://database.invalid');
Deno.env.set('SUPABASE_SERVICE_ROLE_KEY', 'test-role-only');
assert.equal((await handler(new Request(origin, { method: 'HEAD' }))).status, 204);
const audio = () => new Request(origin, { method: 'POST', body: wave, headers: { 'Content-Type': 'audio/wav' } });
let quota = false;
let providerCalls = 0;
globalThis.fetch = async (url, options) => {
    if (url.startsWith('https://database.invalid/')) {
        assert.match(JSON.parse(options.body).p_client, /^[a-f0-9]{64}$/);
        return Response.json(quota);
    }
    assert.equal(url, 'https://api.audd.io/');
    assert.equal(options.body.get('file').size, wave.length);
    assert.equal(options.body.get('api_token'), 'test-token-only');
    providerCalls++;
    return Response.json({ status: 'success', result: { title: 'Sample', artist: 'Artist', album: 'Album', extra: 'removed' } });
};
assert.equal((await handler(audio())).status, 429);
assert.equal(providerCalls, 0);
quota = true;
assert.deepEqual(await (await handler(audio())).json(), { status: 'success', result: { title: 'Sample', artist: 'Artist', album: 'Album' } });
assert.equal(providerCalls, 1);
console.log('Recognition SQL and edge tests passed');
