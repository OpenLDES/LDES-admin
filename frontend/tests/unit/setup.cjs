// jsdom (Jest 29) lacks crypto.randomUUID, which browsers have
const { randomUUID } = require('node:crypto')

if (typeof globalThis.crypto.randomUUID !== 'function') {
  Object.defineProperty(globalThis.crypto, 'randomUUID', { value: randomUUID })
}
