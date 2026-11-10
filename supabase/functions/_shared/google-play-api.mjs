// Server-only Google Play API helper. Never ship service account keys in the app.
const TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const PACKAGE = "io.focuslock.app";
function base64url(bytes) {
  let raw = "";
  for (const byte of bytes) raw += String.fromCharCode(byte);
  return btoa(raw).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
const encoded = (value) => base64url(new TextEncoder().encode(JSON.stringify(value)));
async function accessToken(credentials, fetchFn = fetch) {
  if (!credentials?.client_email || !credentials?.private_key) throw new Error("Missing Google service account credentials");
  const now = Math.floor(Date.now() / 1000);
  const header = encoded({ alg: "RS256", typ: "JWT" });
  const claim = encoded({ iss: credentials.client_email, scope: SCOPE, aud: TOKEN_ENDPOINT, iat: now, exp: now + 3600 });
  const pem = credentials.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const keyBytes = Uint8Array.from(atob(pem), (ch) => ch.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", keyBytes, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const signingInput = header + "." + claim;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(signingInput));
  const body = new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: signingInput + "." + base64url(new Uint8Array(signature)) });
  const response = await fetchFn(TOKEN_ENDPOINT, { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" }, body });
  if (!response.ok) throw new Error("Google authorization failed");
  const json = await response.json();
  if (typeof json.access_token !== "string" || !json.access_token) throw new Error("Google did not return an access token");
  return json.access_token;
}
export async function getPlaySubscription(credentials, purchaseToken, fetchFn = fetch) {
  if (typeof purchaseToken !== "string" || !purchaseToken || purchaseToken.length > 4096) throw new Error("Invalid purchase token");
  const token = await accessToken(credentials, fetchFn);
  const url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PACKAGE +
    "/purchases/subscriptionsv2/tokens/" + encodeURIComponent(purchaseToken);
  const result = await fetchFn(url, { headers: { authorization: "Bearer " + token } });
  if (!result.ok) throw new Error("Google Play subscription verification failed");
  return await result.json();
}
