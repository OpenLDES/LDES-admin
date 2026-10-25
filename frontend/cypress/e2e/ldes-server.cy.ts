const SANDBOX_SERVER = '/ldes-servers/3f6d2a8e-1b7c-4e2a-9c5d-0a1b2c3d4e03'

describe('an LDES server', () => {
  beforeEach(() => {
    cy.visit(SANDBOX_SERVER, { onBeforeLoad: (win) => win.localStorage.setItem('ldes-admin.dark', 'false') })
  })

  it('creates an event stream and a view on it, configured in forms that stay in step with the Turtle', () => {
    cy.get('[data-cy=new-stream]').click()
    cy.dialogOpened('create-stream-dialog')
    cy.get('[data-cy=create-stream-dialog]').within(() => {
      const turtle = () => cy.get('[data-cy=definition] .cm-content')
      cy.get('[data-cy=stream-collection]').clear().type('trees')
      turtle().should('contain', 'server:trees a ldes:EventStream')

      // A choice of the usual timestamp paths; the prefix gets declared
      cy.get('[data-cy="config-ldes:timestampPath"] .p-select-dropdown').click()
    })
    cy.contains('.p-select-option', 'Generated at').click()
    cy.get('[data-cy=create-stream-dialog]').within(() => {
      const turtle = () => cy.get('[data-cy=definition] .cm-content')
      turtle().should('contain', 'ldes:timestampPath prov:generatedAtTime').and('contain', '@prefix prov:')

      // A retention policy, validated as the server would
      cy.get('[data-cy=add-retention-version-based]').click()
      turtle().should('contain', 'a ldes:LatestVersionSubset')
      cy.get('[data-cy="config-ldes:amount"] input').clear().type('0')
      cy.get('[data-cy="config-ldes:amount"]').should('contain', 'Enter 1 or more')
      cy.get('[data-cy=create-stream-submit]').should('be.disabled')
      cy.get('[data-cy="config-ldes:amount"] input').clear().type('3')
      turtle().should('contain', 'ldes:amount 3')
      cy.get('[data-cy=create-stream-submit]').should('be.enabled').click()
    })
    cy.contains('[data-cy=streams-table] tbody tr', 'trees').click()

    cy.get('[data-cy=new-view]').click()
    cy.dialogOpened('create-view-dialog')
    cy.get('[data-cy=create-view-dialog]').within(() => {
      const turtle = () => cy.get('[data-cy=definition] .cm-content')
      cy.get('[data-cy=preset-time]').click()
      turtle().should('contain', 'HierarchicalTimeBasedFragmentation').and('contain', 'tree:fragmentationPath prov:generatedAtTime')

      // The Turtle drives the form, which shows what the server would refuse
      turtle().type('{selectall}{backspace}@prefix tree: <https://w3id.org/tree#> . @prefix server: <http://localhost/trees/> . '
        + 'server:by-week tree:viewDescription [ tree:fragmentationStrategy ( [ a tree:HierarchicalTimeBasedFragmentation ; '
        + 'tree:maxGranularity "week" ; tree:fragmentationPath <http://purl.org/dc/terms/created> ] ) ] .', { delay: 0 })
      cy.get('[data-cy=view-name]').should('have.value', 'by-week')
      cy.get('[data-cy="config-tree:maxGranularity"]').should('contain', 'Choose one of year, month, day, hour, minute, second')
      cy.get('[data-cy=create-view-submit]').should('be.disabled')

      // ... and a dropdown offers the granularities there are
      cy.get('[data-cy="config-tree:maxGranularity"] .p-select').click()
    })
    cy.contains('.p-select-option', 'hour').click()
    cy.get('[data-cy=create-view-dialog]').within(() => {
      cy.get('[data-cy=definition] .cm-content').should('contain', 'tree:maxGranularity "hour"')
      cy.get('[data-cy=create-view-submit]').should('be.enabled').click()
    })
    cy.contains('[data-cy=view-card]', 'by-week').should('contain', 'Hierarchical Time Based')
  })

  it('orders the fragmentations of a view', () => {
    cy.contains('[data-cy=streams-table] tbody tr', 'sandbox-parcels').click()
    cy.get('[data-cy=new-view]').click()
    cy.dialogOpened('create-view-dialog')
    cy.get('[data-cy=create-view-dialog]').within(() => {
      cy.get('[data-cy=preset-reference]').click()
      cy.get('[data-cy=add-fragmentation-time-based]').click()
      cy.get('[data-cy=fragmentation-card]').should('have.length', 2).last().find('[aria-label="Move up"]').click()
      cy.get('[data-cy=fragmentation-card]').first().should('contain', 'Time-based')
      cy.get('[data-cy=definition] .cm-content').invoke('text')
        .should('match', /HierarchicalTimeBasedFragmentation.*ReferenceFragmentation/)
    })
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
