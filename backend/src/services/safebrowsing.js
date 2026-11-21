"use strict";

// Google Safe Browsing integration is intentionally NOT implemented yet.
//
// Official docs were read before writing this (Safe Browsing v4, both APIs):
//
//   Lookup API v4 — POST https://safebrowsing.googleapis.com/v4/threatMatches:find
//     The docs state this endpoint must send full URLs ("always use the URL field,
//     never the hash field"). Bulwark only sends an 8-byte SHA-256 prefix, so using
//     Lookup means sending the full domain — which breaks the PRD privacy rule.
//
//   Update API v4 — POST https://safebrowsing.googleapis.com/v4/fullHashes:find
//     This one accepts hash prefixes, but:
//       * the prefix must be Google's own scheme: a 4-byte prefix of SHA-256 over a
//         canonicalized URL plus a Google-specific suffix — NOT our
//         SHA-256(normalized domain);
//       * it requires clientStates produced by a prior threatListUpdates:fetch, i.e.
//         the caller is expected to keep a local hash database.
//     A bare 8-byte prefix of our domain hash is therefore not a valid lookup key.
//
// PRD "Keputusan teknis" #8: read the official docs, don't invent an endpoint; if the
// approach is unclear, mark a TODO and continue. So this returns "unknown" and never
// claims a domain is safe.
//
// TODO(V1): choose one and implement behind SAFE_BROWSING_KEY:
//   (a) send the full domain to Lookup API v4, accepting and documenting the privacy
//       tradeoff (only possible for domain checks, never for APK hashes); or
//   (b) implement a real Update API client (local DB + clientStates + URL hashing).
async function lookupDomainPrefix(_prefix, _hash) {
  return { verdict: "unknown", source: "none" };
}

module.exports = { lookupDomainPrefix };
