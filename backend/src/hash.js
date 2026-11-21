"use strict";

const crypto = require("crypto");

// Hash rules are fixed by the PRD and MUST match the Android client and the contract:
//   - domain is normalized first: lowercase, no trailing dot
//   - hash is SHA-256 (NOT keccak256)
//   - the lookup key is the first 8 bytes (a bytes8 prefix)
function normalizeDomain(domain) {
  if (typeof domain !== "string") {
    throw new TypeError("domain must be a string");
  }
  return domain.trim().toLowerCase().replace(/\.$/, "");
}

function sha256Utf8(input) {
  return "0x" + crypto.createHash("sha256").update(input, "utf8").digest("hex");
}

function sha256Bytes(buf) {
  return "0x" + crypto.createHash("sha256").update(buf).digest("hex");
}

function domainHash(domain) {
  return sha256Utf8(normalizeDomain(domain));
}

// First 8 bytes of a 32-byte hex hash -> 0x + 16 hex chars.
function prefix8(hashHex) {
  return "0x" + hashHex.replace(/^0x/, "").slice(0, 16);
}

module.exports = { normalizeDomain, sha256Utf8, sha256Bytes, domainHash, prefix8 };
