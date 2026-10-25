/**
 * Prefixes of the vocabularies that show up in LDES definitions and members.
 */
export const PREFIXES: Record<string, string> = {
  rdf: 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
  ldes: 'https://w3id.org/ldes#',
  tree: 'https://w3id.org/tree#',
  dcterms: 'http://purl.org/dc/terms/',
  prov: 'http://www.w3.org/ns/prov#',
  sh: 'http://www.w3.org/ns/shacl#',
  xsd: 'http://www.w3.org/2001/XMLSchema#',
  dcat: 'http://www.w3.org/ns/dcat#',
  geo: 'http://www.opengis.net/ont/geosparql#',
  sosa: 'http://www.w3.org/ns/sosa/',
  perceel: 'https://data.vlaanderen.be/ns/perceel#',
  adres: 'https://data.vlaanderen.be/ns/adres#',
  mobiliteit: 'https://data.vlaanderen.be/ns/mobiliteit#',
}

/** perceel:Perceel → https://data.vlaanderen.be/ns/perceel#Perceel */
export function expand(prefixed: string): string {
  const [prefix, local] = prefixed.split(':')
  return PREFIXES[prefix] ? PREFIXES[prefix] + local : prefixed
}

/** https://data.vlaanderen.be/ns/perceel#Perceel → perceel:Perceel; unknown IRIs as <iri> */
export function compact(iri: string): string {
  const entry = Object.entries(PREFIXES).find(([, namespace]) => iri.startsWith(namespace))
  return entry ? `${entry[0]}:${iri.slice(entry[1].length)}` : `<${iri}>`
}

/** Turtle @prefix declarations for the given prefixes */
export function prefixLines(...prefixes: string[]): string {
  return prefixes.map((prefix) => `@prefix ${`${prefix}:`.padEnd(12)}<${PREFIXES[prefix]}> .`)
    .join('\n')
}
