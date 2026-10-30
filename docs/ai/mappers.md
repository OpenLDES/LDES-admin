# Mapping representations

Keep conversion where the external representation enters or leaves the hexagon.

| Boundary                  | Existing pattern                                             | Must not leak inward                               |
|---------------------------|--------------------------------------------------------------|----------------------------------------------------|
| HTTP to use case          | Request records to input-port command records in controllers | Servlet, Spring HTTP and validation types          |
| Domain to HTTP            | `*WebModel.fromDomain(...)` factories                        | JPA entities or framework response wrappers        |
| JPA to domain             | Package-local mapping helpers such as `InstanceMapper`       | Entities, lazy proxies and persistence annotations |
| Remote RDF/JSON to domain | LDES/LDIO outbound client parsing helpers                    | Jena models, Jackson nodes and remote wire quirks  |
| Frontend wire data        | Typed API implementation to shared frontend models           | Transport-specific logic in Vue components         |

`JpaInstanceRepository` demonstrates `InstanceMapper.toJpaEntity` and `toModel`.
`InstanceController` demonstrates `InstanceWebModel.fromDomain`. Follow those conventions; do not
add MapStruct, builders, a universal mapper service or a static mapper class for every record.

Mapping should not query a database, call another service or perform remote I/O. Preserve
identifiers, timestamps, enum semantics, optional values and registered/local instance fields.
Use existing domain factories so restored data still respects invariants.

Do not return `null` automatically for every invalid input. Respect the contract: an absent value
may be optional; a required malformed value should fail explicitly. Avoid catching parsing errors
and returning an empty model, which makes bad upstream data look like success.

For RDF, test graph meaning, datatypes, blank nodes and lists, not incidental prefix names or
serialization order. Preserve repeated relations and multiple output models. For URL fields,
preserve the distinction between an instance's reachable URL and the IRIs its server publishes.
See [OpenLDES behaviour](openldes.md) before normalizing identifiers or remote definitions.
