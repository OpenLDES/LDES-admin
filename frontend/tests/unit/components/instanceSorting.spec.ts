import type { LdesServerSummary, LdioSummary } from '@/domain/dashboard'
import type { Instance } from '@/domain/instance'
import {
  instanceSortOptions,
  readInstanceSort,
  saveInstanceSort,
  sortInstanceSummaries,
} from '@/components/instances/instanceSorting'

function instance(name: string, type: Instance['type']): Instance {
  return {
    id: name,
    type,
    name,
    url: `https://${name}.example.org`,
    origin: 'REGISTERED',
    sandbox: false,
    registeredAt: '2026-10-26T10:00:00Z',
  }
}

function server(name: string, streamViews: number[], ingestedMembers: number): LdesServerSummary {
  return {
    server: instance(name, 'LDES_SERVER'),
    health: { status: 'UP', checkedAt: '' },
    ingestedMembers,
    streams: streamViews.map((viewCount, index) => ({
      collection: `stream-${index}`,
      ingestedMembers: 0,
      backlog: 0,
      views: Array.from({ length: viewCount }, (_, viewIndex) => ({
        view: `view-${viewIndex}`,
        bucketisedMembers: 0,
        paginatedMembers: 0,
      })),
    })),
    problems: [],
  }
}

function ldio(
  name: string,
  running: number,
  halted: number,
  processedMembers: number,
): LdioSummary {
  return {
    ldio: instance(name, 'LDIO'),
    health: { status: 'UP', checkedAt: '' },
    pipelinesByStatus: {},
    processedMembers,
    pipelines: [
      ...Array.from({ length: running }, (_, index) => ({
        name: `running-${index}`,
        status: 'RUNNING' as const,
        membersIn: 0,
        membersOut: 0,
      })),
      ...Array.from({ length: halted }, (_, index) => ({
        name: `halted-${index}`,
        status: 'HALTED' as const,
        membersIn: 0,
        membersOut: 0,
      })),
    ],
    problems: [],
  }
}

describe('instance sorting', () => {
  it('offers the supported sort orders for each list', () => {
    expect(instanceSortOptions('LDES_SERVER').map(({ value }) => value)).toEqual([
      'name-asc', 'name-desc', 'streams-asc', 'streams-desc',
      'views-asc', 'views-desc', 'members-asc', 'members-desc',
    ])
    expect(instanceSortOptions('LDIO').map(({ value }) => value)).toEqual([
      'name-asc', 'name-desc', 'running-asc', 'running-desc',
      'halted-asc', 'halted-desc', 'processed-asc', 'processed-desc',
    ])
  })

  it.each([
    ['name-asc', ['Alpha', 'Bravo', 'Charlie']],
    ['name-desc', ['Charlie', 'Bravo', 'Alpha']],
    ['streams-asc', ['Bravo', 'Alpha', 'Charlie']],
    ['streams-desc', ['Charlie', 'Alpha', 'Bravo']],
    ['views-asc', ['Bravo', 'Charlie', 'Alpha']],
    ['views-desc', ['Alpha', 'Charlie', 'Bravo']],
    ['members-asc', ['Bravo', 'Charlie', 'Alpha']],
    ['members-desc', ['Alpha', 'Charlie', 'Bravo']],
  ] as const)('sorts LDES servers by %s', (sort, expected) => {
    const summaries = [
      server('Alpha', [2, 1], 300),
      server('Bravo', [1], 100),
      server('Charlie', [1, 1, 0], 200),
    ]

    expect(sortInstanceSummaries(summaries, 'LDES_SERVER', sort).map(({ server: item }) => item.name))
      .toEqual(expected)
  })

  it.each([
    ['name-asc', ['Alpha', 'Bravo', 'Charlie']],
    ['name-desc', ['Charlie', 'Bravo', 'Alpha']],
    ['running-asc', ['Bravo', 'Charlie', 'Alpha']],
    ['running-desc', ['Alpha', 'Charlie', 'Bravo']],
    ['halted-asc', ['Charlie', 'Alpha', 'Bravo']],
    ['halted-desc', ['Bravo', 'Alpha', 'Charlie']],
    ['processed-asc', ['Bravo', 'Charlie', 'Alpha']],
    ['processed-desc', ['Alpha', 'Charlie', 'Bravo']],
  ] as const)('sorts LDIOs by %s', (sort, expected) => {
    const summaries = [
      ldio('Alpha', 3, 1, 300),
      ldio('Bravo', 1, 3, 100),
      ldio('Charlie', 2, 0, 200),
    ]

    expect(sortInstanceSummaries(summaries, 'LDIO', sort).map(({ ldio: item }) => item.name))
      .toEqual(expected)
  })

  it('defaults to ascending name order and persists valid choices separately by list', () => {
    localStorage.clear()
    expect(readInstanceSort('LDES_SERVER')).toBe('name-asc')
    saveInstanceSort('LDES_SERVER', 'views-desc')
    saveInstanceSort('LDIO', 'processed-asc')

    expect(readInstanceSort('LDES_SERVER')).toBe('views-desc')
    expect(readInstanceSort('LDIO')).toBe('processed-asc')
  })

  it('ignores a saved order that is not supported on the current list', () => {
    localStorage.setItem('ldes-admin.instances.sort.LDES_SERVER', 'running-desc')

    expect(readInstanceSort('LDES_SERVER')).toBe('name-asc')
  })
})
