const RUNNING_SANDBOX = {
  state: 'RUNNING',
  ldesServer: {
    id: '0b6c4f7e-0000-4000-8000-000000000001',
    type: 'LDES_SERVER',
    name: 'Sandbox LDES server',
    url: 'http://localhost:55001',
    internalUrl: 'http://sandbox-ldes-server:8080',
    origin: 'LOCAL',
    sandbox: true,
    registeredAt: '2026-10-24T08:00:00Z',
  },
  ldio: {
    id: '0b6c4f7e-0000-4000-8000-000000000002',
    type: 'LDIO',
    name: 'Sandbox LDIO',
    url: 'http://localhost:55002',
    internalUrl: 'http://sandbox-ldio:8080',
    origin: 'LOCAL',
    sandbox: true,
    registeredAt: '2026-10-24T08:00:00Z',
  },
  eventStream: 'sandbox-parcels',
  ingestPipeline: 'sandbox-ingest',
  eventStreamUrl: 'http://sandbox-ldes-server:8080/sandbox-parcels',
}

const STOPPED_SANDBOX = {
  ...RUNNING_SANDBOX, state: 'STOPPED', ldesServer: null, ldio: null, eventStreamUrl: null,
}

/** A backend without instances, whose sandbox starts when asked */
function stubBackend() {
  let running = false
  cy.intercept('GET', '/actuator/health', { status: 'UP' })
  cy.intercept('GET', '/api/v1/dashboard', (request) => request.reply({ ldesServers: [], ldios: [] }))
  cy.intercept('GET', '/api/v1/dashboard/throughput', [])
  cy.intercept('GET', '/api/v1/activity*', [])
  cy.intercept('GET', '/api/v1/ldes-servers', (request) =>
    request.reply(running ? [RUNNING_SANDBOX.ldesServer] : []))
  cy.intercept('GET', '/api/v1/ldios', (request) => request.reply(running ? [RUNNING_SANDBOX.ldio] : []))
  cy.intercept('GET', '/api/v1/sandbox', (request) =>
    request.reply(running ? RUNNING_SANDBOX : STOPPED_SANDBOX))
  cy.intercept('POST', '/api/v1/sandbox', (request) => {
    running = true
    request.reply({ delay: 500, body: RUNNING_SANDBOX })
  }).as('startSandbox')
  cy.intercept('DELETE', '/api/v1/sandbox', (request) => {
    running = false
    request.reply({ statusCode: 204 })
  }).as('stopSandbox')
}

describe('demo and production mode', () => {
  beforeEach(() => {
    cy.visit('/', { onBeforeLoad: (win) => win.localStorage.setItem('ldes-admin.dark', 'false') })
  })

  it('starts in demo mode, on sample data', () => {
    cy.get('[data-cy=mode-demo]').should('have.attr', 'aria-checked', 'true')
    cy.get('[data-cy=mode-notice]').should('contain', 'Demo mode')
    cy.get('[data-cy=dashboard-server]').should('have.length', 4)
  })

  it('switches to the backend and back', () => {
    stubBackend()

    cy.get('[data-cy=mode-production]').click()

    cy.get('[data-cy=mode-production]').should('have.attr', 'aria-checked', 'true')
    cy.contains('.p-toast', 'Connected to the LDES-admin backend')
    cy.get('[data-cy=mode-notice]').should('contain', 'Production mode')
    cy.get('[data-cy=dashboard-server]').should('not.exist')
    cy.contains('No throughput sampled yet')
    cy.contains('Nothing happened yet')

    cy.reload()
    cy.get('[data-cy=mode-production]').should('have.attr', 'aria-checked', 'true')

    cy.get('[data-cy=mode-demo]').click()
    cy.get('[data-cy=dashboard-server]').should('have.length', 4)
  })

  it('starts and stops the sandbox through the backend', () => {
    stubBackend()
    cy.get('[data-cy=mode-production]').click()

    cy.get('[data-cy=sandbox-state]').should('contain', 'Stopped')
    cy.get('[data-cy=start-sandbox]').click()
    cy.get('[data-cy=sandbox-state]').should('contain', 'Starting')
    cy.wait('@startSandbox')
    cy.get('[data-cy=sandbox-state]').should('contain', 'Running')
    cy.get('[data-cy=sandbox-environment]')
      .should('contain', 'http://sandbox-ldes-server:8080/sandbox-parcels')
    cy.get('[data-cy=sidebar] a[href="/ldes-servers"] .nav-count').should('have.text', '1')

    cy.get('[data-cy=stop-sandbox]').click()
    cy.contains('.p-confirmdialog button', 'Stop sandbox').click()
    cy.wait('@stopSandbox')
    cy.get('[data-cy=sandbox-state]').should('contain', 'Stopped')
  })

  it('polls the URL of an HTTP poller through the backend', () => {
    stubBackend()
    cy.intercept('POST', '/api/v1/sandbox/poll', {
      body: { contentType: 'application/json; charset=utf-8', body: '[{"name": "Vrijdagmarkt", "availablecapacity": 312}]' },
    }).as('poll')
    cy.get('[data-cy=mode-production]').click()
    cy.get('[data-cy=mode-notice]').should('contain', 'Production mode')
    cy.get('[data-cy=sidebar] a[href="/sandbox"]').click()

    const pipeline = `{name: parkings, input: {name: "Ldio:HttpInPoller",
      config: {url: "https://data.stad.gent/parkings", cron: "0 * * * * *", auth: {type: API_KEY, api-key: secret}},
      adapter: {name: "Ldio:JsonToLdAdapter"}}, outputs: [{name: "Ldio:ConsoleOut"}]}`.replace(/\s+/g, ' ')
    cy.get('[data-cy=open-yaml]').click()
    cy.dialogOpened('yaml-dialog')
    cy.get('[data-cy=yaml-dialog] .cm-content')
      .type(`{selectall}{backspace}${pipeline.replaceAll('{', '{{}')}`, { delay: 0 })
    cy.get('[data-cy=apply-yaml]').click()
    cy.get('[data-cy=toggle-simulation]').click()
    cy.get('[data-cy=fetch-input]').click()

    cy.wait('@poll').its('request.body').should('deep.equal', {
      url: 'https://data.stad.gent/parkings',
      headers: { 'X-API-KEY': 'secret' },
    })
    cy.get('[data-cy=sample-input] .cm-content').should('contain', '"availablecapacity": 312')
    cy.get('[data-cy=sample-content-type]').should('contain', 'JSON')
  })

  it('runs the pipeline of the sandbox on the backend, step by step', () => {
    stubBackend()
    const step = (kind: string, component: string, status: string, summary: string, outputs: string[] = []) =>
      ({ kind, index: 0, component, status, summary, outputs, contentType: 'text/turtle', format: 'turtle', durationMs: 4 })
    cy.intercept('POST', '/api/v1/sandbox/runs', {
      body: {
        steps: [
          step('input', 'Ldio:HttpIn', 'ok', 'Received POST /parcels-to-ldes (application/json)', ['{}']),
          step('adapter', 'Ldio:JsonToLdAdapter', 'ok', 'Made 2 models', [
            '<http://example.org/a> <http://example.org/name> "first" .',
            '<http://example.org/b> <http://example.org/name> "second" .']),
          step('transformer', 'Ldio:GeoJsonToWktTransformer', 'error', 'No geometry in the GeoJSON-LD vocabulary'),
        ],
        delivered: false,
        members: [],
      },
    }).as('run')
    cy.get('[data-cy=mode-production]').click()
    cy.get('[data-cy=mode-notice]').should('contain', 'Production mode')
    cy.get('[data-cy=sidebar] a[href="/sandbox"]').click()

    cy.get('[data-cy=execute-outputs]').click()
    cy.get('[data-cy=run-simulation]').click()
    cy.wait('@run').its('request.body').should((body) => {
      expect(body.pipeline.name).to.equal('parcels-to-ldes')
      expect(body.input.contentType).to.equal('application/json')
      expect(body.executeOutputs).to.equal(true)
      expect(body.session).to.be.a('string').and.not.be.empty
    })

    cy.get('[data-cy=simulation-step]', { timeout: 8000 }).should('have.length', 3)
    cy.get('[data-cy=simulation]').should('contain', 'Stopped at GeoJSON to WKT')
      .and('contain', 'No geometry in the GeoJSON-LD vocabulary')
    cy.contains('[data-cy=simulation-step]', 'JSON to JSON-LD').click()
    cy.get('[data-cy=model-pager]').should('contain', 'Model 1 of 2')
    cy.get('[data-cy=step-output] .cm-content').should('contain', '"first"')
    cy.get('[data-cy=next-model]').click()
    cy.get('[data-cy=model-pager]').should('contain', 'Model 2 of 2')
    cy.get('[data-cy=step-output] .cm-content').should('contain', '"second"')
  })

  it('tells when the backend does not answer', () => {
    cy.intercept('GET', '/actuator/health', { statusCode: 502 })
    cy.intercept('GET', '/api/v1/**', { statusCode: 502, body: { detail: 'Bad gateway' } })

    cy.get('[data-cy=mode-production]').click()

    cy.get('[data-cy=backend-down]').should('contain', 'Backend unreachable')
    cy.contains('.p-toast', 'does not answer')
  })
})
