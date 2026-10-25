---
title: Monitoring
description: Keep an eye on the health, throughput and background work of your LDES servers and LDIOs.
summary: The dashboard, health checks, event stream statistics, background processes and pipelines.
icon: activity
permalink: /monitoring/
---

LDES-admin watches every LDES server and LDIO you add to it: it checks their health every minute,
samples how many members the servers ingest, and reads their metrics whenever you look. This page
explains what you see, and what it takes for an instance to show it.

## What LDES-admin needs from an instance

You add an instance by its base URL (see [LDES servers](../ldes-servers/#add-an-ldes-server-that-runs-elsewhere)
and [LDIO](../ldio/#add-an-ldio-that-runs-elsewhere)). LDES-admin then reaches these endpoints on
it:

| Endpoint                  | LDES server                                      | LDIO                                       |
|---------------------------|--------------------------------------------------|--------------------------------------------|
| `/actuator/health`        | Health check (with the admin API as fallback)    | Health check (with the admin API as fallback) |
| `/admin/api/v1/…`         | Event streams, views, shapes, DCAT               | Pipelines, their status, the component catalog, LDES clients |
| `/actuator/prometheus`    | Members ingested, bucketised and paginated; background processes | Members in and out per pipeline |
| `/{collection}/…`         | Ingesting members, browsing views                | –                                          |
| `/{pipeline}`             | –                                                | Sending data to pipelines with an HTTP input |

Both expose only the health endpoint by default. For the statistics, also expose `prometheus`:

```shell
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus
```

Local instances, which LDES-admin starts itself, are set up like this already.

> An instance without the `prometheus` endpoint still works in LDES-admin: you manage its event
> streams or pipelines as usual, but its numbers stay empty, and the dashboard lists it under
> *Needs attention*.
{: .note }

## The dashboard

The dashboard is the home page of LDES-admin. It refreshes itself every 30 seconds.

{% include figure.html src="/screenshots/dashboard.png" alt="The dashboard of LDES-admin" caption="The dashboard: headline numbers, throughput, recent activity, and every instance" %}

### Headline numbers

| Tile                        | Shows                                                                            | Opens          |
|-----------------------------|----------------------------------------------------------------------------------|----------------|
| **LDES servers up**         | How many LDES servers are healthy, of all you added                              | LDES servers   |
| **LDIOs up**                | How many LDIOs are healthy                                                       | LDIOs          |
| **Pipelines running**       | Running pipelines of all LDIOs, and how many are halted or starting              | LDIOs          |
| **Members ingested, 24 h**  | Members all LDES servers ingested in the last 24 hours, with a trend line        | LDES servers   |
| **Needs attention**         | Instances that are down, plus the problems LDES-admin ran into reading the others (e.g. metrics it could not fetch); below it, the members waiting to be paginated on all servers | – |

### Members ingested per hour

A line per LDES server shows the members it ingested per hour over the last 24 hours. Hover the
chart to read every server at that hour, or switch to the table for the exact numbers.

The monitor samples the members every healthy server ingested so far, every minute, and keeps the
samples for 48 hours. A line therefore starts when LDES-admin started watching that server.

### Recent activity

A feed of what happened to your instances:

* what you did through LDES-admin: instances added, started, removed and stopped, event streams and
  views created and deleted, pipelines deployed, updated, halted, resumed and deleted;
* what LDES-admin noticed: an instance going down (*is down*) and coming back (*is up again*), and
  local instances that were gone when LDES-admin started.

Only changes are recorded: an instance that is already down when LDES-admin starts is not news.

### Instances and pipelines

Below the [sandbox environment](../sandbox/#the-sandbox-environment), two lists:

* **LDES servers**, with their number of event streams and members, a warning sign with the problems
  LDES-admin ran into, and their health;
* **Pipelines**, grouped per LDIO: the status of every pipeline and its members in and out.

Click a server or LDIO to open it.

## Health checks

Every minute (`ldes-admin.monitor.interval`, see [configuration](../installation/#configuration)),
LDES-admin asks every instance for its `/actuator/health`. An instance that does not expose it
counts as healthy when its admin API answers. An instance is then:

| Health      | Meaning                                                            |
|-------------|--------------------------------------------------------------------|
| **Up**      | It answers, and reports itself healthy                             |
| **Down**    | It does not answer, or reports itself unhealthy                    |

The monitor records when an instance goes down and when it comes back in the
[recent activity](#recent-activity). Pages check the health of what they show when they open
(*Checking* until it answers), so they never wait for the next round of the monitor. An instance
that does not respond shows so on its card, and its page offers *Check again*.

The monitor also tidies up: local instances stop with LDES-admin, so on startup it removes the
local instances whose container is gone.

## LDES servers and LDIOs

The *LDES servers* and *LDIOs* pages show every instance as a card: its name and URL, its health,
whether it is a *local container* (started by LDES-admin) or *registered* (it runs elsewhere),
whether it is a *sandbox*, and when it was added.

{% include figure.html src="/screenshots/ldes-servers.png" alt="The LDES servers page with a card per server" caption="LDES servers, registered and local" %}

Below that, the numbers that matter:

* for an LDES server: its event streams, views and members;
* for an LDIO: its running and halted pipelines, and the members it processed.

The first problem LDES-admin ran into shows at the bottom of the card.

## Event stream statistics

The *Event streams* tab of an LDES server lists its event streams, each with:

* **Members**: how many it ingested (hover for the exact number);
* **Paginated**: the share of the members that the slowest view has put on pages;
* **Backlog**: the members that are bucketised but not yet paginated, in the slowest view.

{% include figure.html src="/screenshots/ldes-server.png" alt="The event streams of an LDES server with members, pagination and backlog" caption="Event streams with their statistics" %}

An LDES server fragments new members in the background: it first assigns them to the buckets of
every view (**bucketisation**), then puts them on pages and links the pages together
(**pagination**). The backlog is what pagination still has to do. It grows briefly after members
arrive; a backlog that keeps growing means the server does not keep up, or a background job fails
(see [background processes](#background-processes)).

Open an event stream for the same numbers in tiles (*Members ingested*, *Views*,
*Waiting to be paginated*, *Paginated*), and per view on its card: how many members it bucketised
and paginated, with a bar for its progress.

{% include figure.html src="/screenshots/event-stream.png" alt="An event stream with its views and their progress" caption="Views of an event stream, and how far they are paginated" %}

LDES-admin reads these numbers from the gauges `ldes_server_ingested_members_count`,
`ldes_server_bucket_members_count` and `ldes_server_pagination_members_count` of the server.

## Background processes

The *Background processes* tab of an LDES server shows the steps of its two batch jobs, with what
they did since the server started:

| Job           | Step                       | What it does                                                         |
|---------------|----------------------------|----------------------------------------------------------------------|
| Fragmentation | **Bucketisation**          | Assigns newly ingested members to the buckets of every view          |
| Fragmentation | **Pagination**             | Puts bucketised members on pages and links the pages together        |
| Maintenance   | **View retention**         | Removes members from views whose retention policy no longer keeps them |
| Maintenance   | **Event source retention** | Removes members that the retention policy of the event source no longer keeps |
| Maintenance   | **Compaction**             | Merges small immutable pages into larger ones                        |
| Maintenance   | **Deletion**               | Deletes compacted pages once the compaction duration has passed      |

{% include figure.html src="/screenshots/background-processes.png" alt="The background processes of an LDES server, one with failed runs" caption="Background processes, with a failed retention run" %}

Every step shows whether it is **running**, **idle** or has **failed** runs, and its completed
runs, failed runs, average run and longest recent run. The tab itself carries a red badge with the
number of steps that failed, or else the number that is running.

The runs change by the second, so the tab refreshes every 2 seconds while it shows. Pick another
interval in *Refresh* (every second up to every 30 seconds, or never); your browser remembers it.

The jobs run on the schedules of the server, `ldes-server.fragmentation-cron` (every 30 seconds by
default; every 5 seconds on local servers) and `ldes-server.maintenance-cron`. LDES-admin reads
them from the Spring Batch metrics of the server (`spring_batch_step_*`).

## Pipelines

The *Pipelines* tab of an LDIO lists its pipelines with their components, their status and their
members in and out.

{% include figure.html src="/screenshots/ldio.png" alt="The pipelines of an LDIO" caption="The pipelines of an LDIO, with their status and throughput" %}

| Status                       | Meaning                                                    |
|------------------------------|------------------------------------------------------------|
| **Initialising**, **Starting** | LDIO is setting the pipeline up                          |
| **Running**                  | The pipeline processes data; it can be halted              |
| **Halted**                   | Halted on purpose; it can be resumed                       |
| **Resuming**                 | On its way back to running                                 |
| **Stopping**, **Stopped**    | LDIO is stopping it, or has, e.g. because it is deleted    |

A pipeline that reads an LDES (`Ldio:LdesClient`) also shows how far its client is: *replicating*
(catching up with the history of the stream), *synchronising* (following new members),
*completed* or *error*. A pipeline that was halted or resumed by hand shows *changed manually*.

*Members in → out* counts what entered the pipeline and what its outputs sent on, from the
counters `ldio_data_in_total` and `ldio_data_out_total`, since the LDIO started. The difference
shows as *in flight*: members still on their way, or that the pipeline dropped, e.g. in a change
detection filter. See [LDIO](../ldio/#the-pipelines-of-an-ldio) to halt, resume, edit or delete
pipelines.

## When something looks wrong

| You see                                          | Check                                                                   |
|--------------------------------------------------|-------------------------------------------------------------------------|
| An instance *does not respond*                   | That it runs, that LDES-admin can reach its URL (from the LDES-admin container, if it runs in Docker), and that its admin API is exposed |
| Empty statistics, and a problem on its card      | That `/actuator/prometheus` is exposed (`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus`) |
| A backlog that keeps growing                     | The *Background processes* tab: failed pagination runs, or runs that take longer than the fragmentation schedule |
| No line in *Members ingested per hour*           | A line starts at the first sample of the monitor, once the server is healthy and exposes `/actuator/prometheus`; it counts members ingested from then on |
| A pipeline whose members in grow, but not out    | The pipeline in the [sandbox](../sandbox/): simulate it with one of its members, and check where its outputs send to |
