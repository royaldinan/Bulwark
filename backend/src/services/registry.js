"use strict";

// Reads and writes the Bulwark Registry contract on Polygon Amoy (chain ID 80002).
// Configuration comes from environment variables only:
//   REGISTRY_ADDRESS  deployed contract address
//   AMOY_RPC_URL      JSON-RPC endpoint
//   PRIVATE_KEY       backend wallet key (only needed for report(); never logged)
//
// ethers is required lazily so the rest of the API can start and be tested
// without a configured chain.

const ABI = [
  "function isFlaggedPrefix(bytes8 prefix) view returns (bool)",
  "function isFlagged(bytes32 hash) view returns (bool)",
  "function totalReports() view returns (uint256)",
  "function report(bytes32 hash, uint8 kind)",
  "event Reported(bytes32 indexed hash, uint8 kind, uint256 at)",
];

class RegistryNotConfiguredError extends Error {
  constructor() {
    super("REGISTRY_ADDRESS and AMOY_RPC_URL must be set to reach the registry");
    this.name = "RegistryNotConfiguredError";
    this.code = "REGISTRY_NOT_CONFIGURED";
  }
}

let cached = null;
let cachedKey = null;

function getContract() {
  const address = process.env.REGISTRY_ADDRESS;
  const rpc = process.env.AMOY_RPC_URL;
  const privateKey = process.env.PRIVATE_KEY;

  if (!address || !rpc) {
    throw new RegistryNotConfiguredError();
  }

  const key = `${address}|${rpc}|${privateKey ? "signer" : "reader"}`;
  if (cached && cachedKey === key) {
    return cached;
  }

  const { ethers } = require("ethers");
  const provider = new ethers.JsonRpcProvider(rpc);
  const runner = privateKey ? new ethers.Wallet(privateKey, provider) : provider;
  cached = new ethers.Contract(address, ABI, runner);
  cachedKey = key;
  return cached;
}

async function isFlaggedPrefix(prefix) {
  return Boolean(await getContract().isFlaggedPrefix(prefix));
}

async function isFlagged(hash) {
  return Boolean(await getContract().isFlagged(hash));
}

async function totalReports() {
  return await getContract().totalReports();
}

// Appends an entry; only the backend wallet (contract owner) can do this.
// Returns the real transaction hash for the caller to show as proof.
async function report(hash, kind) {
  const contract = getContract();
  const tx = await contract.report(hash, kind);
  const receipt = await tx.wait();
  return { txHash: receipt?.hash || tx.hash };
}

module.exports = {
  isFlaggedPrefix,
  isFlagged,
  totalReports,
  report,
  RegistryNotConfiguredError,
};
