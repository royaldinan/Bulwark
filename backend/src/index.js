"use strict";

require("dotenv").config();

const { createApp, createOpenPhishFeed } = require("./app");
const registry = require("./services/registry");
const safeBrowsing = require("./services/safebrowsing");

const app = createApp({
  registry,
  safeBrowsing,
  feed: createOpenPhishFeed(),
  startFeed: true,
});
const port = Number(process.env.PORT) || 3000;

app.listen(port, () => {
  console.log(`Bulwark mediation API listening on port ${port}`);
  console.log(`chain=amoy/80002 registry=${process.env.REGISTRY_ADDRESS || "(unset)"}`);
});
