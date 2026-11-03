import { createClient } from "npm:@supabase/supabase-js@2.95.3";

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return new Response("Unauthorized", { status: 401 });
  const url = Deno.env.get("SUPABASE_URL")!;
  const auth = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false },
  });
  const { data, error } = await auth.auth.getUser(authorization.slice(7));
  if (error || !data.user) return new Response("Unauthorized", { status: 401 });
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false },
  });
  const result = await admin.auth.admin.deleteUser(data.user.id);
  if (result.error) return Response.json({ message: "Penghapusan akun gagal. Coba lagi." }, { status: 500 });
  return Response.json({ deleted: true });
});
