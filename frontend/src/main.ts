import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import ConfirmationService from 'primevue/confirmationservice'
import ToastService from 'primevue/toastservice'
import Tooltip from 'primevue/tooltip'
import 'primeicons/primeicons.css'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import './assets/main.css'
import App from './App.vue'
import router from './router'
import { initialMode, useModeStore } from './stores/mode'
import { LdesAdminPreset } from './theme'

const pinia = createPinia()
const app = createApp(App)
  .use(pinia)
  .use(router)
  .use(PrimeVue, {
    ripple: true,
    theme: {
      preset: LdesAdminPreset,
      options: { darkModeSelector: '.app-dark' },
    },
  })
  .use(ToastService)
  .use(ConfirmationService)
  .directive('tooltip', Tooltip)

// The views call the API as soon as they mount, so decide on demo or production mode first
initialMode().then(({ mode, backendUp }) => {
  useModeStore(pinia).start(mode, backendUp)
  app.mount('#app')
})
