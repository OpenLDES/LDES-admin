# LDES-admin

This application is an administration, monitoring, and sandboxing tool for OpenLDES server and
Linked Data pipelines (LDIO).

First, there's a dashboard that allows you to monitor the status of your OpenLDES server (s)
and pipelines that are running. You can see the status of each pipeline, the number of events
processed, and any errors that may have occurred.

From there, you can open detailed information of an LDES server, add an existing LDES server, or
start/stop a local LDES server.

* list all LDES streams, views, shapes and Data Catalog Vocabulary (DCAT).
* Create new LDES streams, views, shapes and Data Catalog Vocabulary (DCAT).
* Update existing LDES streams, views, shapes and Data Catalog Vocabulary (DCAT).
* Delete existing LDES streams, views, shapes and Data Catalog Vocabulary (DCAT).
* See the status of each LDES stream: how many members are ingested, how many members are
  paginated, ...
* Watch the status of the background processes (compaction, retention, ...)
* Navigate over an LDES stream/view
* Ingest members directly on a stream

From the dashboard, you can also navigate to the Linked Data Interaction Orchestrators that
are configured. You can add an existing LDIO, or start/stop a local LDIO.

You can see the status of each pipeline, the number of events processed, and any errors that may
have occurred.
You can also manage pipelines: create, update, delete, halt, resume pipelines.

There's also a sandbox where you can test your (sandbox) LDES streams and views. You can ingest
members directly on a stream, and see how they are processed by the OpenLDES server. You can also
create and modify a pipeline, and see how it processes the members. Or, you define a pipeline, put a
member on its input, and see how this member gets transformed and flows through the pipeline. You
can also see the output of the pipeline, and you can ingest this output to a local (sandbox) LDES
server.

# Technology

This project uses Spring boot in its backend. Everything is created in a hexagonal architecture
(see https://www.happycoders.eu/software-craftsmanship/hexagonal-architecture-spring-boot/
and https://github.com/SvenWoltmann/hexagonal-architecture-java/tree/with-spring-boot).
All components are tested. There are also integration tests, which make use of testcontainers to
start a Postgres server, an LDES and/or an LDIO server.

All configuration, ... is stored in a Postgres server using JPA. The database is created and kept up
to date by running Flyway.

The frontend is built with Typescript, Vue, PrimeVue and Vue Flow. This frontend is also tested with
Jest and Cypress, and built with pnpm.


