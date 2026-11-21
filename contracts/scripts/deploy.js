// Deploy Registry.sol to Polygon Amoy (chain ID 80002).
//
// Required environment variables (set them yourself, never commit them):
//   PRIVATE_KEY     backend wallet private key (funded with testnet MATIC)
//   AMOY_RPC_URL    JSON-RPC endpoint for Polygon Amoy
//
// Run:  npx hardhat run scripts/deploy.js --network amoy
//
// The private key is consumed by Hardhat from the network config in
// hardhat.config.js; this script never reads or prints it.
const hre = require("hardhat");

async function main() {
  if (!process.env.AMOY_RPC_URL) {
    throw new Error("AMOY_RPC_URL is not set");
  }
  if (!process.env.PRIVATE_KEY) {
    throw new Error("PRIVATE_KEY is not set");
  }

  const network = await hre.ethers.provider.getNetwork();
  if (Number(network.chainId) !== 80002) {
    throw new Error(`Expected chain ID 80002 (Polygon Amoy), got ${network.chainId}`);
  }

  const [deployer] = await hre.ethers.getSigners();
  console.log("Deploying Registry with account:", deployer.address);

  const Registry = await hre.ethers.getContractFactory("Registry");
  const registry = await Registry.deploy();
  await registry.waitForDeployment();

  const address = await registry.getAddress();
  console.log("Registry deployed to:", address);
  console.log("Chain ID:", network.chainId.toString());
  console.log("Explorer:", `https://amoy.polygonscan.com/address/${address}`);
  console.log("\nSet REGISTRY_ADDRESS in the backend environment to this address.");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
