// Loaded before every e2e spec.

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /**
       * Waits until a PrimeVue dialog finished opening. When the transition ends the dialog moves
       * the focus to its first focusable element, which would steal the keystrokes of a test.
       */
      dialogOpened(dataCy: string): Chainable<JQuery<HTMLElement>>
    }
  }
}

Cypress.Commands.add('dialogOpened', (dataCy: string) =>
  cy.get(`[data-cy=${dataCy}]`).should('not.have.class', 'p-dialog-enter-active'))

Cypress.on('uncaught:exception', (error) => {
  // ResizeObserver noise from Vue Flow and CodeMirror is harmless
  return !error.message.includes('ResizeObserver')
})

export {}
