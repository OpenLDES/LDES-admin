describe('managing LDES servers and LDIOs', () => {
  beforeEach(() => {
    cy.visit('/ldes-servers', { onBeforeLoad: (win) => win.localStorage.setItem('ldes-admin.dark', 'false') })
  })

  it('adds an existing LDES server', () => {
    cy.get('[data-cy=add-instance]').click()
    cy.dialogOpened('register-dialog')
    cy.get('[data-cy=register-dialog]').within(() => {
      cy.get('[data-cy=register-submit]').should('be.disabled')
      cy.get('[data-cy=instance-name]').type('Acceptance')
      cy.get('[data-cy=instance-url]').type('https://acceptance.ldes.example.org')
      cy.get('[data-cy=register-submit]').click()
    })
    cy.get('h1').should('have.text', 'Acceptance')
    cy.contains('No event streams yet')
    cy.get('[data-cy=sidebar]').contains('LDES servers').parent().should('contain', '5')
  })

  it('refuses to add the same server twice', () => {
    cy.get('[data-cy=add-instance]').click()
    cy.dialogOpened('register-dialog')
    cy.get('[data-cy=instance-name]').type('Production again')
    cy.get('[data-cy=instance-url]').type('https://ldes.vlaanderen.example')
    cy.get('[data-cy=register-submit]').click()
    cy.get('[data-cy=register-dialog]').should('contain', 'is already registered')
  })

  it('starts a local LDIO and stops it again', () => {
    cy.visit('/ldios')
    cy.get('[data-cy=start-local]').click()
    cy.dialogOpened('start-local-dialog')
    cy.get('[data-cy=start-local-dialog]').within(() => {
      cy.get('[data-cy=start-local-submit]').click()
      cy.contains('Pulling the LDIO image')
    })
    cy.get('h1', { timeout: 8000 }).should('have.text', 'Sandbox LDIO 2')
    cy.contains('Local container')

    cy.contains('button', 'Stop').click()
    cy.contains('.p-confirmdialog button', 'Stop and remove').click()
    cy.location('pathname').should('eq', '/ldios')
    cy.get('[data-cy=instance-card]').should('have.length', 3)
  })

  it('halts and resumes a pipeline', () => {
    cy.visit('/ldios')
    cy.contains('[data-cy=instance-card]', 'Ingest LDIO').click()
    cy.contains('[data-cy=pipelines-table] tr', 'parcels-ingest').as('row')
    cy.get('@row').find('[data-cy=pipeline-status]').should('contain', 'Running')

    cy.get('@row').find('[data-cy=halt]').click()
    cy.contains('[data-cy=pipelines-table] tr', 'parcels-ingest').find('[data-cy=pipeline-status]')
      .should('contain', 'Halted')

    cy.contains('[data-cy=pipelines-table] tr', 'parcels-ingest').find('[data-cy=resume]').click()
    cy.contains('[data-cy=pipelines-table] tr', 'parcels-ingest').find('[data-cy=pipeline-status]')
      .should('contain', 'Running')
  })

  it('shows the components installed on an LDIO', () => {
    cy.visit('/ldios')
    cy.contains('[data-cy=instance-card]', 'Consumer LDIO').click()
    cy.get('[data-cy=tab-catalog]').click()
    cy.contains('Version Object Creator')
    cy.contains('Ldio:RepositorySink')
  })
})
