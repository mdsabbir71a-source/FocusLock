// Draft only. A verifier response must not itself grant premium access.
// Authentication, token ownership, acknowledgement, persistence and renewal
// handling must be completed before this endpoint is deployed or used.
import { verifyPlaySubscription } from "../_shared/verify-play-subscription.mjs";

export async function verifyRequest(request, { credentials, verify = verifyPlaySubscription }) {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  if (!credentials) return new Response("Verification is not configured", { status: 503 });
  let body;
  try { body = await request.json(); } catch { return new Response("Invalid request", { status: 400 }); }
  if (typeof body?.purchaseToken !== "string" || !body.purchaseToken || body.purchaseToken.length > 4096)
    return new Response("Invalid request", { status: 400 });
  try {
    const result = await verify(credentials, body.purchaseToken);
    return Response.json(result, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return new Response("Verification temporarily unavailable", { status: 503 });
  }
}
