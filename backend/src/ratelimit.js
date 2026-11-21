"use strict";

// Simple in-memory sliding-window rate limiter, keyed by client IP.
// Good enough for a single Railway instance and the hackathon demo.
// It is NOT shared across instances and NOT persistent.
function rateLimit(options = {}) {
  const windowMs = Number(options.windowMs) || 60_000;
  const max = Number(options.max) || 10;
  const hits = new Map();

  return function rateLimitMiddleware(req, res, next) {
    const key = req.ip || req.socket?.remoteAddress || "unknown";
    const now = Date.now();
    const recent = (hits.get(key) || []).filter((t) => now - t < windowMs);

    if (recent.length >= max) {
      res.set("Retry-After", String(Math.ceil(windowMs / 1000)));
      return res.status(429).json({ error: "rate_limited" });
    }

    recent.push(now);
    hits.set(key, recent);
    next();
  };
}

module.exports = { rateLimit };
