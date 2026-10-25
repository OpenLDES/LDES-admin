describe('navigation', () => {
  beforeEach(() => {
    cy.visit('/', { onBeforeLoad: (win) => win.localStorage.setItem('ldes-admin.dark', 'false') })
  })

  it('shows the health of every instance on the dashboard', () => {
    cy.get('[data-cy=stat-tile]').should('have.length', 5)
    cy.contains('[data-cy=stat-tile]', 'LDES servers up').should('contain', '3 of 4')
    cy.get('[data-cy=dashboard-server]').should('have.length', 4)
    cy.contains('[data-cy=dashboard-server]', 'Legacy 3.x').should('contain', 'Down')
  })

  it('opens the LDES servers or LDIOs behind a headline number', () => {
    const opens = (tile: string, path: string) => {
      cy.contains('[data-cy=stat-tile]', tile).click()
      cy.location('pathname').should('eq', path)
      cy.go('back')
      cy.location('pathname').should('eq', '/')
    }
    opens('LDES servers up', '/ldes-servers')
    opens('LDIOs up', '/ldios')
    opens('Pipelines running', '/ldios')
    opens('Members ingested', '/ldes-servers')
    cy.contains('[data-cy=stat-tile]', 'Needs attention').should('match', 'div')
  })

  it('shows the throughput chart also as a table', () => {
    cy.get('[data-cy=throughput-mode] .pi-table').click()
    cy.get('.throughput table').should('contain', 'Production').and('contain', 'Staging')
  })

  it('drills down from the dashboard to the members of a view', () => {
    cy.contains('[data-cy=dashboard-server]', 'Production').click()
    cy.location('pathname').should('match', /\/ldes-servers\/.+/)
    cy.get('[data-cy=streams-table] tbody tr').should('have.length', 4)

    cy.contains('[data-cy=streams-table] tbody tr', 'parcels').click()
    cy.get('h1').should('have.text', 'parcels')
    cy.get('[data-cy=view-card]').should('have.length', 3)

    cy.contains('[data-cy=view-card]', 'by-time').find('[data-cy=browse-view-button]').click()
    cy.get('[data-cy=fragment-browser]').should('contain', 'View root')
    cy.get('[data-cy=relation]').should('have.length', 3).first().click()
    cy.get('[data-cy=fragment-browser]').should('contain', '5 members')
    cy.contains('[data-cy=fragment-browser] .p-accordionheader', 'perceel').click()
    cy.get('[data-cy=fragment-browser] .cm-content').should('contain', 'perceel:Perceel')
  })

  it('switches between light and dark mode', () => {
    cy.get('html').should('not.have.class', 'app-dark')
    cy.get('[data-cy=toggle-dark]').click()
    cy.get('html').should('have.class', 'app-dark')
    cy.reload()
    cy.get('html').should('have.class', 'app-dark')
  })

  it('shows a friendly page for unknown routes', () => {
    cy.visit('/nowhere')
    cy.contains('Page not found')
  })
})
