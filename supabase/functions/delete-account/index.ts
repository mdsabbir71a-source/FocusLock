import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
  "Cache-Control": "no-store",
};

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405, headers: cors });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const authorization = request.headers.get("Authorization") ?? "";

  if (!authorization.startsWith("Bearer ") || !supabaseUrl || !anonKey || !serviceRoleKey) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), { status: 401, headers: cors });
  }

  const userResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
    headers: { apikey: anonKey, Authorization: authorization },
  });
  if (!userResponse.ok) {
    return new Response(JSON.stringify({ error: "Your session is invalid or expired." }), { status: 401, headers: cors });
  }
  const user = await userResponse.json();
  if (!user?.id) {
    return new Response(JSON.stringify({ error: "User account was not found." }), { status: 401, headers: cors });
  }

  const adminResponse = await fetch(`${supabaseUrl}/rest/v1/rpc/current_user_is_admin`, {
    method: "POST",
    headers: { apikey: anonKey, Authorization: authorization, "Content-Type": "application/json" },
    body: "{}",
  });
  const isAdmin = adminResponse.ok && (await adminResponse.json()) === true;
  if (isAdmin) {
    return new Response(JSON.stringify({ error: "The FocusLock owner account cannot be deleted inside the app." }), { status: 403, headers: cors });
  }

  const deleteResponse = await fetch(`${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(user.id)}`, {
    method: "DELETE",
    headers: { apikey: serviceRoleKey, Authorization: `Bearer ${serviceRoleKey}`, "Content-Type": "application/json" },
  });
  if (!deleteResponse.ok) {
    return new Response(JSON.stringify({ error: "Account deletion could not be completed. Please contact support." }), { status: 502, headers: cors });
  }
  return new Response(JSON.stringify({ deleted: true }), { status: 200, headers: cors });
});
