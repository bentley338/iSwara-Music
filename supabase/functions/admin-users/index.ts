import { createClient } from "npm:@supabase/supabase-js@2.95.3";

const headers = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, apikey, content-type", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const reply = (status: number, body: unknown) => Response.json(body, { status, headers });
Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
  if (request.method !== "POST") return reply(405, { message: "Method not allowed" });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return reply(401, { message: "Masuk terlebih dahulu." });
  const url = Deno.env.get("SUPABASE_URL")!;
  const caller = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authorization } }, auth: { persistSession: false },
  });
  const { data: identity, error: authError } = await caller.auth.getUser(authorization.slice(7));
  if (authError || !identity.user) return reply(401, { message: "Sesi tidak valid." });
  const { data: allowed, error: roleError } = await caller.rpc("is_admin");
  if (roleError || allowed !== true) return reply(403, { message: "Akses admin diperlukan." });
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
  try {
    const raw = await request.text();
    if (raw.length > 4096) return reply(400, { message: "Permintaan terlalu besar." });
    const body = JSON.parse(raw || "{}");
    if (body.action === "list") {
      const page = Math.max(1, Math.min(100000, Number.isInteger(body.page) ? body.page : 1));
      const { data, error } = await admin.rpc("admin_user_search", { p_query: String(body.query ?? "").slice(0,100), p_page: page });
      if (error) return reply(500, { message: "Daftar pengguna belum dapat dimuat." });
      return reply(200, { users: data, page });
    }
    if (!["suspend", "restore", "delete"].includes(body.action) || !/^[0-9a-f-]{36}$/i.test(body.user_id ?? ""))
      return reply(400, { message: "Tindakan tidak valid." });
    if (body.user_id === identity.user.id) return reply(409, { message: "Akun admin sendiri dilindungi." });
    const { data: protectedMember, error: memberError } = await admin.from("admin_members").select("user_id").eq("user_id",body.user_id).maybeSingle();
    if (memberError) return reply(500, { message: "Peran pengguna belum dapat diperiksa." });
    if (protectedMember) return reply(409, { message: "Akun admin dilindungi." });
    const { data: target, error: targetError } = await admin.auth.admin.getUserById(body.user_id);
    if (targetError || !target.user) return reply(404, { message: "Pengguna tidak ditemukan." });
    if (body.action === "delete" && String(body.confirm_email ?? "").trim().toLowerCase() !== (target.user.email ?? "").toLowerCase())
      return reply(400, { message: "Ketik email pengguna untuk mengonfirmasi penghapusan." });
    const { data: audit, error: auditError } = await admin.from("admin_audit").insert({ actor: identity.user.id, target_user: body.user_id, action: "user_" + body.action + "_requested" }).select("id").single();
    if (auditError) return reply(500, { message: "Pencatatan admin gagal; tindakan dibatalkan." });
    const result = body.action === "delete" ? await admin.auth.admin.deleteUser(body.user_id)
      : await admin.auth.admin.updateUserById(body.user_id, { ban_duration: body.action === "suspend" ? "720h" : "none" });
    await admin.from("admin_audit").update({ action: "user_" + body.action, detail: { result: result.error ? "failed" : "success" } }).eq("id",audit.id);
    if (result.error) return reply(500, { message: "Tindakan belum berhasil. Muat ulang daftar pengguna." });
    return reply(200, { success: true });
  } catch { return reply(400, { message: "Permintaan tidak valid." }); }
});
