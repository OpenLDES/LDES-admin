---
title: Sandbox
description: Design LDIO pipelines on a canvas, run members through them in your browser, and try them out on a local LDES server and LDIO.
summary: Design pipelines on a canvas, simulate them, deploy them, and run a local sandbox environment.
icon: sandbox
permalink: /sandbox/
---

The sandbox is where you design and try out pipelines before they go anywhere near production. It
has three parts:

* the **designer**: a canvas to draw a pipeline on, with a form for every component;
* the **simulation**: runs a member through the pipeline in your browser, step by step;
* the **sandbox environment**: a local LDES server and LDIO in Docker that work together out of the
  box, to deploy pipelines on.

Open it with **Sandbox** in the navigation, or **Open sandbox** on the dashboard.

{% include figure.html src="/screenshots/sandbox.png" alt="The sandbox: the components on the left, the pipeline on the canvas, and the configuration of a component on the right" caption="Designing a pipeline: components, canvas, and the form of the selected component" %}

## The sandbox environment

The sandbox environment is a local LDES server, *Sandbox LDES server*, and a local LDIO,
*Sandbox LDIO*, prepared to work together. Start it with **Start sandbox** on the dashboard, or
under **Sandbox environment** in the toolbar of the sandbox.

LDES-admin starts both in Docker, on the machine of its backend, and prepares them:

* the LDES server gets the event stream `sandbox-parcels` (timestamp path `prov:generatedAtTime`),
  with a paginated view `by-page` and a time-based view `by-time`, both with pages of 10 members;
* the LDIO gets the pipeline `sandbox-ingest`: it receives RDF over HTTP (`Ldio:HttpIn` +
  `Ldio:RdfAdapter`) and ingests it in `sandbox-parcels` (`Ldio:HttpOut`).

Inside the sandbox, the LDIO reaches the event stream on
`http://sandbox-ldes-server:8080/sandbox-parcels`. The templates of the sandbox, and the components
that write to or read from an LDES, point there by default, so a pipeline you deploy on the sandbox
LDIO works with the sandbox LDES server right away:

* write to it with an `Ldio:HttpOut` to `http://sandbox-ldes-server:8080/sandbox-parcels`;
* read from it with an `Ldio:LdesClient` on a view, e.g.
  `http://sandbox-ldes-server:8080/sandbox-parcels/by-page`.

| State              | Meaning                                                                          |
|--------------------|----------------------------------------------------------------------------------|
| **Stopped**        | Neither runs                                                                     |
| **Starting…**      | LDES-admin starts and prepares them; the first start downloads the Docker images, which takes a few minutes |
| **Running**        | Both run; the panel links to both, and to the event stream                       |
| **Partly running** | One of both runs; *Start sandbox* starts what is missing                         |

Starting again is harmless: what is prepared already stays, and the pipeline is pointed at the
current LDES server again if needed. **Stop** removes both, with every member and pipeline in them.
The sandbox also stops with the LDES-admin backend.

> Any LDES server or LDIO can be marked *Use it as a sandbox* when you add or start it. The
> simulation offers to ingest its result in sandbox LDES servers, and *Deploy* picks a sandbox LDIO
> by default.
{: .tip }

## Design a pipeline

The sandbox has three columns:

* **Components** (left): every LDIO component, grouped by kind: inputs, adapters, transformers and
  outputs. Search them by name. Drag one onto the canvas, or double-click it (or press
  <kbd>Enter</kbd>) to add it below the others.
* **The canvas** (middle): the pipeline, from top to bottom. Connect the bottom of one component to
  the top of the next. *Tidy up the layout* lines everything up; the controls zoom and fit, and the
  minimap shows where you are.
* **The inspector** (right): the selected component, or the whole pipeline when nothing is selected.

### Start from a template

*Templates* in the toolbar replaces the canvas with an example, or an empty canvas. Each comes with
a member to simulate:

| Template                                   | What it does                                                                     |
|--------------------------------------------|----------------------------------------------------------------------------------|
| **Parcels API → versioned LDES**           | Adds a JSON-LD context to parcel updates, converts their geometry to WKT, creates version objects and ingests them in the sandbox LDES |
| **HTTP in → console**                      | The basic example from the LDIO documentation: parses posted RDF and logs it    |
| **Replicate an LDES into a triple store**  | Follows the sandbox LDES, turns version objects back into state objects, drops unchanged ones and stores the rest |
| **Mobility hindrances → SPARQL store**     | GeoJSON points to WKT and blank nodes to stable IRIs, before writing to a SPARQL endpoint |

The sandbox opens with the first one. A pipeline on the canvas is replaced only after you confirm.

### Connections

The canvas only lets you draw pipelines LDIO can run:

* an input starts the pipeline; there is one;
* an adapter can only follow the input;
* transformers form a single chain;
* outputs end the pipeline; there can be several;
* a component cannot connect to itself, and pipelines cannot loop.

Select a component or connection and press <kbd>Delete</kbd> or <kbd>Backspace</kbd> to remove it.

### Configure a component

Click a component to configure it in the inspector, as a **form** and as **YAML**. Both edit the
same configuration: a change in the form shows in the YAML right away, and the form follows the
YAML as you type.

The form knows every property of the component, after the LDIO documentation (linked at the top):

* its type, with an input to match: text, URL, list of URLs, IRI, property path, number, yes/no,
  ISO 8601 duration, cron expression, a choice (such as the RDF formats Apache Jena writes), a
  JSON-LD context, HTTP headers;
* whether it is required, and its default;
* the group it belongs to (authentication, retries, state, …), and when it applies: the API key
  only shows for API key authentication.

Problems show at their field: missing required properties, invalid values, rules between
properties, and keys that are no property of the component. Keys are matched the way LDIO matches
them, so `sourceFormat`, `source-format` and `sourceformat` are the same.

In the inspector, *Component* swaps the component for another of the same kind; *Duplicate* and
*Remove* do what they say.

### The whole pipeline

Click the empty canvas to see the pipeline in the inspector:

* its **name** (the name it gets on the LDIO, and the path of an `Ldio:HttpIn`) and **description**;
* **validation**: every problem of the pipeline; click one to select the component it is about;
* the **pipeline YAML**, as LDIO receives it. *Copy* copies it; *Edit* opens it to change by hand,
  and *Apply to canvas* redraws the pipeline from it.

The badge next to the name in the toolbar says whether the pipeline is valid. **Errors** keep it
from being deployed; **warnings** don't. A pipeline with errors in its configuration can still be
simulated; only errors in its structure (e.g. no input) stop that.

## Simulate

**Run** opens the simulation at the bottom of the canvas and runs the input member through the
pipeline. LDES-admin runs it on the components LDIO itself is built on, so every step shows what
LDIO would make of the member.

{% include figure.html src="/screenshots/simulation.png" alt="The simulation: the input member, the steps it went through, and the model after the selected step" caption="Running a member through the pipeline, step by step" %}

The simulation has three columns:

* **Input member**: what the input receives, as JSON, Turtle, N-Quads, N-Triples, JSON-LD or CSV.
  Choosing another RDF format converts the member to it (JSON-LD with its context in the
  document: a remote context cannot be fetched). The refresh button brings back the sample member
  of the template. A pipeline that starts with an
  HTTP In Poller has buttons to [fetch and poll its URL](#input-from-an-http-poller) instead.
* **Steps**: every component the member went through, with how long it took and what it did:
  passed on, filtered out, or stopped with an error. Components it did not reach are listed as such.
* **After …**: what the step you select made, in Turtle; for an output, what it sends. A step can
  make several models: an adapter makes one per object of a JSON array, or per named graph of an
  RML mapping. Page through them with the arrows (*Model 2 of 14*).

The bar above sums it up: *Delivered to 2 outputs*, *Filtered out by Change Detection Filter*, or
*Stopped at Version Object Creator*. *Reset* clears the result. Click the bar to fold the simulation
away while you design.

### Dry run or execute

Outputs run dry: they show what they would send (*Would POST text/turtle to …*) without sending
it. Switch on **Execute outputs** in the bar to have them really send it: HTTP Out posts the
member, HTTP SPARQL Out updates its triple store, the repository sink adds it to its repository.
Kafka Out, AMQP Out and the database output always run dry: LDES-admin has no clients for them.

An output pointing to the sandbox LDES server on its host name
(`http://sandbox-ldes-server:8080/…`) reaches it when executed: the member is ingested.

> Running a pipeline makes LDES-admin fetch what its components fetch (JSON-LD contexts, SPARQL
> `SERVICE` calls, the HTTP enricher), and executed outputs send wherever they point. [Secure the
> API](../installation/#securing-the-api) wherever LDES-admin reaches what its users should not.
{: .tip }

### Input from an HTTP poller

When the pipeline starts with an `Ldio:HttpInPoller`, the input member can be what its URL answers
right now, as the poller would receive it:

* **Fetch** (the cloud button above the input member) gets the URL once and puts the response
  body in the input, with its content type. Change it if you like, then **Run**. With several URLs,
  pick one.
* **Poll** (the button next to it) fetches on the schedule of the component and runs every
  response through the pipeline, the way the deployed pipeline would: on its `cron` expression (a
  Spring cron expression, in the time zone of your browser), or right away and then every
  `interval`. A poll that fails is reported, and polling goes on. The line above the input shows
  the schedule, the number of polls and when the next one is; **Stop polling** (also in the bar of
  the simulation) ends it. Polling also stops when you leave the sandbox.

The request is the one LDIO makes: a GET of every URL, with the HTTP headers of the component, its
API key, or a token from its OAuth2 client credentials. The response must have a `Content-Type`
header, as LDIO hands it to the adapter. URLs on the host names of the sandbox
(`http://sandbox-ldes-server:8080/…`) work too.

> The LDES-admin backend fetches the URL for you, as most APIs don't let a browser read them. So
> whoever can use its API can make LDES-admin fetch any URL it reaches: [secure the
> API](../installation/#securing-the-api) wherever LDES-admin reaches what its users should not.
{: .tip }

### What runs

Every component runs as in LDIO, with the same configuration:

* adapters: RDF, JSON to JSON-LD, RML and NGSI-v2;
* transformers: SPARQL construct (with LDI's geospatial functions), version object creator,
  version materialiser, GeoJSON to WKT, skolemisation, change detection filter and HTTP enricher;
* outputs: see [dry run or execute](#dry-run-or-execute).

The input member is what the input receives; for an LDES client, it is a member in RDF. The change
detection filter remembers the members it saw while you design the pipeline, as a running pipeline
does; it forgets them when you load another pipeline or change its configuration. A component gets
30 seconds at most. To see what LDIO itself makes of a member, deploy the pipeline on the sandbox
LDIO and [send the member to it](#send-the-member-to-a-real-pipeline).

### Ingest the result

Once the member is delivered, **Ingest** (above the output) ingests the result in an event stream of
a sandbox LDES server, as Turtle: every member, when the pipeline made several. Then [browse](../ldes-servers/#browse-a-view) the event stream to
see the member on its pages.

### Send the member to a real pipeline

The send button above the input member posts it, as it is, to a deployed pipeline with an
`Ldio:HttpIn` input, which really processes it. Pick the LDIO and the pipeline: where this pipeline
is deployed, or else `sandbox-ingest` of the sandbox, is picked for you.

## Deploy

**Deploy** (once the pipeline has no errors) deploys the pipeline on an LDIO you pick; sandbox LDIOs
are marked. When the LDIO has a pipeline of the same name, deploying replaces it: LDIO cannot change
a pipeline, so LDES-admin deletes it and creates it again, and the pipeline starts from scratch.
LDES-admin then opens the LDIO, where the pipeline [shows](../ldio/#the-pipelines-of-an-ldio) with
its status and throughput.

### Edit a deployed pipeline

On the page of an LDIO, the pencil of a pipeline opens it in the sandbox, with a sample member that
fits its input. Hover its name in the toolbar to see that it is deployed on that LDIO; *Deploy*
replaces it there. *New pipeline* on the page of an LDIO opens an empty canvas.

## In demo mode

In [demo mode](../installation/#check-that-it-runs) the sandbox works the same, on sample data in
your browser: the sandbox environment starts without Docker, and its pipelines pass members on to
the event streams of the demo LDES servers. Nothing reaches a real LDIO. The simulation runs in your
browser and approximates the components: SPARQL queries are not evaluated and RML mappings are
approximated. Executed, an HTTP output to a demo LDES server counts the member there. Without a
backend, your browser fetches the URL of an HTTP poller itself, which only works for APIs that allow it (CORS),
such as the open data of the city of Ghent.

## Tips

* Pipelines on the sandbox LDIO reach other local instances on their host name, never on
  `localhost`: `http://sandbox-ldes-server:8080/…`, as in the templates.
* Keep the simulation open while you configure: run again after a change to see its effect on every
  step.
* Before you deploy a pipeline elsewhere, check the URLs in its outputs and inputs: the sandbox host
  names only resolve inside the Docker network of the local instances.
