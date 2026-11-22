"use strict";

const express = require("express");
const crypto = require("crypto");
const { rateLimit } = require("./ratelimit");
const { isHex8, isHex32 } = require("./validate");
const { prefix8 } = require("./hash");
const { createOpenPhishFeed } = require("./services/openphish");

const KIND_NUMBER = { domain: 1, apk: 2 };
const CHAIN_ID = 80002;

function safeEqual(a, b) {
  const ab = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ab.length !== bb.length) {
    return false;
  }
  return crypto.timingSafeEqual(ab, bb);
}

function tokenGuard(req, res, next) {
  const expected = process.env.API_SHARED_TOKEN;
  if (!expected) {
    return res.status(503).json({ error: "server_token_not_configured" });
  }
  const provided = req.get("x-api-token");
  if (!provided || !safeEqual(provided, expected)) {
    return res.status(401).json({ error: "unauthorized" });
  }
  next();
}

// Registry read -> true / false, or null when the chain is not configured.
async function registryLookup(registry, prefix) {
  try {
    return await registry.isFlaggedPrefix(prefix);
  } catch (err) {
    if (err && err.code === "REGISTRY_NOT_CONFIGURED") {
      return null;
    }
    throw err;
  }
}

function createApp({ registry, safeBrowsing, feed, startFeed = false }) {
  if (!registry) throw new Error("createApp requires a registry");
  if (!safeBrowsing) throw new Error("createApp requires a safeBrowsing");
  if (startFeed) void feed.start();

  const app = express();
  app.disable("x-powered-by");
  app.set("trust proxy", true);
  app.use(express.json({ limit: "16kb" }));

  app.get("/health", (_req, res) => res.json({ ok: true }));

  app.get("/v1/health", (_req, res) => {
    res.json({
      ok: true,
      status: "ok",
      chainId: CHAIN_ID,
      network: "amoy",
      contractAddress: process.env.REGISTRY_ADDRESS || null,
      feed: feed ? feed.health() : {
        source: "openphish",
        entries: 0,
        updatedAt: null,
        lastError: null,
      },
    });
  });

  app.post("/v1/check/domain", async (req, res) => {
    const { prefix, hash } = req.body || {};
    if (!isHex8(prefix)) {
      return res
        .status(400)
        .json({ error: "invalid_prefix", detail: "prefix must be 0x + 16 hex chars (8 bytes)" });
    }
    if (hash !== undefined && !isHex32(hash)) {
      return res
        .status(400)
        .json({ error: "invalid_hash", detail: "hash must be 0x + 64 hex chars (32 bytes)" });
    }

    const normalizedPrefix = prefix.toLowerCase();
    let flagged;
    try {
      flagged = await registryLookup(registry, normalizedPrefix);
    } catch {
      return res.status(502).json({ error: "registry_unavailable" });
    }

    if (flagged === true) {
      return res.json({ verdict: "malicious", source: "registry" });
    }

    if (feed && hash && feed.hasHash(hash.toLowerCase())) {
      return res.json({ verdict: "malicious", source: "feed" });
    }

    let sb = null;
    try {
      sb = await safeBrowsing.lookupDomainPrefix(
        normalizedPrefix,
        hash ? hash.toLowerCase() : undefined
      );
    } catch {
      sb = null; // Safe Browsing errors must never become a "safe" verdict.
    }
    if (sb && sb.verdict && sb.verdict !== "unknown") {
      return res.json({ verdict: sb.verdict, source: sb.source });
    }
    return res.json({ verdict: "unknown", source: "none" });
  });

  app.post("/v1/check/apk", async (req, res) => {
    const { sha256 } = req.body || {};
    if (!isHex32(sha256)) {
      return res
        .status(400)
        .json({ error: "invalid_sha256", detail: "sha256 must be 0x + 64 hex chars (32 bytes)" });
    }

    const hash = sha256.toLowerCase();
    let flagged;
    try {
      flagged = await registryLookup(registry, prefix8(hash));
    } catch {
      return res.status(502).json({ error: "registry_unavailable" });
    }

    // Safe Browsing is URL-based, so APKs are decided by the registry only.
    if (flagged === true) {
      return res.json({ verdict: "malicious", source: "registry" });
    }
    return res.json({ verdict: "unknown", source: "none" });
  });

  const reportGuard = rateLimit({
    max: Number(process.env.REPORT_RATE_LIMIT_MAX) || 10,
    windowMs: Number(process.env.REPORT_RATE_LIMIT_WINDOW_MS) || 60_000,
  });

  app.post("/v1/report", tokenGuard, reportGuard, async (req, res) => {
    const { kind, hash } = req.body || {};
    if (!Object.prototype.hasOwnProperty.call(KIND_NUMBER, kind)) {
      return res
        .status(400)
        .json({ error: "invalid_kind", detail: 'kind must be "domain" or "apk"' });
    }
    if (!isHex32(hash)) {
      return res
        .status(400)
        .json({ error: "invalid_hash", detail: "hash must be 0x + 64 hex chars (32 bytes)" });
    }

    const normalizedHash = hash.toLowerCase();
    try {
      if (await registry.isFlagged(normalizedHash)) {
        return res.status(409).json({ error: "already_listed" });
      }
      const { txHash } = await registry.report(normalizedHash, KIND_NUMBER[kind]);
      return res.json({ txHash, kind, hash: normalizedHash, chainId: CHAIN_ID });
    } catch (err) {
      if (err && err.code === "REGISTRY_NOT_CONFIGURED") {
        return res.status(503).json({ error: "registry_not_configured" });
      }
      return res.status(500).json({ error: "registry_write_failed" });
    }
  });

  app.use((err, _req, res, _next) => {
    if (err && err.type === "entity.parse.failed") {
      return res.status(400).json({ error: "invalid_json" });
    }
    return res.status(500).json({ error: "internal_error" });
  });

  app.use((_req, res) => res.status(404).json({ error: "not_found" }));

  return app;
}

module.exports = { createApp, createOpenPhishFeed, KIND_NUMBER };
