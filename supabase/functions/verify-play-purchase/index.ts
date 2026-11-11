// Purchase-token verification endpoint. The unguessable Play purchase token is
// validated directly with Google; no premium access is granted by this service
// unless Google reports a matching, unexpired subscription.
import { verifyPlaySubscription } from "../_shared/verify-play-subscription.mjs";
const headers = { "Content-Type": "application/json", "Cache-Control": "no-store" };
function json(status: number, data: Record<string, unknown>) {
  return new Response(JSON.stringify(data), { status, headers });
}
Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  // Supabase's JWT gateway must remain enabled. A public legacy anon key is
  // itself a valid JWT, so require an actual authenticated user session too.
  try {
    const authorization = req.headers.get("authorization") || "";
    if (!authorization.startsWith("Bearer ")) return json(401, { error: "Authentication required" });
    const parts = authorization.slice(7).split(".");
    if (parts.length !== 3) return json(401, { error: "Authentication required" });
    const payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, "=")));
    if (claims.role !== "authenticated" || typeof claims.sub !== "string" || !claims.sub) {
      return json(401, { error: "Authentication required" });
    }
  } catch { return json(401, { error: "Authentication required" }); }
  const contentLength = Number(req.headers.get("content-length") ?? "0");
  if (contentLength > 8192) return json(413, { error: "Request too large" });
  let body: unknown;
  try {
    const raw = await req.text();
    if (raw.length > 8192) return json(413, { error: "Request too large" });
    body = JSON.parse(raw);
  } catch { return json(400, { error: "Invalid request" }); }
  const token = (body as Record<string, unknown>)?.purchaseToken;
  if (typeof token !== "string" || token.length < 20 || token.length > 4096)
    return json(400, { error: "Invalid request" });
  // Enforce a shared per-session limit across all function instances.
  // Any error fails closed rather than allowing an unthrottled Google request.
  try {
    const base = Deno.env.get("SUPABASE_URL");
    const apiKey = Deno.env.get("SUPABASE_ANON_KEY");
    if (!base || !apiKey) return json(503, { error: "Verification unavailable" });
    const limited = await fetch(base + "/rest/v1/rpc/consume_play_verify_limit", {
      method: "POST",
      headers: { "apikey": apiKey, "Authorization": req.headers.get("authorization") || "", "Content-Type": "application/json" },
      body: "{}",
      signal: AbortSignal.timeout(5000),
    });
    if (!limited.ok) return json(503, { error: "Verification unavailable" });
    if ((await limited.json()) !== true) return json(429, { error: "Please try again later" });
  } catch {
    return json(503, { error: "Verification unavailable" });
  }
  const secret = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");
  if (!secret) return json(503, { error: "Verification unavailable" });
  try {
    const credentials = JSON.parse(secret);
    if (credentials.client_email !== "focuslock-play-billing@focuslock-5a784.iam.gserviceaccount.com") {
      console.error("play-verification-failure service-account-mismatch");
      return json(503, { error: "Verification unavailable" });
    }
    const result = await verifyPlaySubscription(credentials, token);
    return json(200, result);
  } catch (error) {
    if (error instanceof Error && error.name === "InvalidPlayPurchase") return json(200, { entitled: false, expiresAt: null });
    const message = error instanceof Error ? error.message : "";
    const allowed = /^(Google authorization failed [0-9]{3}|Google Play subscription verification failed [0-9]{3} [A-Za-z]{1,80}|Missing Google service account credentials|Google did not return an access token)$/;
    console.error("play-verification-failure", allowed.test(message) ? message : "credentials or configuration error");
    return json(503, { error: "Verification unavailable" });
  }
});
