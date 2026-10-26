/**
 * Unit tests run in Jest (jsdom). TypeScript goes through ts-jest, single file components
 * through vue3-jest, and the ESM-only packages through Babel.
 */
module.exports = {
  testEnvironment: 'jsdom',
  testEnvironmentOptions: {
    customExportConditions: ['node', 'node-addons'],
  },
  roots: ['<rootDir>/tests/unit'],
  setupFiles: ['<rootDir>/tests/unit/setup.cjs'],
  // What Vite defines at build time (vite.config.ts)
  globals: { __DEMO_ONLY__: false },
  moduleFileExtensions: ['ts', 'js', 'mjs', 'json', 'vue'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '\\.css$': '<rootDir>/tests/unit/styleStub.cjs',
  },
  transform: {
    '^.+\\.ts$': ['ts-jest', { tsconfig: '<rootDir>/tsconfig.jest.json' }],
    '^.+\\.vue$': ['@vue/vue3-jest', { tsConfig: '<rootDir>/tsconfig.jest.json' }],
    '^.+\\.m?js$': 'babel-jest',
  },
  // Transform PrimeVue and nostics (which vue-router 5 requires from its CommonJS build), both in
  // the pnpm store (.pnpm/@primevue+core@x) and when linked
  transformIgnorePatterns: [
    '/node_modules/(?!\\.pnpm/(primevue|@primevue\\+|@primeuix\\+|nostics@))(?!primevue|@primevue|@primeuix|nostics)',
  ],
}
