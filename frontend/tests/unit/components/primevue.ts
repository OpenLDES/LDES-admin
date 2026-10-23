import PrimeVue from 'primevue/config'
import Tooltip from 'primevue/tooltip'

/** Mount options for components that use PrimeVue */
export const withPrimeVue = {
  global: {
    plugins: [[PrimeVue, { theme: 'none' }]] as never[],
    directives: { tooltip: Tooltip },
  },
}
