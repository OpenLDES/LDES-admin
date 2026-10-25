import { processInfo, relationLabel } from '@/domain/ldes'

describe('relationLabel', () => {
  it('names pages by their number', () => {
    expect(relationLabel({ node: 'http://sandbox-ldes-server:8080/parcels/by-page?pageNumber=2' }))
      .toBe('Page 2')
  })

  it('names time buckets by their date', () => {
    expect(relationLabel({ node: 'http://ldes/parcels/by-time?year=2026&month=10&day=4' }))
      .toBe('2026-10-04')
    expect(relationLabel({ node: 'http://ldes/parcels/by-time?year=2026&month=10' })).toBe('2026-10')
    expect(relationLabel({ node: 'http://ldes/parcels/by-time?year=2026&month=10&day=4&hour=9' }))
      .toBe('2026-10-04 09:00')
    expect(relationLabel({ node: 'http://ldes/parcels/by-time?year=2026&month=10&day=4&pageNumber=3' }))
      .toBe('2026-10-04 · page 3')
  })

  it('names tiles and references by their value', () => {
    expect(relationLabel({ node: 'http://ldes/hindrances/by-location?tile=15/16812/10970' }))
      .toBe('Tile 15/16812/10970')
    expect(relationLabel({ node: 'http://ldes/hindrances/by-location?tile=15/16812/10970&pageNumber=1' }))
      .toBe('Tile 15/16812/10970 · page 1')
    expect(relationLabel({ node: 'http://ldes/addresses/by-municipality?municipality=Gent' }))
      .toBe('Gent')
  })

  it('falls back on the value of the relation', () => {
    expect(relationLabel({ node: 'not a url', value: '2026-10-24T00:00:00Z' }))
      .toBe('2026-10-24T00:00:00Z')
  })
})

describe('processInfo', () => {
  it('describes the steps of the jobs of the LDES server', () => {
    expect(processInfo({ step: 'pagination' }).name).toBe('Pagination')
    expect(processInfo({ step: 'viewRetentionStep' }).name).toBe('View retention')
    expect(processInfo({ step: 'singleViewRetentionStep' }).name).toBe('View retention')
  })

  it('makes up a name for steps it does not know', () => {
    expect(processInfo({ step: 'completedJobExecutionsStep' }))
      .toEqual({ name: 'Completed job executions', icon: 'pi pi-cog' })
  })
})
