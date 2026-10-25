import { PREFIXES, prefixLines } from '@/domain/rdf'
import { type DemoExample, examplePipelineName, isoSeconds } from './example'

/**
 * Air quality and weather observations of three stations in Ghent, modelled with the SOSA/SSN
 * ontology: every member is an sosa:Observation of one property by one sensor, with its result as
 * a QUDT quantity value. Each observation is a version of "the latest observation of this property
 * at this station", so a client can keep the current values by materialising the versions.
 *
 * The pipeline receives readings as plain JSON, gives them meaning with a JSON-LD context and
 * turns them into version objects.
 */
const { sosa, qudt, unit, xsd } = PREFIXES
const BASE = 'https://sensors.example.org/id/'

const STATIONS = [
  { id: 'gent-sint-pieters', warmth: 0.4, dust: 6 },
  { id: 'gent-dampoort', warmth: 0, dust: 11 },
  { id: 'gent-korenmarkt', warmth: 0.9, dust: 3 },
]

interface ObservedProperty {
  id: string
  sensor: string
  unit: string
  /** The value at a station at a time, varying over the day */
  value(station: (typeof STATIONS)[number], time: Date): number
}

/** -1 at four in the morning, 1 at four in the afternoon */
const daily = (time: Date) => Math.sin(((time.getUTCHours() - 10) / 24) * 2 * Math.PI)
const round = (value: number) => Math.round(value * 10) / 10

const PROPERTIES: ObservedProperty[] = [
  {
    id: 'air-temperature',
    sensor: 'thermometer',
    unit: 'DEG_C',
    value: (station, time) => round(11.5 + station.warmth + 3.2 * daily(time)),
  },
  {
    id: 'relative-humidity',
    sensor: 'hygrometer',
    unit: 'PERCENT',
    value: (station, time) => round(79.5 - 8.5 * daily(time) - station.warmth * 2),
  },
  {
    id: 'pm10',
    sensor: 'particulate-matter-sensor',
    unit: 'MicroGM-PER-M3',
    value: (station, time) => round(17.5 + station.dust - 4.5 * daily(time)),
  },
]

interface Reading {
  station: string
  property: ObservedProperty
  time: string
  value: number
}

function reading(index: number, time: Date): Reading {
  const station = STATIONS[index % STATIONS.length]
  const property = PROPERTIES[Math.floor(index / STATIONS.length) % PROPERTIES.length]
  return { station: station.id, property, time: isoSeconds(time), value: property.value(station, time) }
}

/** Every property at every station, every hour of the last four hours */
function readings(now: Date): Reading[] {
  const lastHour = new Date(now)
  lastHour.setUTCMinutes(0, 0, 0)
  return [3, 2, 1, 0].flatMap((hoursAgo) => {
    const time = new Date(lastHour.getTime() - hoursAgo * 3_600_000)
    return Array.from({ length: STATIONS.length * PROPERTIES.length }, (_, index) => reading(index, time))
  })
}

const stateOf = (reading: Reading) => `${BASE}observation/${reading.station}/${reading.property.id}`

function observationTurtle(reading: Reading): string {
  const state = stateOf(reading)
  return `${prefixLines('sosa', 'qudt', 'unit', 'dcterms', 'xsd')}

<${state}/${reading.time}>
    a sosa:Observation ;
    dcterms:isVersionOf <${state}> ;
    sosa:madeBySensor <${BASE}sensor/${reading.station}/${reading.property.sensor}> ;
    sosa:observedProperty <${BASE}property/${reading.property.id}> ;
    sosa:hasFeatureOfInterest <${BASE}feature/${reading.station}> ;
    sosa:resultTime "${reading.time}"^^xsd:dateTime ;
    sosa:hasResult [
        a qudt:QuantityValue ;
        qudt:numericValue "${reading.value}"^^xsd:double ;
        qudt:unit unit:${reading.property.unit}
    ] .
`
}

/** A reading as a sensor gateway sends it to the pipeline */
function observationJson(reading: Reading) {
  return {
    id: stateOf(reading),
    type: 'Observation',
    madeBySensor: `${BASE}sensor/${reading.station}/${reading.property.sensor}`,
    observedProperty: `${BASE}property/${reading.property.id}`,
    hasFeatureOfInterest: `${BASE}feature/${reading.station}`,
    resultTime: reading.time,
    hasResult: { type: 'QuantityValue', numericValue: reading.value, unit: `${unit}${reading.property.unit}` },
  }
}

/** Gives the JSON of the sensor gateway its meaning in SOSA and QUDT */
export const SENSOR_CONTEXT = JSON.stringify({
  '@context': {
    sosa,
    qudt,
    xsd,
    id: '@id',
    type: '@type',
    Observation: 'sosa:Observation',
    QuantityValue: 'qudt:QuantityValue',
    madeBySensor: { '@id': 'sosa:madeBySensor', '@type': '@id' },
    observedProperty: { '@id': 'sosa:observedProperty', '@type': '@id' },
    hasFeatureOfInterest: { '@id': 'sosa:hasFeatureOfInterest', '@type': '@id' },
    // The version object creator only takes an xsd:dateTime as the time of the version
    resultTime: { '@id': 'sosa:resultTime', '@type': 'xsd:dateTime' },
    hasResult: 'sosa:hasResult',
    numericValue: { '@id': 'qudt:numericValue', '@type': 'xsd:double' },
    unit: { '@id': 'qudt:unit', '@type': '@id' },
  },
}, null, 2)

export const sensors: DemoExample = {
  id: 'sensors',
  title: 'Sensor observations',
  offer: 'Create sensor demo stream, views, data, and pipeline',
  description: 'Temperature, humidity and particulate matter of three stations, modelled with SOSA/SSN',
  icon: 'pi pi-gauge',
  collection: 'sensor-observations',
  memberClass: `${sosa}Observation`,
  timestampPath: `${sosa}resultTime`,

  eventStream: (serverUrl, collection) => `${prefixLines('ldes', 'dcterms', 'tree', 'sh', 'sosa', 'qudt', 'xsd')}
@prefix server:     <${serverUrl}/> .

server:${collection} a ldes:EventStream ;
    ldes:timestampPath sosa:resultTime ;
    ldes:versionOfPath dcterms:isVersionOf ;
    tree:shape [
        a sh:NodeShape ;
        sh:targetClass sosa:Observation ;
        sh:property [
            sh:path sosa:madeBySensor ;
            sh:nodeKind sh:IRI ;
            sh:minCount 1 ;
            sh:maxCount 1
        ] , [
            sh:path sosa:observedProperty ;
            sh:nodeKind sh:IRI ;
            sh:minCount 1 ;
            sh:maxCount 1
        ] , [
            sh:path sosa:hasFeatureOfInterest ;
            sh:nodeKind sh:IRI ;
            sh:minCount 1 ;
            sh:maxCount 1
        ] , [
            sh:path sosa:resultTime ;
            sh:datatype xsd:dateTime ;
            sh:minCount 1 ;
            sh:maxCount 1
        ] , [
            sh:path ( sosa:hasResult qudt:numericValue ) ;
            sh:datatype xsd:double ;
            sh:minCount 1 ;
            sh:maxCount 1
        ] , [
            sh:path ( sosa:hasResult qudt:unit ) ;
            sh:nodeKind sh:IRI ;
            sh:minCount 1 ;
            sh:maxCount 1
        ]
    ] .
`,

  pipeline: (collection, eventStreamUrl) => ({
    name: examplePipelineName(collection),
    description: `Receives sensor readings as JSON, maps them to SOSA observations and ingests them as version objects in ${collection}`,
    input: {
      name: 'Ldio:HttpIn',
      adapter: { name: 'Ldio:JsonToLdAdapter', config: { context: SENSOR_CONTEXT } },
    },
    transformers: [
      {
        name: 'Ldio:VersionObjectCreator',
        config: {
          'member-type': `${sosa}Observation`,
          'date-observed-property': `<${sosa}resultTime>`,
          // The time of the version is the result time, which the observation holds already
          'generatedAt-property': `${sosa}resultTime`,
        },
      },
    ],
    outputs: [
      {
        name: 'Ldio:HttpOut',
        config: { endpoint: eventStreamUrl, 'rdf-writer': { 'content-type': 'text/turtle' } },
      },
    ],
  }),

  member(index, time) {
    const observation = reading(index, time)
    return {
      id: `${stateOf(observation)}/${observation.time}`,
      isVersionOf: stateOf(observation),
      content: observationTurtle(observation),
    }
  },

  sampleData: {
    description: `${4 * STATIONS.length * PROPERTIES.length} observations of the last four hours`,
    members: (now) => readings(now).map(observationTurtle),
    message: (now) => ({
      content: JSON.stringify(readings(now).map(observationJson), null, 2),
      contentType: 'application/json',
    }),
  },
}
