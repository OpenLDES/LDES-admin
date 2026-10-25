const SANDBOX_SERVER = '/ldes-servers/3f6d2a8e-1b7c-4e2a-9c5d-0a1b2c3d4e03'

describe('the demos', () => {
  const visit = (path: string) =>
    cy.visit(path, { onBeforeLoad: (win) => win.localStorage.setItem('ldes-admin.dark', 'false') })

  it('come with a new local LDES server, fed by a pipeline on the sandbox LDIO', () => {
    visit('/ldes-servers')
    cy.get('[data-cy=start-local]').click()
    cy.dialogOpened('start-local-dialog')
    cy.get('[data-cy=start-local-dialog]').within(() => {
      // Nothing unless asked for
      cy.get('[data-cy=example-sensors] input').should('not.be.checked')
      cy.get('[data-cy=example-parking] input').should('not.be.checked')
      cy.contains('Create sensor demo stream, views, data, and pipeline')
      cy.contains('Create parking demo stream, views, and pipeline')

      cy.get('[data-cy=example-sensors]').click()
      cy.get('[data-cy=example-partner]').should('contain', 'Sandbox LDIO')
      cy.get('[data-cy=start-local-submit]').click()
      cy.get('[data-cy=example-step]').should('have.length', 4)
    })
    cy.get('h1', { timeout: 15000 }).should('have.text', 'Sandbox 2')
    cy.contains('[data-cy=streams-table] tbody tr', 'sensor-observations')
      .should('contain', 'by-page').and('contain', 'by-time').and('contain', '36')
  })

  it('come with a new local LDIO, ingesting in the sandbox LDES server', () => {
    visit('/ldios')
    cy.get('[data-cy=start-local]').click()
    cy.dialogOpened('start-local-dialog')
    cy.get('[data-cy=start-local-dialog]').within(() => {
      cy.get('[data-cy=example-parking]').click()
      cy.get('[data-cy=example-partner]').should('contain', 'Sandbox LDES server')
      cy.get('[data-cy=start-local-submit]').click()
    })
    cy.get('h1', { timeout: 15000 }).should('have.text', 'Sandbox LDIO 2')
    cy.contains('[data-cy=pipelines-table] tr', 'parking-occupancy-ingest').should('contain', 'HTTP In Poller')

    // The demo data lives in the browser: no reload
    cy.get('[data-cy=sidebar]').contains('LDES servers').click()
    cy.contains('[data-cy=instance-card]', 'Sandbox LDES server').click()
    cy.contains('[data-cy=streams-table] tbody tr', 'parking-occupancy').should('contain', 'by-time')
  })

  it('fill in a new event stream, and create its views and sample data too', () => {
    visit(SANDBOX_SERVER)
    cy.get('[data-cy=new-stream]').click()
    cy.dialogOpened('create-stream-dialog')
    cy.get('[data-cy=create-stream-dialog]').within(() => {
      cy.get('[data-cy=example-extras]').should('not.exist')
      cy.get('[data-cy=stream-template-sensors]').click()
      cy.get('[data-cy=stream-collection]').should('have.value', 'sensor-observations')
      cy.get('[data-cy=definition] .cm-content').should('contain', 'sh:targetClass sosa:Observation')

      cy.get('[data-cy=example-extras]').within(() => {
        cy.get('[data-cy=extra-views] input').should('not.be.checked')
        cy.get('[data-cy=extra-views]').click()
        cy.get('[data-cy=extra-data]').click()
        cy.contains('ingested directly')
      })
      cy.get('[data-cy=stream-collection]').clear().type('air')
      cy.get('[data-cy=create-stream-submit]').click()
    })
    cy.contains('[data-cy=streams-table] tbody tr', 'air', { timeout: 10000 })
      .should('contain', 'by-page').and('contain', '36').click()

    // Ingesting starts from an observation, in the format of choice
    cy.contains('button', 'Ingest member').click()
    cy.get('[data-cy=ingest-panel]').should('contain', 'sensor observations example')
    cy.get('[data-cy=ingest-panel] .cm-content').should('contain', 'a sosa:Observation')
    cy.get('[data-cy=ingest-format]').click()
    cy.contains('.p-select-option', 'JSON-LD').click()
    cy.get('[data-cy=ingest-panel] .cm-content').should('contain', '"@type": "sosa:Observation"')
    cy.get('[data-cy=ingest-submit]').click()
    cy.contains('Member ingested in air')
  })

  it('leave the parking example without sample data', () => {
    visit(SANDBOX_SERVER)
    cy.get('[data-cy=new-stream]').click()
    cy.dialogOpened('create-stream-dialog')
    cy.get('[data-cy=stream-template-parking]').click()
    cy.get('[data-cy=example-extras]').should('contain', 'parking-occupancy-ingest')
      .find('[data-cy=extra-data]').should('not.exist')
  })
})
