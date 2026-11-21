const { expect } = require("chai");
const { ethers } = require("hardhat");
const { anyValue } = require("@nomicfoundation/hardhat-chai-matchers/withArgs");
const { createHash } = require("crypto");

// Mirror the PRD hash rule exactly: SHA-256 of the normalized domain
// (lowercase, no trailing dot) and the first 8 bytes as the lookup prefix.
function normalize(domain) {
  return domain.toLowerCase().replace(/\.$/, "");
}

function sha256Bytes32(input) {
  const digest = createHash("sha256").update(normalize(input), "utf8").digest();
  return "0x" + digest.toString("hex");
}

function prefix8(hashHex) {
  return "0x" + hashHex.slice(2, 2 + 16);
}

const KIND_DOMAIN = 1;
const KIND_APK = 2;

describe("Registry", function () {
  let registry;
  let owner;
  let attacker;

  const domain = "Scam.Example.com.";
  const domainHash = sha256Bytes32(domain);
  const domainPrefix = prefix8(domainHash);

  const apkHash = "0x" + "ab".repeat(32);
  const apkPrefix = prefix8(apkHash);

  beforeEach(async function () {
    [owner, attacker] = await ethers.getSigners();
    const Registry = await ethers.getContractFactory("Registry");
    registry = await Registry.deploy();
    await registry.waitForDeployment();
  });

  it("starts empty and owned by the deployer", async function () {
    expect(await registry.owner()).to.equal(owner.address);
    expect(await registry.totalReports()).to.equal(0n);
    expect(await registry.isFlagged(domainHash)).to.equal(false);
    expect(await registry.isFlaggedPrefix(domainPrefix)).to.equal(false);
  });

  it("normalization is stable across case and trailing dot", async function () {
    expect(sha256Bytes32("Scam.Example.com.")).to.equal(sha256Bytes32("scam.example.com"));
  });

  it("lets the owner report a domain and records the entry", async function () {
    const tx = await registry.report(domainHash, KIND_DOMAIN);
    const receipt = await tx.wait();
    const block = await ethers.provider.getBlock(receipt.blockNumber);

    await expect(tx)
      .to.emit(registry, "Reported")
      .withArgs(domainHash, KIND_DOMAIN, block.timestamp);

    expect(await registry.totalReports()).to.equal(1n);
    expect(await registry.isFlagged(domainHash)).to.equal(true);
    expect(await registry.isFlaggedPrefix(domainPrefix)).to.equal(true);
    expect(await registry.flaggedPrefix(domainPrefix)).to.equal(true);

    const entry = await registry.getEntry(domainHash);
    expect(entry.hash).to.equal(domainHash);
    expect(entry.kind).to.equal(KIND_DOMAIN);
    expect(entry.reporter).to.equal(owner.address);
    expect(entry.reportedAt).to.equal(BigInt(block.timestamp));
  });

  it("looks up by 8-byte prefix only (client never sends the full hash)", async function () {
    await registry.report(domainHash, KIND_DOMAIN);

    // Only the first 8 bytes are needed for a positive verdict.
    expect(await registry.isFlaggedPrefix(prefix8(domainHash))).to.equal(true);

    // A different hash that shares neither hash nor prefix must stay unflagged.
    const otherPrefix = "0x" + "00".repeat(8);
    expect(await registry.isFlaggedPrefix(otherPrefix)).to.equal(false);
  });

  it("reports an APK with kind 2", async function () {
    await registry.report(apkHash, KIND_APK);

    const entry = await registry.getEntry(apkHash);
    expect(entry.kind).to.equal(KIND_APK);
    expect(await registry.isFlaggedPrefix(apkPrefix)).to.equal(true);
    expect(await registry.totalReports()).to.equal(1n);
  });

  it("accumulates totalReports across domains and APKs", async function () {
    await registry.report(domainHash, KIND_DOMAIN);
    await registry.report(apkHash, KIND_APK);
    expect(await registry.totalReports()).to.equal(2n);
  });

  it("rejects reports from anyone but the owner", async function () {
    await expect(
      registry.connect(attacker).report(domainHash, KIND_DOMAIN)
    ).to.be.revertedWithCustomError(registry, "NotOwner");

    expect(await registry.totalReports()).to.equal(0n);
    expect(await registry.isFlagged(domainHash)).to.equal(false);
  });

  it("rejects an invalid kind", async function () {
    await expect(
      registry.report(domainHash, 3)
    ).to.be.revertedWithCustomError(registry, "InvalidKind");

    expect(await registry.totalReports()).to.equal(0n);
  });

  it("rejects a duplicate report of the same hash", async function () {
    await registry.report(domainHash, KIND_DOMAIN);
    await expect(
      registry.report(domainHash, KIND_DOMAIN)
    ).to.be.revertedWithCustomError(registry, "AlreadyReported");

    expect(await registry.totalReports()).to.equal(1n);
  });

  it("is append-only: no function can remove an entry or lower totalReports", async function () {
    await registry.report(domainHash, KIND_DOMAIN);
    const names = registry.interface.fragments
      .filter((f) => f.type === "function")
      .map((f) => f.name.toLowerCase());

    for (const forbidden of ["delete", "remove", "clear", "reset", "unflag"]) {
      expect(names).to.not.include(forbidden);
    }
    expect(await registry.totalReports()).to.equal(1n);
    expect(await registry.isFlagged(domainHash)).to.equal(true);
  });
});
