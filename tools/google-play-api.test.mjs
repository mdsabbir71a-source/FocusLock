import { strict as assert } from "node:assert";
import { webcrypto } from "node:crypto";
import { getPlaySubscription } from "../supabase/functions/_shared/google-play-api.mjs";
const keys = await crypto.subtle.generateKey({ name:"RSASSA-PKCS1-v1_5", modulusLength:2048, publicExponent:new Uint8Array([1,0,1]), hash:"SHA-256" }, true, ["sign","verify"]);
const data = new Uint8Array(await crypto.subtle.exportKey("pkcs8", keys.privateKey));
const pem = "-----BEGIN PRIVATE KEY-----\n" + Buffer.from(data).toString("base64") + "\n-----END PRIVATE KEY-----";
let count=0;
const fakeFetch=async (url, options) => { count++; if (count===1) { assert.equal(url,"https://oauth2.googleapis.com/token"); assert.match(String(options.body),/grant_type=/); return new Response(JSON.stringify({access_token:"test"})); } assert.ok(url.includes("/applications/io.focuslock.app/purchases/subscriptionsv2/tokens/test-token")); assert.equal(options.headers.authorization,"Bearer test"); return new Response(JSON.stringify({subscriptionState:"SUBSCRIPTION_STATE_ACTIVE"})); };
const result=await getPlaySubscription({client_email:"test@example.com",private_key:pem},"test-token",fakeFetch); assert.equal(result.subscriptionState,"SUBSCRIPTION_STATE_ACTIVE"); console.log("Google Play API helper test passed.");

