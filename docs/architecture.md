# Architecture

Nexus is a single-host control plane. Docker Engine is the source of observed state. PostgreSQL stores history. `nexus.yml` is the desired-state manifest for deploy, rollback, and health.

```text
                    INTERNET
                       │
                       ▼
                  ┌─────────┐
                  │  NGINX  │
                  └────┬────┘
                       │
                       ▼
              ┌─────────────────┐
              │     Next.js     │
              │    Frontend     │
              └────────┬────────┘
                       │
                 REST + SSE
                       │
                       ▼
              ┌─────────────────┐
              │  Spring Boot   │
              │      API       │
              └───────┬─────────┘
                      │
          ┌───────────┼────────────┐
          │           │            │
          ▼           ▼            ▼
       Docker      PostgreSQL   Scheduler
       Engine
```

Redis is not required for V1.

## Request path

Production: browser → edge Nginx (`ava-assistant.duckdns.org` vs `0nexus.duckdns.org`) → `/` to the Next.js standalone server (`127.0.0.1:3000`), `/api/` and `/actuator/health` to Spring Boot (`127.0.0.1:8080`). Nginx disables buffering on `/api/` so SSE is not held. Nexus does not bind port 80.

Local development: `next dev` rewrites `/api/:path*` to `http://localhost:8080`. The production image sets `output: 'standalone'` and omits those rewrites so Nginx remains the only `/api` hop.

## Backend layout

Hexagonal / clean-architecture packages:

```text
domain
    ↓
application
    ↓
interfaces
    ↓
infrastructure
```

- **domain** — projects, deployments, manifests, alerts, activity, audit. No Spring, Docker, or HTTP types.
- **application** — use cases (discover, deploy, rollback, evaluate alerts, retain history).
- **interfaces** — REST and SSE controllers, error envelope.
- **infrastructure** — docker-java, JPA/Flyway, ProcessBuilder executor, scheduler.

Schema changes go through Flyway only (`ddl-auto: validate`).

## Docker access

The backend talks to the Engine over `unix:///var/run/docker.sock` (docker-java 3.7.1, httpclient5 transport). Compose mounts the socket and bind-mounts `/srv/projects` onto itself so `workingDirectory` in `nexus.yml` is a host path that `ProcessBuilder` can `cwd` into.

Write access to the Docker socket is host-root equivalent. There is no arbitrary command endpoint; deploy and rollback run only the relative command from a validated manifest.

## Realtime

Spring MVC `SseEmitter` (timeout `0`) with named events (`log`, `activity`) and comment heartbeats. Clients use `EventSource.addEventListener`, not `onmessage`. Tomcat async timeout is disabled so streams are not cut at 30s.

## Auth

Session cookie + CSRF cookie (`XSRF-TOKEN` sent back as `X-XSRF-TOKEN`). `GET /api/**` is `ADMIN` or `VIEWER`; mutating `/api/**` is `ADMIN`. Actuator exposes only `health` (including probes), with details hidden.

## Limits

Nexus shares the host with the apps it manages. If Nexus is down, Nexus cannot report that it is down. V1 accepts that and relies on Compose healthchecks plus an external watchdog later.
