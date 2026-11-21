"use strict";

const HEX8 = /^0x[0-9a-f]{16}$/i;
const HEX32 = /^0x[0-9a-f]{64}$/i;

function isHex8(value) {
  return typeof value === "string" && HEX8.test(value);
}

function isHex32(value) {
  return typeof value === "string" && HEX32.test(value);
}

module.exports = { isHex8, isHex32 };
