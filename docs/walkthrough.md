---
title: Parking occupancy walkthrough
description: >-
  Start a local LDES server and LDIO, simulate the Ghent parking feed, ingest the result, and browse
  the event stream.
summary: Follow live parking data from the Ghent API into an LDES, then browse its views.
icon: pipeline
permalink: /walkthrough/
---

Follow a parking update all the way from Ghent's open data to a browsable LDES. You will start a
local server and LDIO, use the example pipeline to fetch and transform live data, simulate it
safely, then ingest the result and explore the views.

> **Before you begin:** LDES-admin must be able to start Docker containers. See
> [installation](../installation/) if you have not run it yet. The screenshots below use the dark
> theme in demo mode to show the screens; follow the steps in **Production** mode to use local
> containers and real parking data.
{: .tip }

## 1. Start a local LDES server

Open **LDES servers** and choose **Start local LDES server**. Name it, leave **Use it as a
sandbox** selected, and start it. The first start downloads the server and database images. Wait
until its status is **Up** before continuing.

{% include figure.html src="/screenshots/ldes-servers.png" alt="The LDES servers page, including the Start local LDES server action" caption="Start a local server from the LDES servers page" %}

Open the server and choose **New event stream**. Select the **Parking occupancy** example and
create `parking-occupancy`. The example sets the timestamp path to `dcterms:modified`, versions
each car park by `dcterms:isVersionOf`, and validates the Mobivoc members the pipeline will
produce. Create the example's `by-page` and `by-time` views as well; leave its pipeline for the
LDIO step.

## 2. Start the parking pipeline

Open **LDIOs**, choose **Start local LDIO**, and select the **Parking occupancy** demo. Point it at
the local LDES server you just started. LDES-admin creates the example pipeline on that LDIO:
`HttpInPoller` fetches the city's JSON, `RmlAdapter` and `SparqlConstructTransformer` map it to
Mobivoc, `VersionObjectCreator` makes version objects, and `HttpOut` targets
`parking-occupancy`.

{% include figure.html src="/screenshots/ldio.png" alt="An LDIO page showing a running HTTP poller pipeline and its component chain" caption="Open the new pipeline from the local LDIO" %}

On the pipeline row, choose **Edit** to open it in the sandbox. The canvas shows the pipeline;
select **HTTP In Poller** in the canvas or the component list to edit its configuration.

## 3. Fetch and simulate one update

The example polls every minute with an interval. In the HTTP In Poller form, replace **interval**
with **cron** and enter this Spring cron expression to run once a minute:

```text
0 * * * * *
```

The form and YAML stay in sync. You do not need to deploy this edit for the simulation. Choose
**Fetch** above the input member to request the configured URL once and load its current response
into the editor. The RML adapter makes a model for each car park in the response. Select **Run**
to see each transformation and the result.

{% include figure.html src="/screenshots/sandbox.png" alt="The sandbox canvas with a pipeline and component configuration" caption="Edit the poller and inspect the pipeline on the sandbox canvas" %}

{% include figure.html src="/screenshots/simulation.png" alt="The simulation showing the input member, pipeline steps, and output" caption="A simulation shows the input, each step, and what the output would send" %}

Outputs are dry by default: the simulation does not post to the LDES. Select **Ingest** above the
HTTP Out result and choose the local server and `parking-occupancy` stream. This sends the
simulated result to that stream. The city feed changes over time, so a later fetch may produce
different occupancy values.

## 4. Browse the stream

Return to the local LDES server and open `parking-occupancy`. Its **Views** tab lists `by-page` and
`by-time`. Choose **Browse** on `by-time` to follow the time buckets to a page; expand a member to
read its Mobivoc data. Local servers fragment members every five seconds, so give the background
jobs a moment to bucketise and paginate new data.

{% include figure.html src="/screenshots/fragment-browser.png" alt="A time-based view with a relation to another page and an expanded linked-data member" caption="Follow view relations to pages and inspect their members" %}

## 5. Create and explore another view

From the event stream, choose **New view** and start from **Reference**. Name it `by-facility` and
set `tree:fragmentationPath` to `dcterms:isVersionOf`; this groups versions of the same car park
together. Create the view, then fetch, simulate and ingest another update as above. Once the server
has bucketised and paginated it, open **Browse**, select `by-facility`, and follow a relation to
explore that facility's versions.

{% include figure.html src="/screenshots/create-view.png" alt="The new view dialog showing fragmentation choices, a form, and its Turtle definition" caption="Choose a view template and configure its fragmentation" %}

You have now taken live data through an LDIO pipeline, checked its transformation in the sandbox,
ingested the result, and browsed it through both time-based and reference views. For more detail,
see [LDES servers](../ldes-servers/), [LDIO pipelines](../ldio/) and the
[sandbox](../sandbox/).
