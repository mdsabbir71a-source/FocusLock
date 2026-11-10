import { strict as assert } from "node:assert";
const kp=await crypto.subtle.generateKey({name:"RSASSA-PKCS1-v1_5",modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:"SHA-256"},true,["sign","verify"]);
const pk=Buffer.from(await crypto.subtle.exportKey("pkcs8",kp.privateKey)).toString("base64");
const credential=JSON.stringify({client_email:"billing@example.test",private_key:"-----BEGIN PRIVATE KEY-----\n"+pk+"\n-----END PRIVATE KEY-----"});
let handle;
globalThis.Deno={env:{get:(k)=>k==="GOOGLE_PLAY_SERVICE_ACCOUNT_JSON"?credential:undefined},serve:(cb)=>{handle=cb;}};
globalThis.fetch=async (url,options)=> {
 if(url==="https://oauth2.googleapis.com/token") return Response.json({access_token:"test-access-token"});
 assert.ok(url.includes("/applications/io.focuslock.app/purchases/subscriptionsv2/tokens/"));
 return Response.json({subscriptionState:"SUBSCRIPTION_STATE_ACTIVE",lineItems:[{productId:"focuslock_premium",offerDetails:{basePlanId:"yearly"},expiryTime:new Date(Date.now()+3600000).toISOString()}]});
};
await import("../supabase/functions/verify-play-purchase/index.ts");
assert.equal((await handle(new Request("https://test.invalid",{method:"GET"}))).status,405);
assert.equal((await handle(new Request("https://test.invalid",{method:"POST",body:'{}'}))).status,400);
const res=await handle(new Request("https://test.invalid",{method:"POST",body:JSON.stringify({purchaseToken:"a".repeat(30)})}));
assert.equal(res.status,200);assert.equal((await res.json()).entitled,true);
console.log("Verification endpoint simulated Google test passed.");

