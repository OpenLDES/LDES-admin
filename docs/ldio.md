---
title: LDIO
description: Add or start Linked Data Interaction Orchestrators, and set up and manage their pipelines.
summary: Add or start LDIOs; run, halt, edit and delete pipelines; what a pipeline is made of.
icon: pipeline
permalink: /ldio/
---

A [Linked Data Interaction Orchestrator]({{ site.ldio_docs }}/) (LDIO) runs **pipelines**: each
takes data in, turns it into linked data, transforms it, and sends it on. Pipelines feed LDES
servers (an `Ldio:HttpOut` to the ingest endpoint of an event stream) and consume them (an
`Ldio:LdesClient` that follows an event stream). LDES-admin manages them through the pipeline API of
the LDIO, and designs them in the [sandbox](../sandbox/).

## Add an LDIO that runs elsewhere

On the *LDIOs* page (or the dashboard), choose **Add existing LDIO** and give it a **name**, its
**base URL** (the pipeline API is expected at `/admin/api/v1`, metrics at `/actuator`) and whether
to **use it as a sandbox**. LDES-admin never starts or stops an LDIO it did not start: **Remove**
only makes LDES-admin forget it, and its pipelines keep running.

### Running an LDIO for LDES-admin

This Docker Compose file runs an LDIO {{ site.ldio_image | split: ':' | last }} the way LDES-admin
runs its local ones, with the metrics LDES-admin reads:

```yaml
services:
  ldio:
    image: {{ site.ldio_image }}
    ports:
      - "8082:8080"
    environment:
      # The metrics behind members in and out in LDES-admin
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: health,prometheus
```

Pipelines you deploy through LDES-admin live in the LDIO. See the
[LDIO documentation]({{ site.ldio_docs }}/) to give it pipelines at startup, persist the state of
LDES clients, or secure it.

## Start a local LDIO

**Start local LDIO** runs `{{ site.ldio_image }}` in Docker on the machine of the LDES-admin
backend. Give it a name, tick **Use it as a sandbox** or not, and optionally pick **demos**: their
pipelines run on the new LDIO and ingest in the event streams of a local LDES server you choose, so
start a local LDES server first. See [examples](#examples).

Like a local LDES server, a local LDIO joins the Docker network of the local instances under a host
name made from its name. There it reaches local LDES servers on their internal URL, e.g.
`http://sandbox-ldes-server:8080/sandbox-parcels`. **Stop** removes the LDIO and its pipelines.

## The pipelines of an LDIO

Open an LDIO for its pipelines: their components, status, and members in and out (see
[Monitoring](../monitoring/#pipelines) for what they mean).

{% include figure.html src="/screenshots/ldio.png" alt="The pipelines of an LDIO, with actions to halt, edit and delete them" caption="The pipelines of an LDIO" %}

| Action      | What happens                                                                         |
|-------------|--------------------------------------------------------------------------------------|
| **Halt**    | Only for a running pipeline. It stops taking data in, until you resume it             |
| **Resume**  | Only for a halted pipeline                                                           |
| **Edit**    | Opens the pipeline in the sandbox; *Deploy* replaces it on the LDIO                   |
| **Delete**  | LDIO stops the pipeline gracefully and removes it; its persisted state (e.g. how far an LDES client got) is lost |
| Expand ›    | The pipeline as a diagram, and its YAML                                              |

**New pipeline** opens an empty canvas in the [sandbox](../sandbox/), from where you deploy it.

LDIO has no way to change a pipeline: replacing one deletes it, waits until LDIO has stopped it (up
to `ldes-admin.ldio.delete-timeout`, 30 seconds), and creates it again. The pipeline starts from
scratch, so an LDES client in it replicates its event stream from the start again.

### Component catalog

The *Component catalog* tab lists the inputs, adapters, transformers and outputs the LDIO has
installed, with a link to their documentation. Components LDES-admin does not know show as
*custom component*.

## What a pipeline is made of

A pipeline is a chain, in this order:

| Part             | How many            | What it does                                              |
|------------------|---------------------|-----------------------------------------------------------|
| **Input**        | one                 | Receives or fetches data, and starts the pipeline         |
| **Adapter**      | none or one         | Turns the raw data of the input into linked data. Inputs that receive other data than RDF need one: `Ldio:HttpIn`, `Ldio:HttpInPoller`, `Ldio:KafkaIn` and `Ldio:AmqpIn` |
| **Transformers** | none or more        | Change the linked data model, one after the other         |
| **Outputs**      | one or more         | Send the model to its destination; every output gets every model |

LDIO takes a pipeline as YAML (or JSON). This is the pipeline of the
[sandbox](../sandbox/#the-sandbox-environment): it receives RDF over HTTP and ingests it in an
event stream.

```yaml
name: sandbox-ingest
description: Receives members over HTTP and ingests them in the sandbox-parcels event stream of the sandbox LDES server
input:
  name: Ldio:HttpIn
  adapter:
    name: Ldio:RdfAdapter
outputs:
  - name: Ldio:HttpOut
    config:
      endpoint: http://sandbox-ldes-server:8080/sandbox-parcels
```

And one with transformers, from the templates of the sandbox: it adds a JSON-LD context to parcel
updates in plain JSON, turns their GeoJSON geometry into WKT, makes version objects of them, and
both ingests and logs them.

```yaml
name: parcels-to-ldes
description: Parcel updates from a JSON API, as version objects in the sandbox LDES
input:
  name: Ldio:HttpIn
  adapter:
    name: Ldio:JsonToLdAdapter
    config:
      context: |-
        {
          "@context": {
            "@vocab": "https://data.vlaanderen.be/ns/perceel#",
            "xsd": "http://www.w3.org/2001/XMLSchema#",
            "geojson": "https://purl.org/geojson/vocab#",
            "type": "@type",
            "lastModified": { "@type": "xsd:dateTime" },
            "geometry": "geojson:geometry",
            "Polygon": "geojson:Polygon",
            "coordinates": { "@id": "geojson:coordinates", "@container": "@list" }
          }
        }
transformers:
  - name: Ldio:GeoJsonToWktTransformer
  - name: Ldio:VersionObjectCreator
    config:
      member-type: https://data.vlaanderen.be/ns/perceel#Perceel
      date-observed-property: <https://data.vlaanderen.be/ns/perceel#lastModified>
outputs:
  - name: Ldio:HttpOut
    config:
      endpoint: http://sandbox-ldes-server:8080/sandbox-parcels
      rdf-writer:
        content-type: text/turtle
  - name: Ldio:ConsoleOut
```

LDIO reads configuration keys ignoring case, `-` and `_`: `member-type`, `memberType` and
`membertype` are the same key. It returns them normalised (`membertype`,
`rdfwriter.contenttype`), so that is how they show in deployed pipelines.

### Components

The components of a standard LDIO, and what they do. Each links to its documentation, with all its
properties; the [sandbox](../sandbox/#configure-a-component) has a form for every one of them.

| Kind        | Component                                                                                         | What it does                                              |
|-------------|---------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
| Input       | [`Ldio:HttpIn`]({{ site.ldio_docs }}/ldio-inputs/ldio-http-in)                                    | Listens for data posted to `/{pipeline name}`             |
| Input       | [`Ldio:HttpInPoller`]({{ site.ldio_docs }}/ldio-inputs/ldio-http-in-poller)                       | Polls a URL on an interval or cron schedule               |
| Input       | [`Ldio:LdesClient`]({{ site.ldio_docs }}/ldio-inputs/ldio-ldes-client)                            | Replicates and synchronises an LDES                       |
| Input       | [`Ldio:LdesClientConnector`]({{ site.ldio_docs }}/ldio-inputs/ldio-ldes-client-connector)         | LDES client behind an Eclipse Dataspace Connector         |
| Input       | [`Ldio:KafkaIn`]({{ site.ldio_docs }}/ldio-inputs/ldio-kafka-in)                                  | Consumes messages from Kafka topics                       |
| Input       | [`Ldio:AmqpIn`]({{ site.ldio_docs }}/ldio-inputs/ldio-amqp-in)                                    | Consumes messages from an AMQP queue                      |
| Adapter     | [`Ldio:RdfAdapter`]({{ site.ldio_docs }}/ldio-adapters/ldio-rdf-adapter)                          | Parses RDF in any serialisation Apache Jena supports      |
| Adapter     | [`Ldio:JsonToLdAdapter`]({{ site.ldio_docs }}/ldio-adapters/ldio-json-to-json-ld)                 | Adds a JSON-LD context to plain JSON                      |
| Adapter     | [`Ldio:RmlAdapter`]({{ site.ldio_docs }}/ldio-adapters/ldio-rml-adapter)                          | Maps JSON, CSV or XML to RDF with an RML mapping          |
| Adapter     | [`Ldio:NgsiV2ToLdAdapter`]({{ site.ldio_docs }}/ldio-adapters/ldio-ngsiv2-to-ld)                  | Converts NGSI-v2 entities into NGSI-LD                    |
| Transformer | [`Ldio:VersionObjectCreator`]({{ site.ldio_docs }}/ldio-transformers/ldio-version-object-creator) | Turns a state object into a version object                |
| Transformer | [`Ldio:VersionMaterialiser`]({{ site.ldio_docs }}/ldio-transformers/ldio-version-materializer)    | Turns a version object back into a state object           |
| Transformer | [`Ldio:SparqlConstructTransformer`]({{ site.ldio_docs }}/ldio-transformers/ldio-sparql-construct) | Reshapes the model with a SPARQL CONSTRUCT query          |
| Transformer | [`Ldio:GeoJsonToWktTransformer`]({{ site.ldio_docs }}/ldio-transformers/ldio-geojson-to-wkt)      | Replaces GeoJSON geometries with WKT literals             |
| Transformer | [`Ldio:SkolemisationTransformer`]({{ site.ldio_docs }}/ldio-transformers/ldio-skolemisation-transformer) | Replaces blank nodes with well-known IRIs          |
| Transformer | [`Ldio:ChangeDetectionFilter`]({{ site.ldio_docs }}/ldio-transformers/ldio-change-detection-filter) | Drops members that did not change since the last time   |
| Transformer | [`Ldio:HttpEnricher`]({{ site.ldio_docs }}/ldio-transformers/ldio-http-enricher)                  | Adds the response of an HTTP call to the model            |
| Output      | [`Ldio:ConsoleOut`]({{ site.ldio_docs }}/ldio-outputs/ldio-console-out)                           | Writes the model to the LDIO log                          |
| Output      | [`Ldio:HttpOut`]({{ site.ldio_docs }}/ldio-outputs/ldio-http-out)                                 | Posts the model to a URL, e.g. an LDES ingest endpoint    |
| Output      | [`Ldio:HttpSparqlOut`]({{ site.ldio_docs }}/ldio-outputs/ldio-http-sparql-out)                    | Writes the model to a SPARQL endpoint                     |
| Output      | [`Ldio:RepositorySink`]({{ site.ldio_docs }}/ldio-outputs/ldio-repository-sink)                   | Commits the model to an RDF4J repository                  |
| Output      | [`Ldio:KafkaOut`]({{ site.ldio_docs }}/ldio-outputs/ldio-kafka-out)                               | Produces the model to a Kafka topic                       |
| Output      | [`Ldio:AmqpOut`]({{ site.ldio_docs }}/ldio-outputs/ldio-amqp-out)                                 | Sends the model to an AMQP queue                          |
| Output      | [`Ldio:LdioRdbOut`]({{ site.ldio_docs }}/ldio-outputs/ldio-rdb-out)                               | Inserts rows into a database table via a SPARQL SELECT    |
| Output      | [`Ldio:NoopOut`]({{ site.ldio_docs }}/ldio-outputs/index)                                         | Discards the model; handy while testing                   |

## Send data to a pipeline

A pipeline with an `Ldio:HttpIn` input listens on `/{pipeline name}` of the LDIO. Post anything its
adapter reads, with the right content type; LDIO answers `202 Accepted`:

```shell
curl -X POST http://localhost:8082/my-pipeline \
  -H 'Content-Type: text/turtle' \
  --data-binary @member.ttl
```

In LDES-admin, the [simulation of the sandbox](../sandbox/#send-the-member-to-a-real-pipeline) sends
its input member to such a pipeline.

## Examples

The demos of LDES-admin each come with a pipeline, named `<collection>-ingest`, that feeds their
event stream:

* **Sensor observations**: `Ldio:HttpIn` + `Ldio:JsonToLdAdapter` → `Ldio:VersionObjectCreator` →
  `Ldio:HttpOut`. It receives readings as plain JSON, which an inline JSON-LD context maps onto SOSA,
  and makes a version object of every observation. Send it the sample data of the example, one JSON
  array of 36 readings.
* **Parking occupancy**, after the
  [advanced conversion](https://github.com/OpenLDES/Onboarding-Example/tree/main/advanced-conversion)
  onboarding example of OpenLDES: `Ldio:HttpInPoller` + `Ldio:RmlAdapter` →
  `Ldio:SparqlConstructTransformer` → `Ldio:VersionObjectCreator` → `Ldio:HttpOut`. It polls the
  occupancy of the car parks of Ghent every minute and publishes them in Mobivoc. It brings its own
  data.

Pick them when you start a local LDIO or LDES server, or when you create an event stream (see
[LDES servers](../ldes-servers/#start-from-an-example)).

## Good to know

What we learned running pipelines against LDES servers:

* `Ldio:LdesClient` cannot follow relative IRIs. The LDES server must publish absolute ones: set its
  `LDESSERVER_HOSTNAME` (local servers do).
* `Ldio:JsonToLdAdapter` takes an inline context wrapped as `{"@context": {...}}`, makes a model of
  every object of a JSON array, and writes numbers it types as `xsd:double` in their canonical form
  (`82` becomes `"8.2E1"`).
* `Ldio:VersionObjectCreator` versions the first subject of the member type, and takes only an
  `xsd:dateTime`-typed literal as the observed time. It puts the time in the IRI of the version: use
  `/` as delimiter when the IRIs of the state objects contain a `#`.
* `Ldio:GeoJsonToWktTransformer` needs the geometry in the GeoJSON-LD vocabulary
  (`https://purl.org/geojson/vocab#`).
* `Ldio:RmlAdapter` makes a model of every named graph its mapping produces (`rr:graphMap`).
* Deleting a pipeline takes a moment: LDIO answers right away and stops the pipeline in the
  background. LDES-admin waits until it is gone.
