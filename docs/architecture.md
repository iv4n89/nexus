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

Production: browser → Ava Caddy (`ava-assistant.duckdns.org` vs `0nexus.duckdns.org`) → `/` to Next.js (`host.docker.internal:3000`), `/api/` and `/actuator/health` to Spring Boot (`host.docker.internal:8080`). Caddy disables proxy buffering on `/api/` so SSE is not held. Nexus does not bind port 80.

Local development: `next dev` rewrites `/api/:path*` to `http://localhost:8080`. The production image sets `output: 'standalone'` and omits those rewrites so Nginx remains the only `/api` hop.

## Backend layout

Hexagonal / clean-architecture packages:

```text
interfaces ─────────────────> application ──> domain
    │                              ↑             ↑
    └─ deployment SSE transport ─> infrastructure
```

- **domain** — projects, deployments, manifests, alerts, activity, and audit. It depends only on the JDK.
- **application** — use cases and outbound ports. It depends on the domain and JDK, with Spring orchestration annotations allowed (`@Service`, scheduling/configuration conditions, injection qualifiers, and transaction boundaries). It does not depend on interface or infrastructure adapters, persistence APIs, serialization libraries, or adapter SDKs.
- **infrastructure** — implements application ports with docker-java, JPA/Flyway, database drivers, `ProcessBuilder`, and SSE progress delivery.
- **interfaces** — REST/error-envelope adapters invoke application use cases and domain-facing views. One explicit outer-layer transport collaboration remains: `DeploymentController` creates the `SseEmitter` and subscribes it directly with infrastructure `DeploymentStreamHub`.

The same `DeploymentStreamHub` implements the application-owned `DeploymentProgress` port for append/completion output while retaining replay, live-subscriber callbacks, and transport completion. This interface-to-infrastructure SSE connection is an outer-layer transport exception; the core invariant is that domain and application never depend outward. `HexagonalArchitectureTest` enforces positive allowlists: domain may use only domain/JDK classes, while every `com.ivan.nexus.application..` class may use only application/domain/JDK, SLF4J, and the narrowly listed Spring orchestration annotation packages. A synthetic application fixture with an unknown test-only SDK dependency proves the global allowlist rejects unapproved libraries.

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
