const fs = require("node:fs"), vm = require("node:vm"), assert = require("node:assert/strict");
const code = fs.readFileSync("supabase/functions/public-announcement/index.ts", "utf8")
  .replace(/^import .*;\s*/m, "").replace("request: Request", "request");
async function run(fetchResult, method = "GET") {
  let handler;
  const sandbox = { Request, Response, JSON, String, Array, Deno: {
    env: {get: key => key === "SUPABASE_URL" ? "https://example.test" : "private-test-key"},
    serve: fn => {handler = fn;}
  }, fetch: async () => fetchResult };
  vm.runInNewContext(code, sandbox);
  return handler(new Request("https://example.test", {method}));
}
(async () => {
  let r = await run(new Response(JSON.stringify([{announcement_enabled:true,announcement_title:"Hello",announcement_body:"Update",updated_at:"today",secret:"not-public"}])));
  assert.equal(r.status, 200);
  assert.deepEqual(await r.json(), {announcement_enabled:true,announcement_title:"Hello",announcement_body:"Update",announcement_title_only:false,announcement_button_label:"",announcement_button_url:"",updated_at:"today",latest_version_code:0,latest_version_name:""});
  r = await run(new Response(JSON.stringify([{announcement_enabled:true,announcement_title:"Title only",announcement_body:"  ",updated_at:"today"}])));
  let one = await r.json(); assert.equal(one.announcement_title_only, true); assert.equal(one.announcement_title, ""); assert.equal(one.announcement_body, "Title only");
  r = await run(new Response(JSON.stringify([{announcement_enabled:true,announcement_title:"",announcement_body:"Message only",updated_at:"today"}])));
  one = await r.json(); assert.equal(one.announcement_title_only, false); assert.equal(one.announcement_title, ""); assert.equal(one.announcement_body, "Message only");
  r = await run(new Response(JSON.stringify([{latest_version_code:231,latest_version_name:"1.1.31"}])));
  const config = await r.json(); assert.equal(config.latest_version_code, 231); assert.equal(config.latest_version_name, "1.1.31");
  r = await run(new Response(JSON.stringify([{latest_version_code:"invalid"}])));
  assert.equal((await r.json()).latest_version_code, 0);
  r = await run(new Response("error", {status:500})); assert.equal(r.status, 503);
  r = await run(new Response("[]")); assert.equal(r.status, 503);
  r = await run(new Response("{}"), "POST"); assert.equal(r.status, 405);
  console.log("Public announcement checks passed.");
})().catch(error => { console.error(error); process.exitCode = 1; });
