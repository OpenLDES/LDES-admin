const SANDBOX_SERVER = '/ldes-servers/3f6d2a8e-1b7c-4e2a-9c5d-0a1b2c3d4e03'

describe('an LDES server', () => {
  beforeEach(() => {
    cy.visit(SANDBOX_SERVER, { onBeforeLoad: (win) => win.localStorage.setItem('ldes-admin.dark', 'false') })
  })

  it('creates an event stream and a view on it', () => {
    cy.get('[data-cy=new-stream]').click()
    cy.dialogOpened('create-stream-dialog')
    cy.get('[data-cy=create-stream-dialog]').within(() => {
      cy.get('[data-cy=stream-collection]').clear().type('trees')
      cy.get('.cm-content').should('contain', 'server:trees a ldes:EventStream')
      cy.get('[data-cy=create-stream-submit]').click()
    })
    cy.contains('[data-cy=streams-table] tbody tr', 'trees').click()

    cy.get('[data-cy=new-view]').click()
    cy.dialogOpened('create-view-dialog')
    cy.get('[data-cy=create-view-dialog]').within(() => {
      cy.contains('button', 'Time-based').click()
      cy.get('.cm-content').should('contain', 'HierarchicalTimeBasedFragmentation')
      cy.get('[data-cy=create-view-submit]').click()
    })
    cy.contains('[data-cy=view-card]', 'by-time').should('contain', 'Hierarchical Time Based')
  })

  it('ingests a member', () => {
    cy.get('[data-cy=tab-ingest]').click()
    cy.get('[data-cy=ingest-panel] .cm-content').should('contain', 'perceel:Perceel')
    cy.get('[data-cy=ingest-submit]').click()
    cy.contains('.p-toast', 'Member ingested in sandbox-parcels')
    cy.contains('Ingested in this session').parent().parent().should('contain', 'sandbox-parcels')
  })

  it('reports a member that is not valid RDF', () => {
    cy.get('[data-cy=tab-ingest]').click()
    cy.get('[data-cy=ingest-panel] .cm-content').type('{selectall}{backspace}this is not turtle', { delay: 0 })
    cy.get('[data-cy=ingest-submit]').click()
    cy.contains('.p-toast', 'Invalid RDF')
  })

  it('shows the background processes', () => {
    cy.get('[data-cy=tab-processes]').click()
    cy.get('[data-cy=process]').should('have.length', 6)
    cy.contains('[data-cy=process]', 'Pagination').should('contain', 'Completed runs')
    cy.contains('[data-cy=process]', 'View retention')
  })

  it('publishes a catalog of the server', () => {
    cy.get('[data-cy=internal-url]').should('contain', 'http://sandbox-ldes-server:8080')
    cy.get('[data-cy=tab-dcat]').click()
    cy.contains('This server has no catalog yet')
    cy.contains('Nothing published yet')

    cy.get('[data-cy=server-catalog]').within(() => {
      cy.get('[data-cy=save-document]').should('be.disabled')
      cy.get('.cm-content').type('{selectall}{backspace}@prefix dcat: <http://www.w3.org/ns/dcat#> . '
        + '@prefix dcterms: <http://purl.org/dc/terms/> . [] a dcat:Catalog ; dcterms:title "Experiments"@en .', { delay: 0 })
      cy.get('[data-cy=save-document]').click()
    })

    cy.contains('.p-toast', 'Server catalog saved')
    cy.get('[data-cy=published-catalog]')
      .should('contain', 'sandbox-ldes-server:8080> a dcat:Catalog')
      .and('contain', '"Experiments"@en')
  })
})
