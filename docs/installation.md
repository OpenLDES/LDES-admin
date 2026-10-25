---
title: Installation
description: Run LDES-admin with Docker, or build and run it from source.
summary: Run LDES-admin with Docker Compose, or build it from source.
icon: download
permalink: /installation/
---

LDES-admin is one application: a Spring Boot backend that serves the web frontend on `/` and its
API on `/api/v1`. It keeps what it knows in Postgres (the LDES servers and LDIOs you add, their
activity and their throughput) and starts local LDES servers and LDIOs in Docker.

## What you need

* **Docker**, to run LDES-admin, and for the local LDES servers and LDIOs it starts, including the
  [sandbox](../sandbox/).
* **Postgres**. LDES-admin creates its tables itself (Flyway) and is tested with Postgres 16.
* To build from source: a **JDK 25**. The build downloads Maven, Node.js and pnpm by itself.

## Run with Docker Compose

The quickest way to run LDES-admin is with its Docker image and a Postgres next to it. Save this as
`compose.yaml`:

```yaml
services:
  ldes-admin:
    image: {{ site.docker_image }}:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ldes_admin
      SPRING_DATASOURCE_USERNAME: ldes_admin
      SPRING_DATASOURCE_PASSWORD: ldes_admin
      # On Docker Desktop: how LDES-admin reaches the containers it starts
      # TESTCONTAINERS_HOST_OVERRIDE: host.docker.internal
    volumes:
      # Lets LDES-admin start local LDES servers and LDIOs
      - /var/run/docker.sock:/var/run/docker.sock
    group_add:
      # The group that owns the Docker socket (0 on Docker Desktop)
      - "0"
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: ldes_admin
      POSTGRES_USER: ldes_admin
      POSTGRES_PASSWORD: ldes_admin
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ldes_admin -d ldes_admin"]
      interval: 5s
      timeout: 5s
      retries: 10
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

Start it, and open <http://localhost:8080>:

```shell
docker compose up -d
```

### Access to Docker

LDES-admin starts local LDES servers and LDIOs as containers next to its own, through the Docker
socket of the host. The image runs as a user without privileges, so it needs the group that owns
the socket:

| Where Docker runs                    | Group to add                               | Also set                                            |
|--------------------------------------|--------------------------------------------|-----------------------------------------------------|
| Linux                                | `stat -c '%g' /var/run/docker.sock`        | –                                                   |
| Docker Desktop (macOS, Windows)      | `0`                                        | `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` |

Without the socket, LDES-admin still manages the LDES servers and LDIOs that run elsewhere; only
starting local ones and the sandbox fail.

> Local instances are meant for experimenting. They stop with LDES-admin, data and all, and
> LDES-admin forgets them when it starts again.
{: .note }

### Images

| Image                                | What                                                          |
|--------------------------------------|---------------------------------------------------------------|
| `{{ site.docker_image }}:<version>`  | A release, on Docker Hub; `latest` is the most recent one     |
| `{{ site.snapshot_image }}:latest`   | The latest build of the `develop` branch, on GitHub; every build is also tagged `<version>-<timestamp>` |

Both are built for `linux/amd64` and `linux/arm64`.

## Run with `docker run`

With a Postgres of your own:

```shell
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<database> \
  -e SPRING_DATASOURCE_USERNAME=<user> \
  -e SPRING_DATASOURCE_PASSWORD=<password> \
  -v /var/run/docker.sock:/var/run/docker.sock --group-add <group of the socket> \
  {{ site.docker_image }}:latest
```

## Check that it runs

The health endpoint answers once LDES-admin is up:

```shell
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

Open <http://localhost:8080>. The switch in the top bar picks the mode:

* **Production** manages real LDES servers and LDIOs, through the backend. The frontend starts in
  this mode when the backend answers.
* **Demo** runs on sample data in your browser, to explore LDES-admin without any server. Nothing
  you do there reaches the backend.

The browser remembers your choice. Next: [add the LDES servers](../ldes-servers/) and
[LDIOs](../ldio/) you want to [monitor](../monitoring/), or start the [sandbox](../sandbox/).

## Build from source

Clone the repository and build it with the Maven wrapper. Maven builds the frontend along with the
backend, so you need neither Node.js nor pnpm:

```shell
git clone {{ site.github_url }}.git
cd LDES-admin
./mvnw install -DskipTests
./mvnw -pl bootstrap spring-boot:run
```

This starts Postgres from `compose.yaml` in Docker, and LDES-admin on <http://localhost:8080>.

The build also makes `bootstrap/target/ldes-admin-bootstrap-<version>.jar`, which holds the
frontend too. Run it anywhere with a JDK 25, given a database:

```shell
java -jar bootstrap/target/ldes-admin-bootstrap-*.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/ldes_admin \
  --spring.datasource.username=ldes_admin \
  --spring.datasource.password=ldes_admin
```

To work on LDES-admin itself, with the frontend on its own and hot reloading, see the
[README]({{ site.github_url }}#running-ldes-admin).

## Configuration

LDES-admin is a Spring Boot application: set its properties as environment variables (as in the
examples above), as command line arguments (`--name=value`) or in an `application.yaml` next to the
jar.

| Property                                     | Environment variable                          | Default                     | What                                                                     |
|----------------------------------------------|-----------------------------------------------|-----------------------------|--------------------------------------------------------------------------|
| `spring.datasource.url`                      | `SPRING_DATASOURCE_URL`                       |                             | The JDBC URL of the Postgres database                                    |
| `spring.datasource.username`                 | `SPRING_DATASOURCE_USERNAME`                  |                             | Its user                                                                 |
| `spring.datasource.password`                 | `SPRING_DATASOURCE_PASSWORD`                  |                             | Its password                                                             |
| `ldes-admin.monitor.enabled`                 | `LDESADMIN_MONITOR_ENABLED`                   | `true`                      | Check the health of every instance and sample the throughput of the LDES servers |
| `ldes-admin.monitor.interval`                | `LDESADMIN_MONITOR_INTERVAL`                  | `1m`                        | How often                                                                |
| `ldes-admin.local-runtime.ldes-server-image` | `LDESADMIN_LOCALRUNTIME_LDESSERVERIMAGE`      | `{{ site.ldes_server_image }}` | The image of local LDES servers                                       |
| `ldes-admin.local-runtime.ldio-image`        | `LDESADMIN_LOCALRUNTIME_LDIOIMAGE`            | `{{ site.ldio_image }}`     | The image of local LDIOs                                                 |
| `ldes-admin.local-runtime.postgres-image`    | `LDESADMIN_LOCALRUNTIME_POSTGRESIMAGE`        | `postgres:16-alpine`        | The database of every local LDES server                                  |
| `ldes-admin.local-runtime.startup-timeout`   | `LDESADMIN_LOCALRUNTIME_STARTUPTIMEOUT`       | `5m`                        | How long a local instance may take to start, pulling its image included  |
| `ldes-admin.local-runtime.fragmentation-cron` | `LDESADMIN_LOCALRUNTIME_FRAGMENTATIONCRON`   | `*/5 * * * * *`             | How often local LDES servers fragment new members (the server default is every 30 seconds) |
| `ldes-admin.ldio.delete-timeout`             | `LDESADMIN_LDIO_DELETETIMEOUT`                | `30s`                       | How long to wait for an LDIO to stop a pipeline when deleting or updating it |
| `ldes-admin.security.enabled`                | `LDESADMIN_SECURITY_ENABLED`                  | `false`                     | Require a JWT on the API (see below)                                     |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI` |             | The issuer of the tokens, with security enabled                          |

### Securing the API

By default the API is open, for use on your own machine or a trusted network. With
`ldes-admin.security.enabled=true`, it becomes an OAuth2 resource server: every call needs a JWT of
the issuer you configure. `/actuator/health` stays public, and so does the frontend, which holds no
data.

> The frontend cannot sign in yet. With security enabled it loads, but its calls to the API are
> refused: use the API with a token of your own until then.
{: .warning }

## Upgrading

Pull the new image (or build the new version) and restart LDES-admin. It migrates its database on
startup, so the LDES servers, LDIOs and activity it knew are kept.
