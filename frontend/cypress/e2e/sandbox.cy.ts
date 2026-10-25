describe('the sandbox', () => {
  beforeEach(() => {
    cy.visit('/sandbox', { onBeforeLoad: (win) => win.localStorage.setItem('ldes-admin.dark', 'false') })
    cy.get('[data-cy=canvas] .vue-flow__node').should('have.length', 6)
  })

  it('starts with a valid example pipeline', () => {
    cy.get('[data-cy=pipeline-validity]').should('contain', 'Valid')
    cy.get('[data-cy=pipeline-yaml] .cm-content')
      .should('contain', 'name: parcels-to-ldes')
      .and('contain', 'Ldio:VersionObjectCreator')
  })

  it('shows and edits the YAML of the component you click', () => {
    cy.get('[data-cy="node-Ldio:HttpOut"]').click()
    cy.get('[data-cy=inspector]').should('contain', 'HTTP Out')
    cy.get('[data-cy=component-yaml] .cm-content')
      .should('contain', 'name: Ldio:HttpOut')
      .and('contain', 'endpoint: http://sandbox-ldes-server:8080/sandbox-parcels')

    cy.get('[data-cy=component-yaml] .cm-content').type('{selectall}{backspace}name: Ldio:Telepathy', { delay: 0 })
    cy.get('[data-cy=yaml-error]').should('contain', "Unknown component 'Ldio:Telepathy'")

    cy.get('[data-cy=component-yaml] .cm-content').type('{selectall}{backspace}name: Ldio:KafkaOut', { delay: 0 })
    cy.get('[data-cy=yaml-error]').should('not.exist')
    cy.get('[data-cy="node-Ldio:KafkaOut"]').should('exist')
    cy.get('[data-cy="config-bootstrap-servers"]').should('contain', 'Required')
  })

  it('configures a component in a form that stays in step with its YAML', () => {
    cy.get('[data-cy=templates]').click()
    cy.contains('.p-menu-item', 'Replicate an LDES into a triple store').click()
    cy.contains('.p-confirmdialog button', 'Replace').click()
    cy.get('[data-cy="node-Ldio:LdesClient"]').click()
    const urls = () => cy.get('[data-cy="config-urls"]')
    const yaml = () => cy.get('[data-cy=component-yaml] .cm-content')
    urls().find('[data-cy=list-item]').should('have.length', 1)

    // A URL that is not one: the field says so, the YAML shows it, and it cannot be deployed
    urls().find('[data-cy=add-item]').click()
    urls().find('[data-cy=list-item]').eq(1).type('not a url')
    urls().should('contain', 'Not an http(s) URL')
    yaml().should('contain', '- not a url')
    cy.get('[data-cy=pipeline-validity]').should('contain', '1 problem')
    cy.get('[data-cy=deploy]').should('be.disabled')

    urls().find('[data-cy=list-item]').eq(1).clear().type('http://sandbox-ldes-server:8080/sandbox-parcels/by-time')
    urls().should('not.contain', 'Not an http(s) URL')
    yaml().should('contain', '- http://sandbox-ldes-server:8080/sandbox-parcels/by-time')
    cy.get('[data-cy=pipeline-validity]').should('contain', 'Valid')

    // A dropdown with the content types Jena reads
    cy.get('[data-cy="config-source-format"] .p-select').click()
    cy.contains('.p-select-option', 'N-Quads').click()
    yaml().should('contain', 'source-format: application/n-quads')

    // The state group is open, as the template keeps the state in SQLite
    cy.get('[data-cy="config-sqlite.directory"]').should('exist')

    // ... and the YAML drives the form
    const typed = '{name: Ldio:LdesClient, config: {urls: [http://ldes.example.org/a], sourceFormat: text/n3}}'
    yaml().type(`{selectall}{backspace}${typed.replaceAll('{', '{{}')}`, { delay: 0 })
    urls().find('[data-cy=list-item]').should('have.length', 1).first()
      .should('have.value', 'http://ldes.example.org/a')
    cy.get('[data-cy="config-source-format"]').should('contain', 'text/n3')
    cy.get('[data-cy="config-sqlite.directory"]').should('not.exist')
  })

  it('refuses a component of another kind', () => {
    cy.get('[data-cy="node-Ldio:GeoJsonToWktTransformer"]').click()
    cy.get('[data-cy=component-yaml] .cm-content').type('{selectall}{backspace}name: Ldio:ConsoleOut', { delay: 0 })
    cy.get('[data-cy=yaml-error]').should('contain', 'is an output, but this component is a transformer')
  })

  it('adds components by dragging them from the palette', () => {
    const dataTransfer = new DataTransfer()
    cy.get('[data-cy="palette-Ldio:NoopOut"]').trigger('dragstart', { dataTransfer })
    cy.get('[data-cy=canvas]').trigger('drop', { dataTransfer, clientX: 700, clientY: 300 })

    cy.get('[data-cy=canvas] .vue-flow__node').should('have.length', 7)
    cy.get('[data-cy=inspector]').should('contain', 'No-op Out')
    cy.get('[data-cy=pipeline-validity]').should('contain', '1 warnings')
  })

  it('adds components with a double click and finds them in the palette', () => {
    cy.get('[data-cy=palette-search]').type('skolem')
    cy.get('[data-cy="palette-Ldio:SkolemisationTransformer"]').dblclick()
    cy.get('[data-cy="node-Ldio:SkolemisationTransformer"]').should('exist')
  })

  it('explains what is wrong when a component is removed', () => {
    cy.get('[data-cy="node-Ldio:JsonToLdAdapter"]').click()
    cy.get('[data-cy=remove-node]').click()
    cy.get('[data-cy=pipeline-validity]').should('contain', 'problem')
    cy.get('[data-cy=inspector]').should('contain', 'HTTP In receives raw data and needs an adapter')
  })

  it('runs a member through the pipeline', () => {
    cy.get('[data-cy=run-simulation]').click()
    cy.get('[data-cy=simulation-step]', { timeout: 8000 }).should('have.length', 6)
    cy.get('[data-cy=simulation]').should('contain', 'Delivered to 2 outputs')
    cy.contains('[data-cy=simulation-step]', 'Version Object Creator').click()
    cy.get('[data-cy=step-output] .cm-content').should('contain', 'dcterms:isVersionOf')

    cy.get('[data-cy=open-ingest-result]').click()
    cy.get('[data-cy=ingest-result]').click()
    cy.contains('.p-toast', 'Ingested the result in sandbox-parcels')
  })

  it('loads a template', () => {
    cy.get('[data-cy=templates]').click()
    cy.contains('.p-menu-item', 'Replicate an LDES into a triple store').click()
    cy.contains('.p-confirmdialog button', 'Replace').click()
    cy.get('[data-cy=canvas] .vue-flow__node').should('have.length', 4)
    cy.get('[data-cy=pipeline-name]').should('have.value', 'parcels-replication')
  })

  it('redraws the canvas from edited YAML', () => {
    // One line of flow-style YAML: CodeMirror would indent typed new lines itself
    const yaml = (text: string) => cy.get('[data-cy=yaml-dialog] .cm-content')
      .type(`{selectall}{backspace}${text.replaceAll('{', '{{}')}`, { delay: 0 })

    cy.get('[data-cy=open-yaml]').click()
    cy.dialogOpened('yaml-dialog')
    yaml('{name: tiny}')
    cy.get('[data-cy=apply-yaml]').click()
    cy.get('[data-cy=yaml-dialog]').should('contain', "A pipeline needs an 'input' with a 'name'")

    yaml('{name: tiny, input: {name: "Ldio:LdesClient"}, outputs: [{name: "Ldio:ConsoleOut"}]}')
    cy.get('[data-cy=apply-yaml]').click()
    cy.get('[data-cy=yaml-dialog]').should('not.exist')
    cy.get('[data-cy=canvas] .vue-flow__node').should('have.length', 2)
    cy.get('[data-cy=pipeline-name]').should('have.value', 'tiny')
  })

  it('deploys the pipeline on an LDIO', () => {
    cy.get('[data-cy=pipeline-name]').clear().type('parcels-from-sandbox')
    cy.get('[data-cy=deploy]').click()
    cy.dialogOpened('deploy-dialog')
    cy.get('[data-cy=deploy-dialog]').should('contain', 'parcels-from-sandbox')
    cy.get('[data-cy=deploy-submit]').click()
    cy.location('pathname').should('match', /^\/ldios\//)
    cy.contains('[data-cy=pipelines-table] tr', 'parcels-from-sandbox')
  })

  it('shows the sandbox environment and sends a member to its LDIO', () => {
    cy.get('[data-cy=open-environment]').click()
    cy.get('[data-cy=sandbox-environment]')
      .should('contain', 'Running')
      .and('contain', 'sandbox-ingest')
      .and('contain', 'sandbox-parcels')
    cy.get('body').type('{esc}')

    cy.get('[data-cy=toggle-simulation]').click()
    cy.get('[data-cy=open-send]').click()
    cy.get('[data-cy=send-popover]').within(() => {
      cy.get('[data-cy=send-ldio]').should('contain', 'Sandbox LDIO')
      cy.get('[data-cy=send-pipeline]').should('contain', 'sandbox-ingest')
      cy.get('[data-cy=send-member]').click()
    })
    cy.contains('.p-toast', 'Sent the member to sandbox-ingest on Sandbox LDIO')
  })
})
