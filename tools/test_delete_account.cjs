const fs = require("node:fs");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const source = fs.readFileSync("supabase/functions/delete-account/index.ts", "utf8")
  .replace(/^import .*;\s*/m, "").replace("request: Request", "request")
  .replace("let isAdmin: unknown;", "let isAdmin;");

async function check(name, adminResponse, payload, expectedStatus, expectedDeletes) {
  let handler;
  let deletes = 0;
  const sandbox = {
    Request, Response, JSON, encodeURIComponent,
    Deno: { env: { get: () => "configured" }, serve: fn => { handler = fn; } },
    fetch: async url => {
      if (url.endsWith("/auth/v1/user")) return new Response(JSON.stringify({id:"11111111-1111-1111-1111-111111111111"}));
      if (url.includes("current_user_is_admin")) return adminResponse();
      if (url.includes("/auth/v1/admin/users/")) { deletes++; return new Response("{}"); }
      throw new Error("Unexpected request");
    },
  };
  vm.runInNewContext(source, sandbox);
  const response = await handler(new Request("https://example.test", {
    method:"POST", headers:{Authorization:"Bearer test"}, body: JSON.stringify(payload)
  }));
  assert.equal(response.status, expectedStatus, name);
  assert.equal(deletes, expectedDeletes, name + " delete count");
}
(async () => {
  await check("admin protected", () => new Response("true"), {}, 403, 0);
  await check("non-admin self deletion", () => new Response("false"), {}, 200, 1);
  await check("failed lookup", () => new Response("error", {status:500}), {}, 503, 0);
  await check("malformed lookup", () => new Response("{}"), {}, 503, 0);
  await check("invalid JSON", () => new Response("bad"), {}, 503, 0);
  await check("non-admin cannot delete other", () => new Response("false"), {targetUserId:"22222222-2222-2222-2222-222222222222"}, 403, 0);
  await check("admin can delete other", () => new Response("true"), {targetUserId:"22222222-2222-2222-2222-222222222222"}, 200, 1);
  console.log("All seven account-deletion authorization checks passed.");
})().catch(error => { console.error(error); process.exitCode = 1; });
