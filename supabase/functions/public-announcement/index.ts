import "jsr:@supabase/functions-js/edge-runtime.d.ts";

// This endpoint intentionally publishes only the global announcement. It
// never accepts device identifiers and does not expose admin configuration.
Deno.serve(async (request: Request) => {
  const headers = { "Content-Type": "application/json", "Cache-Control": "public, max-age=60" };
  if (request.method !== "GET") return new Response(null, { status: 405 });
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) return new Response(null, { status: 503 });
  try {
    const result = await fetch(`${url}/rest/v1/app_remote_config?id=eq.global&select=announcement_enabled,announcement_title,announcement_body,announcement_button_label,announcement_button_url,updated_at,latest_version_code,latest_version_name`, {
      headers: { apikey: key, Authorization: `Bearer ${key}` },
    });
    if (!result.ok) return new Response(null, { status: 503 });
    const rows = await result.json();
    const row = Array.isArray(rows) ? rows[0] : null;
    if (!row) return new Response(null, { status: 503 });
    return new Response(JSON.stringify({
      announcement_enabled: row.announcement_enabled === true,
      announcement_title: String(row.announcement_title || "").slice(0, 80),
      announcement_body: String(row.announcement_body || "").slice(0, 500),
      announcement_button_label: String(row.announcement_button_label || "").slice(0, 40),
      announcement_button_url: String(row.announcement_button_url || "").slice(0, 2048),
      updated_at: String(row.updated_at || ""),
      latest_version_code: Number.isSafeInteger(row.latest_version_code) && row.latest_version_code > 0 ? row.latest_version_code : 0,
      latest_version_name: String(row.latest_version_name || "").slice(0, 80),
    }), { headers });
  } catch {
    return new Response(null, { status: 503 });
  }
});
