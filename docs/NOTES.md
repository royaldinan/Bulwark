# NOTES.md — Bulwark

Append-only working notes. Newest at the bottom. Conflicts are expected; keep both sides.

## 2026-11-21 — Task 3: registry contract + mediation API (backend/, contracts/)

- Built `contracts/Registry.sol`: append-only, `report(bytes32,uint8)` is `onlyOwner`,
  `flaggedPrefix[bytes8]` for prefix lookups, `isFlagged`/`isFlaggedPrefix` reads,
  `Reported(bytes32 indexed,uint8,uint256)` event, `totalReports` counter. No delete path.
- Hash scheme follows PRD "Keputusan teknis" #4/#5: SHA-256 (not keccak256) of the
  normalized domain (lowercase, no trailing dot); 8-byte prefix is the lookup key.
- Backend is Express on `process.env.PORT`; `GET /health` -> `{"ok":true}`,
  `GET /v1/health` returns chain metadata, plus `/v1/check/domain`, `/v1/check/apk`,
  `/v1/report` (shared-token header + in-memory rate limit).
- Safe Browsing is a documented TODO (`backend/src/services/safebrowsing.js`): official
  docs show Lookup v4 requires full URLs and Update v4 needs Google's own hash scheme plus
  clientStates, so neither fits a bare 8-byte prefix. It returns `unknown`, never `safe`.
- Verified: `npx hardhat test` 10 passing; `npm test` (backend) 23 passing; live boot smoke test.

### Alternatives considered
- Store only the 8-byte prefix on-chain instead of the full 32-byte hash: rejected — we
  want the full hash for public provenance and collision-safe entries; the prefix stays a
  fast lookup index.
- `keccak256` for hashing: rejected by PRD #4 (SHA-256 is available for free on Android).
- Redis-backed rate limit: rejected for now — single instance for the demo; in-memory is enough.

## Contract

API contract agreed between `android/` and `backend/`/`contracts/`. Keep this in sync with
`backend/src/app.js` and `contracts/contracts/Registry.sol`.

```json
{
  "name": "Bulwark Mediation API",
  "version": "1.0.0",
  "baseUrl": "Railway host; Android reads it from a config constant (never hardcoded in code)",
  "chain": {
    "network": "polygon-amoy",
    "chainId": 80002,
    "explorer": "https://amoy.polygonscan.com",
    "contractAddress": "process.env.REGISTRY_ADDRESS (filled in after deploy)"
  },
  "hashScheme": {
    "algorithm": "SHA-256",
    "domainNormalization": "trim, lowercase, strip a single trailing dot (client does this before hashing)",
    "lookupPrefixBytes": 8,
    "prefixEncoding": "0x + 16 lowercase hex chars",
    "fullHashEncoding": "0x + 64 lowercase hex chars",
    "note": "The API validates encoding only; it cannot re-normalize a hash it receives."
  },
  "verdicts": ["malicious", "safe", "unknown"],
  "sources": ["registry", "safebrowsing", "none"],
  "auth": {
    "reportHeader": "x-api-token",
    "envVar": "API_SHARED_TOKEN",
    "appliesTo": ["POST /v1/report"]
  },
  "rateLimit": {
    "appliesTo": ["POST /v1/report"],
    "scope": "per client IP, in-memory, single instance",
    "defaultMax": 10,
    "defaultWindowMs": 60000,
    "envVars": ["REPORT_RATE_LIMIT_MAX", "REPORT_RATE_LIMIT_WINDOW_MS"],
    "overLimitResponse": { "status": 429, "body": { "error": "rate_limited" } }
  },
  "endpoints": [
    {
      "method": "GET",
      "path": "/health",
      "auth": false,
      "response": { "200": { "ok": true } }
    },
    {
      "method": "GET",
      "path": "/v1/health",
      "auth": false,
      "response": {
        "200": {
          "ok": true,
          "status": "ok",
          "chainId": 80002,
          "network": "amoy",
          "contractAddress": "0x... | null"
        }
      }
    },
    {
      "method": "POST",
      "path": "/v1/check/domain",
      "auth": false,
      "request": {
        "contentType": "application/json",
        "body": {
          "prefix": "0x + 16 hex chars (required) — 8-byte SHA-256 prefix of the normalized domain",
          "hash": "0x + 64 hex chars (optional) — full SHA-256, when the client has it"
        }
      },
      "response": {
        "200": { "verdict": "malicious|safe|unknown", "source": "registry|safebrowsing|none" }
      },
      "errors": {
        "400": ["invalid_prefix", "invalid_hash"],
        "502": ["registry_unavailable"]
      },
      "note": "On-chain match is authoritative (source=registry). Safe Browsing is a TODO, so unknown/none is returned when the registry has no match."
    },
    {
      "method": "POST",
      "path": "/v1/check/apk",
      "auth": false,
      "request": {
        "contentType": "application/json",
        "body": { "sha256": "0x + 64 hex chars (required) — SHA-256 of the APK file" }
      },
      "response": {
        "200": { "verdict": "malicious|safe|unknown", "source": "registry|safebrowsing|none" }
      },
      "errors": {
        "400": ["invalid_sha256"],
        "502": ["registry_unavailable"]
      },
      "note": "APKs are decided by the registry only (Safe Browsing is URL-based). No match => unknown, never safe."
    },
    {
      "method": "POST",
      "path": "/v1/report",
      "auth": true,
      "headers": { "x-api-token": "API_SHARED_TOKEN" },
      "request": {
        "contentType": "application/json",
        "body": {
          "kind": "\"domain\" | \"apk\"",
          "hash": "0x + 64 hex chars — full SHA-256 (domain hash or APK sha256)"
        }
      },
      "response": {
        "200": { "txHash": "0x...", "kind": "domain|apk", "hash": "0x...", "chainId": 80002 }
      },
      "errors": {
        "400": ["invalid_kind", "invalid_hash", "invalid_json"],
        "401": ["unauthorized"],
        "429": ["rate_limited"],
        "502": ["report_failed"],
        "503": ["server_token_not_configured", "registry_not_configured"]
      },
      "note": "Writes on-chain with the backend wallet (contract owner). txHash is the real transaction hash for the Lapor screen."
    }
  ],
  "contract": {
    "name": "Registry",
    "file": "contracts/contracts/Registry.sol",
    "solidity": "0.8.24",
    "chainId": 80002,
    "kindMap": { "domain": 1, "apk": 2 },
    "functions": {
      "report": { "sig": "report(bytes32 hash, uint8 kind)", "access": "onlyOwner", "mutability": "nonpayable" },
      "isFlagged": { "sig": "isFlagged(bytes32 hash) view returns (bool)" },
      "isFlaggedPrefix": { "sig": "isFlaggedPrefix(bytes8 prefix) view returns (bool)" },
      "getEntry": { "sig": "getEntry(bytes32 hash) view returns (Entry)" },
      "totalReports": { "sig": "totalReports() view returns (uint256)" },
      "owner": { "sig": "owner() view returns (address)" }
    },
    "entry": {
      "hash": "bytes32",
      "kind": "uint8 (1 = domain, 2 = apk)",
      "reportedAt": "uint256 (block timestamp)",
      "reporter": "address (backend wallet)"
    },
    "storage": {
      "entries": "mapping(bytes32 => Entry)",
      "flaggedPrefix": "mapping(bytes8 => bool)",
      "totalReports": "uint256 (append-only counter)"
    },
    "event": { "sig": "Reported(bytes32 indexed hash, uint8 kind, uint256 at)" },
    "errors": ["NotOwner()", "InvalidKind(uint8)", "AlreadyReported(bytes32)"],
    "invariants": [
      "append-only: no delete/remove/clear/unflag function exists",
      "totalReports only increases",
      "only the contract owner can call report"
    ]
  }
}
```
