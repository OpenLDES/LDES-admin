import { componentInfo } from '@/pipeline/catalog'
import {
  configProblems,
  readProperty,
  unknownKeys,
  valueProblems,
  writeProperty,
} from '@/pipeline/config'

const property = (component: string, key: string) =>
  componentInfo(component)!.properties.find((candidate) => candidate.key === key)!

const ldesClient = (key: string) => property('Ldio:LdesClient', key)

describe('component configuration', () => {
  describe('reading', () => {
    it('finds nested and flat keys, written the way LDIO accepts them', () => {
      expect(readProperty({ 'rdf-writer': { 'content-type': 'text/turtle' } }, 'rdf-writer.content-type'))
        .toBe('text/turtle')
      expect(readProperty({ 'rdf-writer.content-type': 'application/n-quads' }, 'rdf-writer.content-type'))
        .toBe('application/n-quads')
      // As a deployed pipeline comes back from LDIO, and as in the LDIO docs
      expect(readProperty({ 'rdfwriter.contenttype': 'text/turtle' }, 'rdf-writer.content-type'))
        .toBe('text/turtle')
      expect(readProperty({ sourceFormat: 'application/n-quads' }, 'source-format')).toBe('application/n-quads')
    })
  })

  describe('writing', () => {
    it('nests a new property along the dots of its key', () => {
      expect(writeProperty({ endpoint: 'http://x.org' }, property('Ldio:HttpOut', 'rdf-writer.content-type'),
        'application/n-quads'))
        .toEqual({ endpoint: 'http://x.org', 'rdf-writer': { 'content-type': 'application/n-quads' } })
    })

    it('changes a property in place, keeping how it is written', () => {
      const config = { sourceFormat: 'text/turtle', urls: ['http://x.org/ldes'] }

      const changed = writeProperty(config, ldesClient('source-format'), 'application/n-quads')

      expect(changed).toEqual({ sourceFormat: 'application/n-quads', urls: ['http://x.org/ldes'] })
      expect(Object.keys(changed)).toEqual(['sourceFormat', 'urls'])
      expect(config.sourceFormat).toBe('text/turtle')
    })

    it('removes a blank property, and the maps it leaves empty', () => {
      const config = { urls: ['http://x.org'], materialisation: { enabled: true } }

      expect(writeProperty(config, ldesClient('materialisation.enabled'), undefined))
        .toEqual({ urls: ['http://x.org'] })
      expect(writeProperty(config, ldesClient('urls'), [])).toEqual({ materialisation: { enabled: true } })
    })

    it('stores booleans and whole numbers as such', () => {
      expect(writeProperty({}, ldesClient('enable-exactly-once'), false)).toEqual({ 'enable-exactly-once': false })
      expect(writeProperty({}, ldesClient('retries.max'), '7')).toEqual({ retries: { max: 7 } })
      expect(writeProperty({}, ldesClient('retries.max'), 'seven')).toEqual({ retries: { max: 'seven' } })
    })

    it('writes a single poller URL as a value', () => {
      const url = property('Ldio:HttpInPoller', 'url')

      expect(writeProperty({}, url, ['https://api.example.org'])).toEqual({ url: 'https://api.example.org' })
      expect(writeProperty({}, url, ['https://a.org', 'https://b.org']))
        .toEqual({ url: ['https://a.org', 'https://b.org'] })
    })
  })

  describe('validating values', () => {
    it('checks every URL of a list', () => {
      expect(valueProblems(ldesClient('urls'), ['http://x.org/ldes', 'ftp://x.org', '', 'http://x.org/ldes']))
        .toEqual([
          { key: 'urls', index: 1, severity: 'error', message: 'Not an http(s) URL' },
          { key: 'urls', index: 2, severity: 'error', message: 'Enter a URL, or remove this line' },
          { key: 'urls', index: 3, severity: 'warning', message: 'Listed twice' },
        ])
    })

    it('only accepts the content types Jena supports for the source format', () => {
      expect(valueProblems(ldesClient('source-format'), 'application/rdf+protobuf')).toEqual([])
      expect(valueProblems(ldesClient('source-format'), 'application/json')[0].message)
        .toMatch(/^Choose one of text\/turtle, application\/n-quads/)
    })

    it('accepts any content type where an adapter reads the messages', () => {
      const contentType = property('Ldio:KafkaIn', 'content-type')

      expect(valueProblems(contentType, 'application/json')).toEqual([])
      expect(valueProblems(contentType, 'json')[0].message).toBe('Enter a content type, e.g. application/json')
    })

    it.each([
      ['Ldio:HttpInPoller', 'interval', '1 minute', 'Enter an ISO 8601 duration, e.g. PT1M or PT30S'],
      ['Ldio:HttpInPoller', 'cron', '* * * * *', 'Enter 6 fields: second minute hour day month weekday, e.g. */10 * * * * *'],
      ['Ldio:HttpInPoller', 'cron', '0 0 25 * * *', 'The hour goes from 0 to 23, not 25'],
      ['Ldio:LdesClient', 'retries.max', '-1', 'Enter 0 or more'],
      ['Ldio:VersionObjectCreator', 'member-type', '<https://example.org/Thing>', 'Leave out the angle brackets'],
      ['Ldio:VersionObjectCreator', 'date-observed-property', 'ex:modified', 'Enter IRIs in angle brackets, e.g. <https://example.org/name>'],
      ['Ldio:JsonToLdAdapter', 'context', '{"@vocab": "https://example.org/"}', 'An inline context needs an @context entry: {"@context": {...}}'],
      ['Ldio:KafkaOut', 'bootstrap-servers', 'localhost', 'Enter host:port pairs separated by commas, e.g. localhost:9092'],
    ])('validates %s %s: %s', (component, key, value, message) => {
      const problems = valueProblems(property(component, key), value)

      expect(problems.map((problem) => problem.message)).toEqual([message])
    })

    it('accepts valid values', () => {
      expect(valueProblems(property('Ldio:HttpInPoller', 'interval'), 'PT1M')).toEqual([])
      expect(valueProblems(property('Ldio:HttpInPoller', 'cron'), '*/10 * * * * *')).toEqual([])
      expect(valueProblems(property('Ldio:VersionObjectCreator', 'date-observed-property'),
        '<https://example.org/meta>/<https://example.org/modified>')).toEqual([])
      expect(valueProblems(property('Ldio:JsonToLdAdapter', 'context'),
        '{"@context": {"@vocab": "https://example.org/"}}')).toEqual([])
    })
  })

  describe('validating a configuration', () => {
    it('needs the required properties', () => {
      expect(configProblems('Ldio:LdesClient', {}))
        .toContainEqual({ key: 'urls', severity: 'error', message: 'Required' })
    })

    it('needs what the chosen authentication needs, and only then', () => {
      const urls = ['http://x.org/ldes']

      expect(configProblems('Ldio:LdesClient', { urls, auth: { type: 'API_KEY' } }))
        .toEqual([{ key: 'auth.api-key', severity: 'error', message: 'Needed for API key authentication' }])
      expect(configProblems('Ldio:LdesClient', { urls, auth: { 'api-key': 'secret' } }))
        .toEqual([{ key: 'auth.api-key', severity: 'warning', message: 'Only used when auth.type is API_KEY' }])
    })

    it('needs an interval or a cron expression to poll', () => {
      expect(configProblems('Ldio:HttpInPoller', { url: 'https://api.example.org' }))
        .toEqual([{ key: 'interval', severity: 'error', message: "Set 'interval' or 'cron'" }])
    })

    it('warns that exactly-once filtering does not combine with materialisation', () => {
      expect(configProblems('Ldio:LdesClient', { urls: ['http://x.org'], materialisation: { enabled: true } })
        .map((problem) => problem.key)).toEqual(['enable-exactly-once'])
      expect(configProblems('Ldio:LdesClient', {
        urls: ['http://x.org'], materialisation: { enabled: true }, 'enable-exactly-once': false,
      })).toEqual([])
    })

    it('points out keys that are not properties of the component', () => {
      const config = { urls: ['http://x.org'], sourceFormatt: 'text/turtle', retries: { maximum: 3 } }

      expect(unknownKeys(componentInfo('Ldio:LdesClient')!, config)).toEqual([['sourceFormatt'], ['retries', 'maximum']])
      expect(configProblems('Ldio:LdesClient', config).map((problem) => problem.message))
        .toEqual(['Not a property of LDES Client; LDIO ignores it', 'Not a property of LDES Client; LDIO ignores it'])
    })

    it('leaves nested configuration of an enricher adapter alone', () => {
      expect(configProblems('Ldio:HttpEnricher', {
        adapter: { name: 'Ldio:JsonToLdAdapter', config: { context: 'https://example.org/context.jsonld' } },
        'url-property-path': '<http://example.org/url>',
      })).toEqual([])
    })
  })
})
