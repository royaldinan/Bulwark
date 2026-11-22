"use strict";

const { domainHash } = require("../hash");

const OPENPHISH_FEED_URL = "https://openphish.com/feed.txt";

function hashesFromText(text) {
  const hashes = new Set();
  for (const line of text.split(/\r?\n/)) {
    const candidate = line.trim();
    if (!candidate || candidate.startsWith("#")) continue;
    try {
      const hostname = new URL(candidate).hostname;
      if (hostname) hashes.add(domainHash(hostname));
    } catch {
      // Ignore malformed feed rows without discarding valid entries.
    }
  }
  return hashes;
}

function createOpenPhishFeed({
  fetchImpl = globalThis.fetch,
  setIntervalImpl = globalThis.setInterval,
  now = () => new Date(),
} = {}) {
  if (typeof fetchImpl !== "function") throw new Error("createOpenPhishFeed requires fetchImpl");

  let hashes = new Set();
  let updatedAt = null;
  let lastError = null;

  async function refresh() {
    try {
      const response = await fetchImpl(OPENPHISH_FEED_URL);
      if (!response.ok) throw new Error(`OpenPhish download failed: HTTP ${response.status}`);
      const nextHashes = hashesFromText(await response.text());
      hashes = nextHashes;
      updatedAt = now().toISOString();
      lastError = null;
    } catch (err) {
      lastError = err instanceof Error ? err.message : String(err);
    }
  }

  function start() {
    const initialRefresh = refresh();
    const timer = setIntervalImpl(() => refresh(), 15 * 60 * 1000);
    if (timer && typeof timer.unref === "function") timer.unref();
    return initialRefresh;
  }

  return {
    refresh,
    start,
    hasHash(hash) {
      return typeof hash === "string" && hashes.has(hash.toLowerCase());
    },
    health() {
      return { source: "openphish", entries: hashes.size, updatedAt, lastError };
    },
  };
}

module.exports = { createOpenPhishFeed, hashesFromText, OPENPHISH_FEED_URL };
