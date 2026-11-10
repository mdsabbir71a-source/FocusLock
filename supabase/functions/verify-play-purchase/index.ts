// Purchase-token verification endpoint. The unguessable Play purchase token is
// validated directly with Google; no premium access is granted by this service
// unless Google reports a matching, unexpired subscription.
import { verifyPlaySubscription } from "../_shared/verify-play-subscription.mjs";
const limitWindow = 60_000;
let windowStarted = Date.now();
let attempts = 0;
const headers = { "Content-Type": "application/json", "Cache-Control": "no-store" };
function json(status: number, data: Record<string, unknown>) {
  return new Response(JSON.stringify(data), { status, headers });
}
Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  const now = Date.now();
  if (now - windowStarted >= limitWindow) { windowStarted = now; attempts = 0; }
  if (++attempts > 60) return json(429, { error: "Please try again later" });
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
  const secret = Deno.env.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON");
  if (!secret) return json(503, { error: "Verification unavailable" });
  try {
    const credentials = JSON.parse(secret);
    const result = await verifyPlaySubscription(credentials, token);
    return json(200, result);
  } catch { return json(503, { error: "Verification unavailable" }); }
});
