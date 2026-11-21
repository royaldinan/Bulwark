"use strict";

const { test, before, after } = require("node:test");
const assert = require("node:assert/strict");

const { createApp, KIND_NUMBER } = require("../src/app");
const { domainHash, normalizeDomain, prefix8, sha256Utf8 } = require("../src/hash");
const { isHex8, isHex32 } = require("../src/validate");

const PREFIX = "0x" + "ab".repeat(8);
const HASH = "0x" + "ab".repeat(32);
const APK_HASH = "0x" + "cd".repeat(32);
const TX = "0x" + "11".repeat(32);

function fakeRegistry(opts = {}) {
  const flagged = new Set((opts.flagged || []).map((p) => p.toLowerCase()));
  const calls = { report: [] };
  const unavailable = () => {
    const err = new Error("not configured");
    err.code = "REGISTRY_NOT_CONFIGURED";
    return err;
  };
  return {
    calls,
    async isFlaggedPrefix(prefix) {
      if (opts.unavailable) throw unavailable();
      if (opts.fail) throw new Error("boom");
      return flagged.has(prefix.toLowerCase());
    },
    async report(hash, kind) {
      if (opts.unavailable) throw unavailable();
      if (opts.fail) throw new Error("boom");
      calls.report.push({ hash, kind });
      return { txHash: opts.txHash || TX };
    },
  };
}

function fakeSafeBrowsing(result = { verdict: "unknown", source: "none" }) {
  return { async lookupDomainPrefix() { return result; } };
}

function start(app) {
  return new Promise((resolve) => {
    const server = app.listen(0, "127.0.0.1", () => {
      resolve({ server, base: `http://127.0.0.1:${server.address().port}` });
    });
  });
}

async function post(base, path, body, headers = {}) {
  const res = await fetch(base + path, {
    method: "POST",
    headers: { "content-type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  return { status: res.status, body: await res.json() };
}

async function get(base, path) {
  const res = await fetch(base + path);
  return { status: res.status, body: await res.json() };
}

let server, base;
const registry = fakeRegistry({ flagged: [PREFIX] });

before(async () => {
  process.env.API_SHARED_TOKEN = "test-token";
  process.env.REPORT_RATE_LIMIT_MAX = "1000";
  process.env.REPORT_RATE_LIMIT_WINDOW_MS = "60000";
  delete process.env.REGISTRY_ADDRESS;
  const app = createApp({ registry, safeBrowsing: fakeSafeBrowsing() });
  ({ server, base } = await start(app));
});

after(() => server && server.close());

test("hash util: SHA-256 of normalized domain, 8-byte prefix", () => {
  assert.equal(normalizeDomain("Scam.Example.com."), "scam.example.com");
  assert.equal(domainHash("Scam.Example.com."), domainHash("scam.example.com"));
  assert.equal(sha256Utf8("scam.example.com").length, 66);
  assert.equal(prefix8(HASH), PREFIX);
  assert.equal(prefix8("0x" + "ab".repeat(32)).length, 18);
});

test("validate util accepts/rejects hex widths", () => {
  assert.equal(isHex8(PREFIX), true);
  assert.equal(isHex8("0xab"), false);
  assert.equal(isHex32(HASH), true);
  assert.equal(isHex32(PREFIX), false);
  assert.equal(isHex32("0x" + "zz".repeat(32)), false);
});

test("kind map matches the contract constants", () => {
  assert.deepEqual(KIND_NUMBER, { domain: 1, apk: 2 });
});

test("GET /health returns ok", async () => {
  const { status, body } = await get(base, "/health");
  assert.equal(status, 200);
  assert.deepEqual(body, { ok: true });
});

test("GET /v1/health returns ok + chain metadata", async () => {
  const { status, body } = await get(base, "/v1/health");
  assert.equal(status, 200);
  assert.equal(body.ok, true);
  assert.equal(body.chainId, 80002);
  assert.equal(body.network, "amoy");
  assert.equal(body.contractAddress, null);
});

test("POST /v1/check/domain: flagged prefix -> malicious/registry", async () => {
  const { status, body } = await post(base, "/v1/check/domain", { prefix: PREFIX });
  assert.equal(status, 200);
  assert.deepEqual(body, { verdict: "malicious", source: "registry" });
});

test("POST /v1/check/domain: unknown prefix -> unknown/none", async () => {
  const { status, body } = await post(base, "/v1/check/domain", { prefix: "0x" + "00".repeat(8) });
  assert.equal(status, 200);
  assert.deepEqual(body, { verdict: "unknown", source: "none" });
});

test("POST /v1/check/domain: rejects a bad prefix", async () => {
  const { status, body } = await post(base, "/v1/check/domain", { prefix: "0x1234" });
  assert.equal(status, 400);
  assert.equal(body.error, "invalid_prefix");
});

test("POST /v1/check/domain: rejects a bad optional hash", async () => {
  const { status, body } = await post(base, "/v1/check/domain", { prefix: PREFIX, hash: "nope" });
  assert.equal(status, 400);
  assert.equal(body.error, "invalid_hash");
});

test("POST /v1/check/domain: Safe Browsing result is surfaced when known", async () => {
  const app = createApp({
    registry: fakeRegistry(),
    safeBrowsing: fakeSafeBrowsing({ verdict: "malicious", source: "safebrowsing" }),
  });
  const { server: s, base: b } = await start(app);
  try {
    const { body } = await post(b, "/v1/check/domain", { prefix: PREFIX });
    assert.deepEqual(body, { verdict: "malicious", source: "safebrowsing" });
  } finally {
    s.close();
  }
});

test("POST /v1/check/domain: registry not configured -> unknown/none", async () => {
  const app = createApp({ registry: fakeRegistry({ unavailable: true }), safeBrowsing: fakeSafeBrowsing() });
  const { server: s, base: b } = await start(app);
  try {
    const { status, body } = await post(b, "/v1/check/domain", { prefix: PREFIX });
    assert.equal(status, 200);
    assert.deepEqual(body, { verdict: "unknown", source: "none" });
  } finally {
    s.close();
  }
});

test("POST /v1/check/domain: registry error -> 502", async () => {
  const app = createApp({ registry: fakeRegistry({ fail: true }), safeBrowsing: fakeSafeBrowsing() });
  const { server: s, base: b } = await start(app);
  try {
    const { status, body } = await post(b, "/v1/check/domain", { prefix: PREFIX });
    assert.equal(status, 502);
    assert.equal(body.error, "registry_unavailable");
  } finally {
    s.close();
  }
});

test("POST /v1/check/domain: Safe Browsing error -> unknown/none (not safe)", async () => {
  const app = createApp({
    registry: fakeRegistry(),
    safeBrowsing: {
      async lookupDomainPrefix() {
        throw new Error("gsb down");
      },
    },
  });
  const { server: s, base: b } = await start(app);
  try {
    const { status, body } = await post(b, "/v1/check/domain", { prefix: PREFIX });
    assert.equal(status, 200);
    assert.deepEqual(body, { verdict: "unknown", source: "none" });
  } finally {
    s.close();
  }
});

test("POST /v1/check/apk: flagged -> malicious/registry", async () => {
  const flaggedPrefix = prefix8(APK_HASH);
  const app = createApp({ registry: fakeRegistry({ flagged: [flaggedPrefix] }), safeBrowsing: fakeSafeBrowsing() });
  const { server: s, base: b } = await start(app);
  try {
    const { status, body } = await post(b, "/v1/check/apk", { sha256: APK_HASH });
    assert.equal(status, 200);
    assert.deepEqual(body, { verdict: "malicious", source: "registry" });
  } finally {
    s.close();
  }
});

test("POST /v1/check/apk: not flagged -> unknown/none (never safe)", async () => {
  const { status, body } = await post(base, "/v1/check/apk", { sha256: APK_HASH });
  assert.equal(status, 200);
  assert.deepEqual(body, { verdict: "unknown", source: "none" });
});

test("POST /v1/check/apk: rejects bad sha256", async () => {
  const { status, body } = await post(base, "/v1/check/apk", { sha256: "0xabc" });
  assert.equal(status, 400);
  assert.equal(body.error, "invalid_sha256");
});

test("POST /v1/report: missing token -> 401", async () => {
  const { status, body } = await post(base, "/v1/report", { kind: "domain", hash: HASH });
  assert.equal(status, 401);
  assert.equal(body.error, "unauthorized");
});

test("POST /v1/report: wrong token -> 401", async () => {
  const { status, body } = await post(
    base,
    "/v1/report",
    { kind: "domain", hash: HASH },
    { "x-api-token": "nope" }
  );
  assert.equal(status, 401);
  assert.equal(body.error, "unauthorized");
});

test("POST /v1/report: valid token -> txHash", async () => {
  const { status, body } = await post(
    base,
    "/v1/report",
    { kind: "domain", hash: HASH },
    { "x-api-token": "test-token" }
  );
  assert.equal(status, 200);
  assert.equal(body.txHash, TX);
  assert.equal(body.kind, "domain");
  assert.equal(body.chainId, 80002);
  assert.deepEqual(registry.calls.report.at(-1), { hash: HASH, kind: 1 });
});

test("POST /v1/report: invalid kind -> 400", async () => {
  const { status, body } = await post(
    base,
    "/v1/report",
    { kind: "url", hash: HASH },
    { "x-api-token": "test-token" }
  );
  assert.equal(status, 400);
  assert.equal(body.error, "invalid_kind");
});

test("POST /v1/report: invalid hash -> 400", async () => {
  const { status, body } = await post(
    base,
    "/v1/report",
    { kind: "apk", hash: "0xzz" },
    { "x-api-token": "test-token" }
  );
  assert.equal(status, 400);
  assert.equal(body.error, "invalid_hash");
});

test("POST /v1/report: registry not configured -> 503", async () => {
  const app = createApp({ registry: fakeRegistry({ unavailable: true }), safeBrowsing: fakeSafeBrowsing() });
  const { server: s, base: b } = await start(app);
  try {
    const { status, body } = await post(
      b,
      "/v1/report",
      { kind: "domain", hash: HASH },
      { "x-api-token": "test-token" }
    );
    assert.equal(status, 503);
    assert.equal(body.error, "registry_not_configured");
  } finally {
    s.close();
  }
});

test("POST /v1/report: rate limit returns 429", async () => {
  process.env.REPORT_RATE_LIMIT_MAX = "2";
  const app = createApp({ registry: fakeRegistry(), safeBrowsing: fakeSafeBrowsing() });
  const { server: s, base: b } = await start(app);
  try {
    const headers = { "x-api-token": "test-token" };
    const first = await post(b, "/v1/report", { kind: "domain", hash: HASH }, headers);
    const second = await post(b, "/v1/report", { kind: "domain", hash: HASH }, headers);
    const third = await post(b, "/v1/report", { kind: "domain", hash: HASH }, headers);
    assert.equal(first.status, 200);
    assert.equal(second.status, 200);
    assert.equal(third.status, 429);
    assert.equal(third.body.error, "rate_limited");
  } finally {
    s.close();
    process.env.REPORT_RATE_LIMIT_MAX = "1000";
  }
});

test("unknown route -> 404", async () => {
  const { status, body } = await get(base, "/nope");
  assert.equal(status, 404);
  assert.equal(body.error, "not_found");
});
