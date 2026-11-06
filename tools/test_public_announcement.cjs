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
  assert.deepEqual(await r.json(), {announcement_enabled:true,announcement_title:"Hello",announcement_body:"Update",updated_at:"today"});
  r = await run(new Response("error", {status:500})); assert.equal(r.status, 503);
  r = await run(new Response("[]")); assert.equal(r.status, 503);
  r = await run(new Response("{}"), "POST"); assert.equal(r.status, 405);
  console.log("Public announcement checks passed.");
})().catch(error => { console.error(error); process.exitCode = 1; });
