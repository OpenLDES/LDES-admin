import { PREFIXES, prefixLines } from '@/domain/rdf'
import { type DemoExample, examplePipelineName, isoSeconds } from './example'

/**
 * The occupancy of the car parks of Ghent, after the "advanced conversion" onboarding example of
 * OpenLDES (https://github.com/OpenLDES/Onboarding-Example/tree/main/advanced-conversion). The
 * pipeline polls the open data of the city every minute, maps each car park to an intermediate
 * model with RML and reshapes that into the Mobivoc vocabulary with a SPARQL CONSTRUCT query. A
 * car park becomes a new member when its last update changes; the server ignores the versions it
 * has already.
 *
 * Unlike the onboarding example, it is one pipeline instead of two, and versions get a / between
 * the car park and the time: some car parks have a # in their IRI already.
 */
const { mv, dcterms } = PREFIXES

export const GHENT_PARKING_URL = 'https://data.stad.gent/api/explore/v2.1/catalog/datasets/bezetting-parkeergarages-real-time/exports/json?lang=en&timezone=Europe%2FBrussels'

/** One named graph per car park, so the adapter passes them on one by one */
const MAPPING = `@prefix rml:   <http://semweb.mmlab.be/ns/rml#> .
@prefix rr:    <http://www.w3.org/ns/r2rml#> .
@prefix ql:    <http://semweb.mmlab.be/ns/ql#> .
@prefix carml: <http://carml.taxonic.com/carml/> .
@prefix xsd:   <http://www.w3.org/2001/XMLSchema#> .
@prefix mv:    <http://schema.mobivoc.org/#> .
@prefix temp:  <https://temp.org/ns/advanced-compose#> .

temp:TriplesMap a rr:TriplesMap ;
    rml:logicalSource [
        a rml:LogicalSource ;
        rml:source [ a carml:Stream ] ;
        rml:referenceFormulation ql:JSONPath ;
        rml:iterator "$.*"
    ] ;
    rr:subjectMap [
        rr:graphMap [ rr:template "{id}" ] ;
        rml:reference "id" ;
        rr:class mv:ParkingFacility
    ] ;
    rr:predicateObjectMap [
        rr:predicate temp:type ;
        rr:objectMap [ rml:reference "type" ]
    ] , [
        rr:predicate temp:name ;
        rr:objectMap [ rml:reference "name" ]
    ] , [
        rr:predicate temp:lastupdate ;
        rr:objectMap [ rml:reference "lastupdate" ; rr:datatype xsd:dateTime ]
    ] , [
        rr:predicate temp:openingtimesdescription ;
        rr:objectMap [ rml:reference "openingtimesdescription" ]
    ] , [
        rr:predicate temp:operatorinformation ;
        rr:objectMap [ rml:reference "operatorinformation" ]
    ] , [
        rr:predicate temp:freeparking ;
        rr:objectMap [ rml:reference "freeparking" ; rr:datatype xsd:integer ]
    ] , [
        rr:predicate temp:totalcapacity ;
        rr:objectMap [ rml:reference "totalcapacity" ; rr:datatype xsd:integer ]
    ] , [
        rr:predicate temp:availablecapacity ;
        rr:objectMap [ rml:reference "availablecapacity" ; rr:datatype xsd:integer ]
    ] , [
        rr:predicate temp:occupation ;
        rr:objectMap [ rml:reference "occupation" ; rr:datatype xsd:integer ]
    ] , [
        rr:predicate temp:latitude ;
        rr:objectMap [ rml:reference "location.lat" ; rr:datatype xsd:double ]
    ] , [
        rr:predicate temp:longitude ;
        rr:objectMap [ rml:reference "location.lon" ; rr:datatype xsd:double ]
    ] .
`

/** From the intermediate model to Mobivoc; a car park is a mv:ParkingLot or a mv:ParkingGarage */
const QUERY = `PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs:   <http://www.w3.org/2000/01/rdf-schema#>
PREFIX dct:    <http://purl.org/dc/terms/>
PREFIX schema: <http://schema.org/>
PREFIX mv:     <http://schema.mobivoc.org/#>
PREFIX temp:   <https://temp.org/ns/advanced-compose#>
PREFIX wgs84:  <http://www.w3.org/2003/01/geo/wgs84_pos#>

CONSTRUCT {
  ?id a mv:ParkingFacility, ?mappedtype ;
    dct:modified ?lastupdate ;
    rdfs:label ?name ;
    schema:openingHoursSpecification [ a schema:OpeningHoursSpecification ; rdfs:label ?openingtimesdescription ] ;
    mv:operatedBy [ a schema:Organization, dct:Agent ; rdfs:label ?operatorinformation ] ;
    mv:price [ a schema:PriceSpecification ; mv:freeOfCharge ?freeparking ] ;
    mv:url ?id ;
    mv:capacity [ a mv:Capacity ; mv:totalCapacity ?totalcapacity ] ;
    mv:capacity [ a mv:RealTimeCapacity ; mv:currentValue ?availablecapacity ] ;
    mv:rateOfOccupancy ?occupation ;
    wgs84:lat ?latitude ;
    wgs84:long ?longitude .
} WHERE {
  ?id rdf:type mv:ParkingFacility ;
    temp:type ?temptype ;
    temp:lastupdate ?lastupdate .
  OPTIONAL { ?id temp:name ?name }
  OPTIONAL { ?id temp:openingtimesdescription ?openingtimesdescription }
  OPTIONAL { ?id temp:operatorinformation ?operatorinformation }
  OPTIONAL { ?id temp:freeparking ?freeparking }
  OPTIONAL { ?id temp:totalcapacity ?totalcapacity }
  OPTIONAL { ?id temp:availablecapacity ?availablecapacity }
  OPTIONAL { ?id temp:occupation ?occupation }
  OPTIONAL { ?id temp:latitude ?latitude }
  OPTIONAL { ?id temp:longitude ?longitude }
  VALUES (?temptype ?mappedtype) {
    ("offStreetParkingGround" mv:ParkingLot)
    ("carPark" mv:ParkingGarage)
  }
}
`

/** Some of the car parks the city publishes, for members to start from */
const CAR_PARKS = [
  { id: 'https://stad.gent/nl/mobiliteit-openbare-werken/parkeren/parkings-gent/parking-vrijdagmarkt', name: 'Vrijdagmarkt', type: 'ParkingGarage', capacity: 625, lat: 51.0566, long: 3.7262 },
  { id: 'https://stad.gent/nl/mobiliteit-openbare-werken/parkeren/parkings-gent/parking-sint-michiels', name: 'Sint-Michiels', type: 'ParkingGarage', capacity: 437, lat: 51.0526, long: 3.7192 },
  { id: 'https://stad.gent/nl/mobiliteit-openbare-werken/parkeren/parkings-gent/parking-het-getouw', name: 'Getouw', type: 'ParkingLot', capacity: 347, lat: 51.0726, long: 3.7148 },
  { id: 'https://stad.gent/nl/mobiliteit-openbare-werken/parkeren/parkings-gent/parking-reep', name: 'Reep', type: 'ParkingGarage', capacity: 489, lat: 51.0534, long: 3.7298 },
  { id: 'https://stad.gent/nl/mobiliteit-openbare-werken/parkeren/parkings-gent/parking-tolhuis', name: 'Tolhuis', type: 'ParkingLot', capacity: 141, lat: 51.0627, long: 3.7262 },
  { id: 'https://stad.gent/nl/mobiliteit-openbare-werken/parkeren/park-and-ride-pr/pr-loopexpo', name: 'P+R Loop-Expo', type: 'ParkingLot', capacity: 600, lat: 51.0248, long: 3.6934 },
]

export const parking: DemoExample = {
  id: 'parking',
  title: 'Parking occupancy',
  offer: 'Create parking demo stream, views, and pipeline',
  description: 'The free spaces in the car parks of Ghent, live from the open data of the city, in Mobivoc',
  icon: 'pi pi-car',
  collection: 'parking-occupancy',
  memberClass: `${mv}ParkingFacility`,
  timestampPath: `${dcterms}modified`,

  eventStream: (serverUrl, collection) => `${prefixLines('ldes', 'dcterms', 'tree', 'sh', 'rdfs', 'mv', 'xsd')}
@prefix server:     <${serverUrl}/> .

server:${collection} a ldes:EventStream ;
    ldes:timestampPath dcterms:modified ;
    ldes:versionOfPath dcterms:isVersionOf ;
    tree:shape [
        a sh:NodeShape ;
        sh:targetClass mv:ParkingFacility ;
        sh:property [
            sh:path rdfs:label ;
            sh:minCount 1
        ] , [
            sh:path dcterms:modified ;
            sh:datatype xsd:dateTime ;
            sh:minCount 1 ;
            sh:maxCount 1
        ] , [
            sh:path mv:rateOfOccupancy ;
            sh:datatype xsd:integer ;
            sh:maxCount 1
        ]
    ] .
`,

  pipeline: (collection, eventStreamUrl) => ({
    name: examplePipelineName(collection),
    description: `Polls the occupancy of the car parks of Ghent every minute, maps it to Mobivoc and ingests version objects in ${collection}`,
    input: {
      name: 'Ldio:HttpInPoller',
      config: { url: GHENT_PARKING_URL, interval: 'PT1M' },
      adapter: { name: 'Ldio:RmlAdapter', config: { mapping: MAPPING } },
    },
    transformers: [
      { name: 'Ldio:SparqlConstructTransformer', config: { query: QUERY } },
      {
        name: 'Ldio:VersionObjectCreator',
        config: {
          'member-type': `${mv}ParkingFacility`,
          'date-observed-property': `<${dcterms}modified>`,
          'generatedAt-property': `${dcterms}modified`,
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
    const carPark = CAR_PARKS[index % CAR_PARKS.length]
    const modified = isoSeconds(time)
    // Busier during the day, quieter at night
    const occupation = Math.round(30 + 25 * Math.sin(((time.getUTCHours() - 8) / 24) * 2 * Math.PI)
      + (index % 7) * 3)
    const available = Math.round(carPark.capacity * (1 - occupation / 100))
    return {
      id: `${carPark.id}/${modified}`,
      isVersionOf: carPark.id,
      content: `${prefixLines('mv', 'schema', 'rdfs', 'dcterms', 'wgs84', 'xsd')}

<${carPark.id}/${modified}>
    a mv:ParkingFacility, mv:${carPark.type} ;
    dcterms:isVersionOf <${carPark.id}> ;
    dcterms:modified "${modified}"^^xsd:dateTime ;
    rdfs:label "${carPark.name}" ;
    schema:openingHoursSpecification [ a schema:OpeningHoursSpecification ; rdfs:label "24/7" ] ;
    mv:operatedBy [ a schema:Organization, dcterms:Agent ; rdfs:label "Mobiliteitsbedrijf Gent" ] ;
    mv:price [ a schema:PriceSpecification ; mv:freeOfCharge 0 ] ;
    mv:url <${carPark.id}> ;
    mv:capacity [ a mv:Capacity ; mv:totalCapacity ${carPark.capacity} ] ,
        [ a mv:RealTimeCapacity ; mv:currentValue ${available} ] ;
    mv:rateOfOccupancy ${occupation} ;
    wgs84:lat "${carPark.lat}"^^xsd:double ;
    wgs84:long "${carPark.long}"^^xsd:double .
`,
    }
  },
}
