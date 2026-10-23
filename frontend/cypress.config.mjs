import { defineConfig } from 'cypress'

// ESM JavaScript: Cypress 13 compiles a TypeScript config to CommonJS, which breaks in a
// "type": "module" package
export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:4173',
    specPattern: 'cypress/e2e/**/*.cy.ts',
    viewportWidth: 1440,
    viewportHeight: 900,
    video: false,
  },
})
