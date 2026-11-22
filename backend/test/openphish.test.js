"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");

const appModule = require("../src/app");
const { createApp } = appModule;
const { domainHash, prefix8 } = require("../src/hash");

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

async function post(base, path, body) {
  const res = await fetch(base + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  return { status: res.status, body: await res.json() };
}

async function get(base, path) {
  const res = await fetch(base + path);
  return { status: res.status, body: await res.json() };
}

test("domain feed verdict requires an exact full hash, never a prefix alone", async () => {
  const fullHash = domainHash("Scam.Example.");
  const prefix = prefix8(fullHash);
  const samePrefixDifferentHash = prefix + "ff".repeat(24);
  const lookups = [];
  const feed = {
    hasHash(hash) {
      lookups.push(hash);
      return hash === fullHash;
    },
  };
  const app = createApp({
    registry: { async isFlaggedPrefix() { return false; } },
    safeBrowsing: fakeSafeBrowsing(),
    feed,
  });
  const { server, base } = await start(app);

  try {
    const hit = await post(base, "/v1/check/domain", { prefix, hash: fullHash });
    assert.equal(hit.status, 200);
    assert.deepEqual(hit.body, { verdict: "malicious", source: "feed" });

    const prefixOnly = await post(base, "/v1/check/domain", { prefix });
    assert.deepEqual(prefixOnly.body, { verdict: "unknown", source: "none" });

    const differentFullHash = await post(base, "/v1/check/domain", {
      prefix,
      hash: samePrefixDifferentHash,
    });
    assert.deepEqual(differentFullHash.body, { verdict: "unknown", source: "none" });
    assert.deepEqual(lookups, [fullHash, samePrefixDifferentHash]);
  } finally {
    server.close();
  }
});

test("OpenPhish text refresh indexes normalized hostname hashes off-chain", async () => {
  const feedUrl = "https://openphish.com/feed.txt";
  const updatedAt = "2026-11-22T00:00:00.000Z";
  const text = [
    "http://Scam.Example.com./payload?q=1,2",
    "https://scam.example.com/other",
    "not a URL",
    "",
  ].join("\n");
  const feed = appModule.createOpenPhishFeed({
    async fetchImpl(url) {
      assert.equal(url, feedUrl);
      return { ok: true, async text() { return text; } };
    },
    now() { return new Date(updatedAt); },
  });

  await feed.refresh();

  assert.equal(feed.hasHash(domainHash("SCAM.EXAMPLE.COM")), true);
  assert.equal(feed.hasHash(domainHash("other.example")), false);
  assert.deepEqual(feed.health(), {
    source: "openphish",
    entries: 1,
    updatedAt,
    lastError: null,
  });
});

test("failed OpenPhish fetch leaves the API registry-only and reports lastError", async () => {
  const feed = appModule.createOpenPhishFeed({
    async fetchImpl() { throw new Error("offline"); },
  });
  await assert.doesNotReject(() => feed.refresh());

  const app = createApp({
    registry: { async isFlaggedPrefix() { return false; } },
    safeBrowsing: fakeSafeBrowsing(),
    feed,
  });
  const { server, base } = await start(app);
  const hash = domainHash("unknown.example");

  try {
    const check = await post(base, "/v1/check/domain", { prefix: prefix8(hash), hash });
    assert.deepEqual(check.body, { verdict: "unknown", source: "none" });

    const health = await get(base, "/v1/health");
    assert.equal(health.status, 200);
    assert.equal(health.body.ok, true);
    assert.equal(health.body.feed.source, "openphish");
    assert.equal(health.body.feed.entries, 0);
    assert.equal(health.body.feed.updatedAt, null);
    assert.match(health.body.feed.lastError, /offline/);
  } finally {
    server.close();
  }
});

test("start refreshes without blocking and schedules an unref 15-minute refresh", async () => {
  let resolveText;
  const pendingText = new Promise((resolve) => { resolveText = resolve; });
  let fetchCount = 0;
  let scheduledRefresh;
  let intervalMs;
  let unrefCalled = false;
  const updatedAt = "2026-11-22T00:00:00.000Z";
  const feed = appModule.createOpenPhishFeed({
    async fetchImpl() {
      fetchCount += 1;
      return { ok: true, text() { return pendingText; } };
    },
    setIntervalImpl(callback, ms) {
      scheduledRefresh = callback;
      intervalMs = ms;
      return { unref() { unrefCalled = true; } };
    },
    now() { return new Date(updatedAt); },
  });

  const initialRefresh = feed.start();
  assert.equal(fetchCount, 1);
  assert.equal(intervalMs, 15 * 60 * 1000);
  assert.equal(unrefCalled, true);
  assert.equal(feed.health().entries, 0);

  resolveText("http://Scam.Example./payload");
  await initialRefresh;
  assert.equal(feed.health().entries, 1);

  await scheduledRefresh();
  assert.equal(fetchCount, 2);
});

test("app startup triggers the feed without awaiting its initial refresh", async () => {
  let startCalled = false;
  let resolveInitialRefresh;
  const initialRefresh = new Promise((resolve) => { resolveInitialRefresh = resolve; });
  const feed = {
    start() {
      startCalled = true;
      return initialRefresh;
    },
    health() {
      return { source: "openphish", entries: 0, updatedAt: null, lastError: null };
    },
  };

  const app = createApp({
    registry: { async isFlaggedPrefix() { return false; } },
    safeBrowsing: fakeSafeBrowsing(),
    feed,
    startFeed: true,
  });
  assert.equal(startCalled, true);

  const { server, base } = await start(app);
  try {
    const health = await get(base, "/v1/health");
    assert.equal(health.status, 200);
  } finally {
    server.close();
    resolveInitialRefresh();
    await initialRefresh;
  }
});

test("GET /v1/health exposes OpenPhish source, entries, updatedAt, and lastError", async () => {
  const feedHealth = {
    source: "openphish",
    entries: 2,
    updatedAt: "2026-11-22T00:00:00.000Z",
    lastError: "previous download failed",
  };
  const app = createApp({
    registry: { async isFlaggedPrefix() { return false; } },
    safeBrowsing: fakeSafeBrowsing(),
    feed: { health() { return feedHealth; } },
  });
  const { server, base } = await start(app);

  try {
    const { status, body } = await get(base, "/v1/health");
    assert.equal(status, 200);
    assert.equal(body.ok, true);
    assert.deepEqual(body.feed, feedHealth);
  } finally {
    server.close();
  }
});
