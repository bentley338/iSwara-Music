const MAX_CLIP = 2_000_044;
const MAX_REPLY = 65_536;
const utf8 = new TextEncoder();

function reply(status: number, code: string): Response {
  return Response.json({ status: "error", code }, { status, headers: { "Cache-Control": "no-store" } });
}

async function limited(body: ReadableStream<Uint8Array> | null, max: number): Promise<Uint8Array<ArrayBuffer>> {
  if (!body) throw new Error("body_missing");
  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let bytes = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      bytes += value.length;
      if (bytes > max) throw new Error("body_too_large");
      chunks.push(value);
    }
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  } finally {
    reader.releaseLock();
  }
  const output = new Uint8Array(bytes);
  let cursor = 0;
  for (const chunk of chunks) { output.set(chunk, cursor); cursor += chunk.length; }
  return output;
}

export function supportedClip(clip: Uint8Array, type: string): boolean {
  const text = (start: number, value: string) => [...value].every((c, i) => clip[start + i] === c.charCodeAt(0));
  if (type === "audio/mp4") return clip.length >= 1_024 && clip.length <= 256_000 && text(4, "ftyp");
  if (type !== "audio/wav" || clip.length < 44 || !text(0, "RIFF") || !text(8, "WAVEfmt ") || !text(36, "data")) return false;
  const head = new DataView(clip.buffer, clip.byteOffset, clip.byteLength);
  const channels = head.getUint16(22, true), rate = head.getUint32(24, true);
  const stride = channels * 2, pcm = clip.length - 44;
  return head.getUint32(4, true) === clip.length - 8 && head.getUint32(16, true) === 16 && head.getUint16(20, true) === 1 &&
    (channels === 1 || channels === 2) && rate >= 8_000 && rate <= 48_000 && head.getUint32(28, true) === rate * stride &&
    head.getUint16(32, true) === stride && head.getUint16(34, true) === 16 && head.getUint32(40, true) === pcm &&
    pcm % stride === 0 && pcm >= rate * stride && pcm <= rate * stride * 12;
}

async function reserve(req: Request, token: string, url: string, role: string): Promise<boolean> {
  const hint = req.headers.get("cf-connecting-ip") ?? req.headers.get("x-forwarded-for")?.split(",").at(-1) ?? "unknown";
  const key = await crypto.subtle.importKey("raw", utf8.encode(token), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const digest = await crypto.subtle.sign("HMAC", key, utf8.encode(hint.trim().slice(0, 64)));
  const client = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  const rpc = await fetch(`${url}/rest/v1/rpc/reserve_recognition`, {
    method: "POST", headers: { apikey: role, Authorization: `Bearer ${role}`, "Content-Type": "application/json" },
    body: JSON.stringify({ p_client: client }), signal: AbortSignal.timeout(4_000), redirect: "error",
  });
  if (!rpc.ok) throw new Error("quota_unavailable");
  return await rpc.json() === true;
}

export async function handler(req: Request): Promise<Response> {
  if (req.method !== "POST" && req.method !== "HEAD") return reply(405, "method");
  const token = Deno.env.get("AUDD_API_TOKEN")?.trim();
  const url = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
  const role = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!token || !url || !role) return req.method === "HEAD" ? new Response(null, { status: 503 }) : reply(503, "unavailable");
  if (req.method === "HEAD") return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
  const type = req.headers.get("Content-Type")?.toLowerCase() ?? "";
  if (!new Set(["audio/mp4", "audio/wav"]).has(type)) return reply(415, "audio_type");
  const declared = Number(req.headers.get("Content-Length"));
  if (declared > MAX_CLIP) return reply(413, "audio_size");
  let clip: Uint8Array<ArrayBuffer>;
  try { clip = await limited(req.body, MAX_CLIP); } catch { return reply(413, "audio_size"); }
  if (!supportedClip(clip, type)) return reply(422, "audio_invalid");
  try {
    if (!await reserve(req, token, url, role)) return reply(429, "limit");
  } catch { return reply(503, "unavailable"); }
  try {
    const form = new FormData();
    form.set("api_token", token);
    form.set("file", new Blob([clip], { type }), type === "audio/wav" ? "clip.wav" : "clip.m4a");
    const upstream = await fetch("https://api.audd.io/", { method: "POST", body: form, signal: AbortSignal.timeout(12_000), redirect: "error" });
    if (!upstream.ok) return reply(upstream.status === 429 ? 429 : 502, "provider");
    const result = JSON.parse(new TextDecoder().decode(await limited(upstream.body, MAX_REPLY)));
    if (result.status !== "success") return reply(502, "provider");
    const clean = (value: unknown) => typeof value === "string" ? value.trim().slice(0, 200) : "";
    const title = clean(result.result?.title), artist = clean(result.result?.artist), album = clean(result.result?.album);
    return Response.json({ status: "success", result: title && artist ? { title, artist, album } : null }, { headers: { "Cache-Control": "no-store" } });
  } catch { return reply(502, "provider"); }
}

if (typeof Deno !== "undefined") Deno.serve(handler);
