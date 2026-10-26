/// <reference types="vite/client" />

/** Whether this build only runs in demo mode (see vite.config.ts) */
declare const __DEMO_ONLY__: boolean

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
