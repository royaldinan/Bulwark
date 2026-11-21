require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

// Amoy is Polygon's testnet, chain ID 80002.
// PRIVATE_KEY and AMOY_RPC_URL are read from the environment only; never hardcoded.
const AMOY_RPC_URL = process.env.AMOY_RPC_URL || "";
const PRIVATE_KEY = process.env.PRIVATE_KEY || "";

/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: { enabled: true, runs: 200 },
    },
  },
  networks: {
    hardhat: {
      chainId: 31337,
    },
    amoy: {
      url: AMOY_RPC_URL,
      chainId: 80002,
      accounts: PRIVATE_KEY ? [PRIVATE_KEY] : [],
    },
  },
};
