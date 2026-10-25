import type { Pipeline } from '@/domain/ldio'
import { SANDBOX_EVENT_STREAM_URL } from '@/domain/sandbox'
import type { SimulationInput } from './simulator'

/**
 * Example pipelines to start from in the sandbox, each with a member to run through it. Deployed
 * on the sandbox LDIO, they reach the sandbox LDES server on its host name in the Docker network.
 */
export interface PipelineTemplate {
  id: string
  title: string
  description: string
  icon: string
  pipeline: Pipeline
  sample: SimulationInput
}

const PARCEL_CONTEXT = JSON.stringify({
  '@context': {
    '@vocab': 'https://data.vlaanderen.be/ns/perceel#',
    xsd: 'http://www.w3.org/2001/XMLSchema#',
    geojson: 'https://purl.org/geojson/vocab#',
    type: '@type',
    lastModified: { '@type': 'xsd:dateTime' },
    geometry: 'geojson:geometry',
    Polygon: 'geojson:Polygon',
    coordinates: { '@id': 'geojson:coordinates', '@container': '@list' },
  },
})

export const templates: PipelineTemplate[] = [
  {
    id: 'parcels-to-ldes',
    title: 'Parcels API → versioned LDES',
    description: 'Adds a JSON-LD context to parcel updates, converts their geometry to WKT, creates version objects and ingests them in the sandbox LDES',
    icon: 'pi pi-map',
    pipeline: {
      name: 'parcels-to-ldes',
      description: 'Parcel updates from a JSON API, as version objects in the sandbox LDES',
      input: {
        name: 'Ldio:HttpIn',
        adapter: {
          name: 'Ldio:JsonToLdAdapter',
          // The geometry in the GeoJSON-LD vocabulary, for the WKT transformer, and a typed
          // timestamp, for the version object creator
          config: { context: PARCEL_CONTEXT },
        },
      },
      transformers: [
        { name: 'Ldio:GeoJsonToWktTransformer' },
        {
          name: 'Ldio:VersionObjectCreator',
          config: {
            'member-type': 'https://data.vlaanderen.be/ns/perceel#Perceel',
            'date-observed-property': '<https://data.vlaanderen.be/ns/perceel#lastModified>',
          },
        },
      ],
      outputs: [
        {
          name: 'Ldio:HttpOut',
          config: {
            endpoint: SANDBOX_EVENT_STREAM_URL,
            'rdf-writer': { 'content-type': 'text/turtle' },
          },
        },
        { name: 'Ldio:ConsoleOut' },
      ],
    },
    sample: {
      contentType: 'application/json',
      content: JSON.stringify({
        '@id': 'https://data.vlaanderen.be/id/perceel/24062B0123-00A000',
        '@type': 'Perceel',
        capakey: '24062B0123/00A000',
        municipality: 'Leuven',
        area: 512.4,
        lastModified: '2026-10-23T08:12:00Z',
        geometry: {
          type: 'Polygon',
          coordinates: [[[4.7005, 50.8798], [4.7012, 50.8798], [4.7012, 50.8803],
            [4.7005, 50.8803], [4.7005, 50.8798]]],
        },
      }, null, 2),
    },
  },
  {
    id: 'http-to-console',
    title: 'HTTP in → console',
    description: 'The basic example from the LDIO docs: parse posted RDF and log it',
    icon: 'pi pi-desktop',
    pipeline: {
      name: 'data',
      description: 'Reads incoming RDF over HTTP and writes it to the console',
      input: { name: 'Ldio:HttpIn', adapter: { name: 'Ldio:RdfAdapter' } },
      transformers: [],
      outputs: [
        { name: 'Ldio:ConsoleOut', config: { 'rdf-writer': { 'content-type': 'application/n-quads' } } },
      ],
    },
    sample: {
      contentType: 'text/turtle',
      content: `@prefix schema: <http://schema.org/> .

<http://www.janedoe.com/#me> a schema:Person ;
    schema:name "Jane Doe" ;
    schema:jobTitle "Professor" ;
    schema:telephone "(425) 123-4567" ;
    schema:url <http://www.janedoe.com> .`,
    },
  },
  {
    id: 'replicate-ldes',
    title: 'Replicate an LDES into a triple store',
    description: 'Follows an LDES, turns version objects back into state objects, drops unchanged ones and stores the rest',
    icon: 'pi pi-database',
    pipeline: {
      name: 'parcels-replication',
      description: 'Keeps a triple store in sync with the latest state of every parcel',
      input: {
        name: 'Ldio:LdesClient',
        config: { urls: [`${SANDBOX_EVENT_STREAM_URL}/by-page`], state: 'sqlite' },
      },
      transformers: [
        {
          name: 'Ldio:VersionMaterialiser',
          config: { 'versionOf-property': 'http://purl.org/dc/terms/isVersionOf' },
        },
        { name: 'Ldio:ChangeDetectionFilter' },
      ],
      outputs: [
        {
          name: 'Ldio:RepositorySink',
          config: { 'sparql-host': 'http://graphdb:7200', 'repository-id': 'parcels' },
        },
      ],
    },
    sample: {
      contentType: 'text/turtle',
      content: `@prefix perceel: <https://data.vlaanderen.be/ns/perceel#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix prov:    <http://www.w3.org/ns/prov#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .

<https://data.vlaanderen.be/id/perceel/24062B0123-00A000/2026-10-23T08:12:00Z>
    a perceel:Perceel ;
    dcterms:isVersionOf <https://data.vlaanderen.be/id/perceel/24062B0123-00A000> ;
    prov:generatedAtTime "2026-10-23T08:12:00Z"^^xsd:dateTime ;
    perceel:capakey "24062B0123/00A000" ;
    perceel:municipality "Leuven" .`,
    },
  },
  {
    id: 'hindrances-geo',
    title: 'Mobility hindrances → SPARQL store',
    description: 'GeoJSON points to WKT and blank nodes to stable IRIs, before writing to a SPARQL endpoint',
    icon: 'pi pi-car',
    pipeline: {
      name: 'mobility-hindrances',
      description: 'Road works and events from the hindrance API into a SPARQL store',
      input: {
        name: 'Ldio:HttpIn',
        adapter: {
          name: 'Ldio:JsonToLdAdapter',
          config: { context: '{"@vocab": "https://data.vlaanderen.be/ns/mobiliteit#"}' },
        },
      },
      transformers: [
        { name: 'Ldio:GeoJsonToWktTransformer' },
        { name: 'Ldio:SkolemisationTransformer', config: { skolemDomain: 'https://data.vlaanderen.be' } },
      ],
      outputs: [
        {
          name: 'Ldio:HttpSparqlOut',
          config: { endpoint: 'http://virtuoso:8890/sparql', graph: 'https://data.vlaanderen.be/graph/hinder' },
        },
        { name: 'Ldio:ConsoleOut', config: { 'rdf-writer': { 'content-type': 'application/n-quads' } } },
      ],
    },
    sample: {
      contentType: 'application/json',
      content: JSON.stringify({
        '@id': 'https://data.vlaanderen.be/id/hinder/GIPOD-1287345',
        '@type': 'Mobiliteitshinder',
        description: 'Road works on the Tiensesteenweg',
        status: 'ongoing',
        period: { start: '2026-10-20T07:00:00Z', end: '2026-11-14T17:00:00Z' },
        location: { type: 'Point', coordinates: [4.7253, 50.8681] },
      }, null, 2),
    },
  },
]

export const defaultTemplate = templates[0]

export function templateById(id: string): PipelineTemplate | undefined {
  return templates.find((template) => template.id === id)
}
