---
title: LDES servers
description: Add the LDES servers you run, or start one in Docker, and configure their event streams, views, shapes and DCAT.
summary: Add or start LDES servers; configure event streams, views, shapes and DCAT; browse and ingest.
icon: server
permalink: /ldes-servers/
---

An [OpenLDES server]({{ site.ldes_server_docs }}/) publishes Linked Data Event Streams: it ingests
members in **event streams** and serves them in **views**, fragmented into pages that clients
follow. LDES-admin configures it through its admin API, in forms that validate your input the way
the server does.

## Add an LDES server that runs elsewhere

On the *LDES servers* page (or the dashboard), choose **Add existing LDES server** and give it:

* a **name**, for you;
* its **base URL**, e.g. `https://ldes.example.org`: LDES-admin expects the admin API at
  `/admin/api/v1` and the metrics at `/actuator`;
* whether to **use it as a sandbox**: sandbox servers are where the sandbox offers to
  [ingest the result of a simulation](../sandbox/#ingest-the-result).

LDES-admin never starts or stops a server it did not start. **Remove** only makes LDES-admin forget
it; the server keeps running.

### Running a server for LDES-admin

LDES-admin reaches the admin API, the ingest and fetch endpoints and the actuator of a server on one
base URL, as a server serves them by default. This Docker Compose file runs an LDES server
{{ site.ldes_server_image | split: ':' | last }} the way LDES-admin runs its local ones:

```yaml
services:
  ldes-server:
    image: {{ site.ldes_server_image }}
    ports:
      - "8081:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://ldes-postgres:5432/ldes
      SPRING_DATASOURCE_USERNAME: ldes
      SPRING_DATASOURCE_PASSWORD: ldes
      SPRING_BATCH_JDBC_INITIALIZESCHEMA: always
      # The URL the server publishes its IRIs on: the one its clients use
      LDESSERVER_HOSTNAME: http://localhost:8081
      # The metrics behind the statistics and background processes in LDES-admin
      MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: health,prometheus
      MANAGEMENT_TRACING_ENABLED: "false"
    depends_on:
      - ldes-postgres

  ldes-postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ldes
      POSTGRES_USER: ldes
      POSTGRES_PASSWORD: ldes
```

Add it with the base URL LDES-admin reaches it on: `http://localhost:8081` when LDES-admin runs on
the same machine outside Docker, `http://host.docker.internal:8081` from a container on Docker
Desktop, or `http://ldes-server:8080` when both share a Compose project.

> Set `LDESSERVER_HOSTNAME` to the absolute URL clients reach the server on. An LDIO
> `Ldio:LdesClient` cannot follow the relative IRIs a server publishes without it.
{: .warning }

The [configuration of the server]({{ site.ldes_server_docs }}/) covers the rest: its database,
the fragmentation and maintenance schedules (`LDESSERVER_FRAGMENTATIONCRON`,
`LDESSERVER_MAINTENANCECRON`), security, Kafka ingest and so on.

## Start a local LDES server

**Start local LDES server** runs `{{ site.ldes_server_image }}` in Docker on the machine of the
LDES-admin backend, with a Postgres of its own. Give it a name, and optionally:

* **Use it as a sandbox**, ticked by default;
* **Demos**: the sensor or parking example, created on the new server once it runs (see
  [examples](#start-from-an-example)). Their pipelines run on a local LDIO you pick; without one,
  the sensor data is ingested directly and the demos come without pipelines.

The first start downloads the images, which takes a few minutes; later starts take seconds.

Local servers are made for experimenting:

* All local instances share a Docker network, under a host name made from their name: *Sandbox 2*
  becomes `sandbox-2`. A local server publishes its IRIs on that **internal URL**
  (`http://sandbox-2:8080`), so local LDIOs can follow them. LDES-admin reaches it on a mapped port
  and translates those IRIs, e.g. when you browse a view. Its page shows both URLs.
* They fragment new members every 5 seconds instead of every 30, so results show quickly
  (`ldes-admin.local-runtime.fragmentation-cron`).
* **Stop** removes the server and its database, members and all. Local servers also stop with
  LDES-admin, which forgets them when it starts again.

## The page of a server

Open a server for its tabs: *Event streams*, *Ingest*, *Background processes*, *Shapes* and
*DCAT*. Its header shows its health, its URL (and internal URL), *Refresh*, and *Remove* or *Stop*.

{% include figure.html src="/screenshots/ldes-server.png" alt="The page of an LDES server with its event streams" caption="The event streams of a server, with their statistics" %}

The statistics and background processes are explained in [Monitoring](../monitoring/).

## Event streams

**New event stream** opens the definition of an event stream as a **form** and as **Turtle**, side
by side. Both edit the same definition: a change in the form rewrites the Turtle, and a change in
the Turtle shows in the form once you stop typing. What the Turtle shows is what LDES-admin sends,
so you can also paste a definition you have. *Reset* starts over.

Start from an empty event stream, or from one of the [examples](#start-from-an-example):

```turtle
@prefix ldes:    <https://w3id.org/ldes#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix tree:    <https://w3id.org/tree#> .
@prefix sh:      <http://www.w3.org/ns/shacl#> .
@prefix server:  <http://localhost:8080/> .

server:parcels a ldes:EventStream ;
    ldes:timestampPath dcterms:created ;
    ldes:versionOfPath dcterms:isVersionOf ;
    tree:shape [ a sh:NodeShape ] .
```

### What you can configure

| Field                | Predicate                  | What                                                                                              |
|----------------------|----------------------------|---------------------------------------------------------------------------------------------------|
| Collection name      | the IRI of the stream      | Part of every URL of the event stream: `http://localhost:8080/parcels`                            |
| Timestamp path       | `ldes:timestampPath`       | **Required.** The property that holds the time of a member; time-based views and retention use it |
| Version-of path      | `ldes:versionOfPath`       | **Required.** The property that refers to the state object a member is a version of               |
| Create versions      | `ldes:createVersions`      | Ingest state objects: the server turns each one into a version object (`false` by default)        |
| Version delimiter    | `ldes:versionDelimiter`    | Joins the IRI of the state object and the timestamp into the IRI of a version (`/` by default); only with *Create versions* |
| Skolemization domain | `ldes:skolemizationDomain` | Replaces the blank nodes of members by IRIs in this domain                                        |
| Shape                | `tree:shape`               | The SHACL shape of the members (see [shapes](#shapes))                                            |
| Retention            | `ldes:eventSource`         | When members that no view holds any longer are deleted; without a policy, the event stream keeps every member |
| Kafka source         | `ldes:kafkaSource`         | Reads members from a Kafka topic (`ldes:topic`, `ldes:mimeType`); only for servers with the Kafka ingest module |

Retention adds an event source with a policy, time-based or version-based (see
[retention policies](#retention-policies)):

```turtle
server:parcels a ldes:EventStream ;
    ldes:timestampPath dcterms:created ;
    ldes:versionOfPath dcterms:isVersionOf ;
    ldes:eventSource [
        a ldes:EventSource ;
        ldes:retentionPolicy [
            a ldes:DurationAgoPolicy ;
            tree:value "P30D"^^xsd:duration
        ]
    ] ;
    tree:shape [ a sh:NodeShape ] .
```

### Validated like the server does

Problems show at their field, or under the Turtle; errors keep you from creating the event stream,
warnings don't. LDES-admin checks what the server would refuse, and some things it would accept but
not as you meant:

* The collection name is the *local name* of the IRI of the stream, as Apache Jena reads it: a
  name like `2026-data` would become `data`. Start names with a letter.
* Paths and the skolemization domain are IRIs; *Create versions* is an `xsd:boolean`.
* The definition describes one event stream: statements about resources nothing refers to are
  refused.
* An invalid retention policy makes the server answer with an error, yet create the event stream
  anyway; a Kafka source without the Kafka module fails.

### Start from an example

Two examples come with LDES-admin. Picking one fills in its event stream, with a shape:

* **Sensor observations** (`sensor-observations`): temperature, humidity and particulate matter of
  three stations, modelled with SOSA/SSN, each a version of "the latest observation of this
  property at this station".
* **Parking occupancy** (`parking-occupancy`): the free spaces in the car parks of Ghent, live from
  the open data of the city, in Mobivoc.

*Also create* then offers the rest of the example, none of it unless you tick it: the views
`by-page` and `by-time`, the pipeline `<collection>-ingest` on an LDIO you pick (for a local
server, a local LDIO), and for the sensors, sample data: 36 observations of the last four hours,
sent through the pipeline, or ingested directly without one. Parts that exist already are skipped.

### Delete an event stream

The bin on its row (or *Delete* on its page) deletes the event stream, its views and all its members
from the server. This cannot be undone.

## Views

Open an event stream for its views. Each card shows the page size, the fragmentation, the retention
policies, and how far the view is bucketised and paginated. *Definition* shows the view as the
server describes it; *Browse* opens it in the [browser](#browse-a-view).

**New view** works like a new event stream: a form and Turtle that stay in step. Start from a
template; it decides the fragmentation:

| Template           | Name           | Fragmentation                         |
|--------------------|----------------|---------------------------------------|
| **Pagination**     | `by-page`      | None: pages of a fixed size, in ingest order |
| **Time-based**     | `by-time`      | Buckets per year, month and day       |
| **Geospatial**     | `by-location`  | Map tiles at zoom level 15            |
| **Reference**      | `by-reference` | A bucket per state object             |
| **Recent members** | `recent`       | None, with a retention policy: only members of the last 30 days |

{% include figure.html src="/screenshots/create-view.png" alt="Creating a time-based view, with a form and Turtle side by side" caption="Creating a view: the form and the Turtle stay in step" %}

The form edits the properties of the fragmentation, but does not add, remove or swap it: for
another fragmentation, start from another template. A time-based view on the event stream above:

```turtle
@prefix ldes:    <https://w3id.org/ldes#> .
@prefix tree:    <https://w3id.org/tree#> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .
@prefix server:  <http://localhost:8080/parcels/> .
@prefix dcterms: <http://purl.org/dc/terms/> .

server:by-time tree:viewDescription [
    tree:fragmentationStrategy ( [
        a tree:HierarchicalTimeBasedFragmentation ;
        tree:maxGranularity "day" ;
        tree:fragmentationPath dcterms:created
    ] ) ;
    tree:pageSize "250"^^xsd:int
] .
```

### Page size

`tree:pageSize`: how many members a page holds before the server starts the next one. At least 1;
the server takes 100 when it is left out.

### Fragmentations

A view has one fragmentation at most. It divides the members into buckets, and the server
paginates every bucket.

| Fragmentation  | Property                          | What                                                                               |
|----------------|-----------------------------------|------------------------------------------------------------------------------------|
| **Time-based** (`tree:HierarchicalTimeBasedFragmentation`) | `tree:maxGranularity` | **Required.** The smallest buckets: `year`, `month`, `day`, `hour`, `minute` or `second`; years hold months, months hold days, and so on |
|                | `tree:fragmentationPath`          | **Required.** The property that holds the time of a member, an `xsd:dateTime`; the timestamp path of the stream to start with |
|                | `tree:fragmentationSubjectFilter` | A regular expression: only use values of subjects whose IRI matches                |
| **Geospatial** (`tree:GeospatialFragmentation`) | `tree:maxZoom` | **Required.** The zoom level of the tiles, from 0 (the whole world) to 23; at 15 a tile spans about a kilometre |
|                | `tree:fragmentationPath`          | **Required.** The property that holds the geometry of a member, a WKT literal (`geo:asWKT`) |
|                | `tree:fragmenterSubjectFilter`    | A regular expression: only use values of subjects whose IRI matches                |
| **Reference** (`tree:ReferenceFragmentation`) | `tree:fragmentationPath` | The property whose values make the buckets (`rdf:type` by default; the version-of path of the stream to start with) |
|                | `tree:fragmentationKey`           | The query parameter in the URL of a bucket, e.g. `?version=…` (`reference` by default) |

### Retention policies

At most one of each kind, on a view or on the event source of a stream:

| Kind              | Type                       | Property      | What                                                                 |
|-------------------|----------------------------|---------------|----------------------------------------------------------------------|
| **Time-based**    | `ldes:DurationAgoPolicy`   | `tree:value`  | How long members are kept, an `xsd:duration`: `P30D`, `PT12H`, `P1Y` |
| **Version-based** | `ldes:LatestVersionSubset` | `ldes:amount` | How many versions of each state object are kept, at least 1          |

Members leave a view once a policy no longer keeps them; the server removes them in its
maintenance job ([View retention](../monitoring/#background-processes)). Without a policy, a view
keeps every member.

### Datatypes the server is strict about

The server checks some datatypes exactly. LDES-admin writes them right, and refuses a definition
that has them otherwise:

* `tree:maxZoom` and `ldes:amount` are `xsd:integer`, not `xsd:int`;
* `tree:value` is an `xsd:duration`, without weeks: write `P14D`, not `P2W`;
* `tree:maxGranularity` and `tree:fragmentationKey` are `xsd:string`.

The server also accepts some things that do not work: several fragmentations in one view, unknown
fragmentation types, a page size of 0, a view name of only digits (it breaks listing the views of
the stream). LDES-admin refuses those.

### Delete a view

The bin on its card deletes the view: its pages are removed from the server, the members stay in
the event stream.

## Browse a view

The *Browse* tab walks through a view the way an LDES client does: from the root of the view, along
its relations, to the pages with members.

{% include figure.html src="/screenshots/fragment-browser.png" alt="Browsing a time-based view: relations to the buckets of days, and members" caption="Browsing the pages and members of a view" %}

* **Relations** link to other pages, named after what they hold: *Page 2*, a day like
  *2026-10-24*, a map tile, or a reference. Each shows its conditions (`≥`, `<`, `=`, *contains*)
  on the path of the fragmentation, and roughly how many members lie behind it. Click one to follow
  it.
* **Members** open as Turtle, with their timestamp.
* A page is **immutable** once the server will not change it any more, and **mutable** while it
  can still grow.

The trail at the top leads back to every page you visited. LDES-admin only fetches pages of the
server itself.

## Ingest members

The *Ingest* tab (on the server, or on an event stream) posts a member to the ingest endpoint of an
event stream, as is. Pick the event stream and the format: Turtle, JSON-LD, N-Quads or N-Triples.

The editor starts with a member that fits the event stream: one of its example, when the stream
belongs to an example, or else one made after its name (parcels, addresses, …). *New sample* makes
another one.
Members you ingested show next to the editor; the server fragments them in the background, so
watch the statistics of the event stream.

What the server accepts:

* With `ldes:createVersions false`, a request holds exactly one member: one named subject, with
  other resources only as objects of it (blank nodes belong to it).
* The timestamp of a member is an `xsd:dateTime`.
* Members are checked against the shape of the event stream, but only once the shape was set with
  *Shapes* (or the server restarted): the shape given when creating the stream is not enforced
  before that.

## Shapes

The *Shapes* tab of a server (or *Shape* of an event stream) edits the SHACL shape of an event
stream in Turtle. *Save* replaces it on the server. From then on, the server refuses members that
do not conform to it. Sequence paths work, e.g. `sh:path ( sosa:hasResult qudt:numericValue )`.

## DCAT

The *DCAT* tab describes the server in the Data Catalog Vocabulary:

* **Server catalog**: the `dcat:Catalog` of the server, in Turtle. Keep it a blank node: the server
  names it after its base URL. A server without a catalog starts empty; *Save* publishes it.
* **Published catalog**: what the server serves, its catalog with the datasets and data services of
  the event streams and views that have DCAT.

The DCAT of single event streams and views can be set through the API of LDES-admin
(`…/eventstreams/{collection}/dcat` and `…/views/{view}/dcat`).
