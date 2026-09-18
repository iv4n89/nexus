# Nexus — Traffic minute ingest, dashboard, and spike alerts (design)

**Date:** 2026-09-18  
**Status:** approved (conversation)  
**Implementation plan:** `docs/superpowers/plans/2026-09-18-traffic-minute-dashboard.md`  
**Roadmap:** V1.7 Traffic Analytics (`Nexus_Product_Roadmapnew.md`)  
**Parent decisions:** HTTP from Caddy access logs; minute aggregates for 7 days; volume 3× median + 5xx spike; ingest via `docker.sock` log follow of the Caddy container; single grain (no dual hourly+minute write); UI matches existing Nexus (black, `#f5f5f5`, `#2a2a2a`, `#ff4d4f` for errors/alerts).

---

## 1. Goal

Show **real HTTP traffic** for the VPS: global, per project, and per service. Persist **one-minute counters for 7 days**. Fire the existing alert pipeline on **volume spikes** and **5xx spikes**. New top-level **Traffic** page with SVG charts in the current visual language.

This is not Docker network stats, not raw access-log storage, and not a Caddy admin UI.

---

## 2. Scope

### In

- Tail Caddy container stdout/stderr (JSON access log) through the existing Docker client
- Map `Host` → `site_domains.hostname` → `projectId` + `serviceName`
- Upsert minute buckets: requests, bytes out, 2xx/3xx/4xx/5xx, latency avg, latency max
- Retention: delete buckets older than 7 days
- Alerts: `TRAFFIC_SPIKE`, `TRAFFIC_5XX_SPIKE` (global rules, fingerprints via `AlertKey`)
- REST: global series + project ranking; extend project traffic with series + services
- Keep `GET /api/projects/{id}/traffic/deploy-delta` (aggregate minutes in the 1h window)
- UI: nav **Traffic**, `/traffic`, project overview Traffic block
- SVG charts (no chart library)
- Flag `NEXUS_TRAFFIC_INGEST_ENABLED` (default **true**)

### Out

- Raw request logs, user-agent, client IP
- Per-path top endpoints on the minute grain (hourly JSON `top_endpoints` is not copied)
- Caddy config generation / TLS (already a separate domain module)
- POST ingest from Caddy, file bind-mount of access.log
- True percentile histograms (p95 on snapshots stays **max-of-max** for deploy-delta compatibility; UI labels latency as **avg** and **max**)
- Multi-Caddy / multi-host
- VIEWER vs ADMIN difference beyond existing GET-only for traffic

---

## 3. Placement

- Nav: Dashboard · Activity · **Traffic** · Settings (`frontend/components/app-shell.tsx`)
- Route: `/traffic` (`frontend/app/(app)/traffic/page.tsx`, `frontend/features/traffic/`)
- Project: Traffic section on overview (`project-overview.tsx`)
- Backend: `domain/traffic`, `application/traffic`, `infrastructure/traffic` (Caddy log adapter), `interfaces/traffic`
- Alerts: new `AlertType` values; seeder already inserts missing global types

---

## 4. Architecture

```text
Caddy container (JSON access log)
        │  docker logs --follow
        ▼
CaddyAccessLogFollower (infrastructure, reconnect)
        │  parsed HttpAccessEvent
        ▼
ResolveTrafficTarget (application)  → DomainStore.findByHostname
        │  projectId, serviceId, host
        ▼
MinuteTrafficIngestor  → TrafficStore.upsert minute
        │
        ├─ GET /api/traffic*
        ├─ EvaluateTrafficAlerts (scheduler ~1 min, closed minute only)
        └─ EnforceTrafficRetention (daily, > 7 days)
```

Hexagonal rules: Docker SDK and JSON parsing stay in infrastructure. Application owns ingest/query/alert/retention ports. Domain owns bucket math and spike predicates (pure).

If the Caddy container is missing or Docker is down: log a warning, do not fail Spring Boot. GET endpoints return empty series.

Unmapped hosts: `projectId=_unmapped`, `serviceId=_unknown`. They appear in the **global** total and in an “unmapped” row; they do not pollute a real project unless the hostname is registered.

---

## 5. Caddy log follow

Reuse the follow pattern in `DockerLogProvider` (docker-java `logContainerCmd` + `withFollow(true)` + `withTimestamps(true)`).

Container selection (first match, re-resolve on disconnect):

1. Compose label `com.docker.compose.service=caddy` (running)
2. Else name equals `caddy` or ends with `-caddy-1` / `_caddy_1`

Expected JSON (Caddy 2 `json` encoder), fields used:

- `request.host` (or `request.headers.Host[0]`)
- `status`
- `size` (bytes out; 0 if absent)
- `duration` (seconds → ms)
- `request.uri` (not stored; reserved if we later add endpoints)

Non-JSON lines, health-check noise we cannot parse, and encoder `console` lines: skip, increment debug counter. Reconnect with exponential backoff (1s–30s). Start from **now** (`withSince` = current unix time) so a restart does not replay 7 days of docker log history.

Configurable: `nexus.traffic.caddy-container` optional explicit id/name.

---

## 6. Data model

New table `traffic_minute` (Flyway `V18__traffic_minute.sql`):

| column | type |
|--------|------|
| id | UUID PK |
| project_id | VARCHAR(64) NOT NULL |
| service_id | VARCHAR(128) NOT NULL |
| host | VARCHAR(253) NOT NULL DEFAULT '' |
| bucket_start | TIMESTAMPTZ NOT NULL (truncated to minute UTC) |
| requests | BIGINT |
| bytes_in | BIGINT DEFAULT 0 (always 0 unless Caddy exposes it later) |
| bytes_out | BIGINT |
| status_2xx/3xx/4xx/5xx | BIGINT |
| latency_avg_ms | DOUBLE PRECISION |
| latency_max_ms | DOUBLE PRECISION NULL |

Unique `(project_id, service_id, host, bucket_start)`.  
Indexes: `(bucket_start)`, `(project_id, bucket_start DESC)`.

Domain type `TrafficMinuteBucket` with `ingest(status, bytes, latencyMs)` (same status bucketing as `TrafficHourlyBucket`).

`TrafficStore` becomes minute-first:

- `findBucket(projectId, serviceId, host, bucketStart)`
- `save`
- `findSince(Instant)` / `findByProjectSince(projectId, Instant)`
- `snapshot(projectId, from, to)` aggregating minutes
- `deleteOlderThan(Instant)`

`HourlyTrafficIngestor` is replaced by `MinuteTrafficIngestor` (`truncatedTo(MINUTES)`). Stop writing `traffic_hourly`. Do **not** drop `traffic_hourly` in this slice (Flyway out-of-order / other branches). `GetProjectTraffic` and `CorrelateDeployTraffic` read minutes. Snapshot `latencyP95Ms` continues as **max of `latency_max_ms`** so deploy-delta stays comparable; UI must not label it “p95”.

Retention: `bucket_start < now - 7 days`. Cron daily (reuse retention hour ~03:00 or a dedicated `nexus.traffic.retention-cron`).

---

## 7. Alert rules

New `AlertType`:

- `TRAFFIC_SPIKE`
- `TRAFFIC_5XX_SPIKE`

`AlertRuleSeeder` already seeds missing global types.

Evaluate **only the last fully closed minute** (`now.truncatedTo(MINUTES).minus(1 minute)`). Failure on one project does not abort others.

### Volume (`TRAFFIC_SPIKE`)

- Scope: per `projectId` (all services/hosts summed for that minute)
- Fire if `requests >= 30` **and** `requests >= 3 × median` of the same clock-minute-of-day across the stored 7 days (all minutes whose `hour:minute` matches, excluding the current sample)
- If fewer than 12 baseline samples, do not fire
- `AlertKey(TRAFFIC_SPIKE, projectId, null)`
- Resolve when latest closed minute is `< 1.5 × median` or `requests < 30`

### 5xx (`TRAFFIC_5XX_SPIKE`)

- Scope: per `projectId` + `serviceId`
- Fire if `requests >= 10` **and** (`status5xx / requests >= 0.05` **or** `status5xx >= 10`)
- `AlertKey(TRAFFIC_5XX_SPIKE, projectId, serviceId)`
- Resolve when rate `< 0.02` and `status5xx < 10`

Messages: include project, service (if any), request count, 5xx count/rate or multiple-of-median. Existing alert UI shows them; Traffic page lists active `TRAFFIC_*` in red.

Wire into the existing persist/fingerprint path used by `EvaluateAlerts` (same OPEN/ACTIVE + activity), not a parallel store.

---

## 8. API

All GET; `ADMIN` and `VIEWER`. `hours` must be in `1..168` or `400` `OPERATION_NOT_ALLOWED`.

### `GET /api/traffic?hours=24`

Global totals + series.

- `hours <= 24`: one point per minute
- `hours > 24`: bin to 15 minutes (sum counters; avg latency request-weighted; max of max)

```json
{
  "from": "...",
  "to": "...",
  "hours": 24,
  "requests": 0,
  "bytesOut": 0,
  "status2xx": 0,
  "status4xx": 0,
  "status5xx": 0,
  "latencyAvgMs": 0,
  "latencyMaxMs": null,
  "series": [{ "t": "...", "requests": 0, "status5xx": 0, "latencyAvgMs": 0, "latencyMaxMs": null }],
  "projects": [{ "projectId": "nexus", "requests": 0, "status5xx": 0, "latencyAvgMs": 0 }]
}
```

`projects` sorted by requests desc. Include `_unmapped`.

### `GET /api/projects/{id}/traffic?hours=24`

Extend current `TrafficResponse` with:

- `latencyMaxMs`
- `series` (same binning rule)
- `services`: `{ serviceId, requests, status5xx, latencyAvgMs, latencyMaxMs }`

Keep `latencyP95Ms` as max-of-max for deploy-delta clients. `topEndpoints` may be empty `[]` on minute grain.

### `GET /api/projects/{id}/traffic/deploy-delta`

Unchanged contract; implementation sums minutes in the 1h before/after window.

---

## 9. UI

Existing tokens only: `bg-black`, text `#f5f5f5` / `#888`, borders `#2a2a2a`, errors `#ff4d4f`, `font-mono` for numbers, section labels `text-xs tracking-[0.25em] text-[#888]`.

**`/traffic`** (`max-w-6xl` like logs/database):

1. Active `TRAFFIC_*` alerts (red), if any
2. Totals 24h: requests, 5xx (red if > 0), latency avg / max, bytes
3. Toggle 24h / 7d
4. Requests chart (white stroke SVG)
5. 5xx chart (red stroke, same time axis)
6. Project table (link to `/projects/{id}`)

**Project overview:** section TRAFFIC — compact 24h request+5xx chart, service rows, link “Full traffic”.

Empty: `No traffic yet`. Load/error copy matches dashboard.

Charts: no npm chart library. Simple polyline in viewBox; grid lines `#2a2a2a`; no gradient, no tooltip library (optional native `title` on points).

---

## 10. Configuration

```yaml
nexus:
  traffic:
    ingest-enabled: ${NEXUS_TRAFFIC_INGEST_ENABLED:true}
    caddy-container: ${NEXUS_TRAFFIC_CADDY_CONTAINER:}
    retention-days: 7
    retention-cron: ${NEXUS_TRAFFIC_RETENTION_CRON:0 15 3 * * *}
    alert-interval-ms: ${NEXUS_TRAFFIC_ALERT_INTERVAL_MS:60000}
```

Compose: no new volume. Relies on existing `/var/run/docker.sock`.

---

## 11. Error handling

- Missing Caddy / Docker: warn, ingest idle, APIs empty
- Bad JSON line: skip
- Unknown host: `_unmapped` / `_unknown`
- Alert evaluation exception per project: log, continue
- Closed-minute only (never the in-progress minute)
- Invalid `hours`: 400
- Ingest never exposed as a public write API

---

## 12. Tests

- Caddy JSON parser: happy path, console line, missing host
- Host → project/service via `DomainStore`; unmapped fallback
- Minute upsert + snapshot 24h; 7d 15-minute binning
- Spike: 3× median fires; below 30 requests does not; thin baseline does not
- 5xx: 5% and absolute 10; resolve under 2%
- Retention deletes `> 7d` only
- `TrafficController`: global GET + project series; bad hours 400
- Frontend: nav link; empty state; series render (vitest)

---

## 13. Implementation notes

- New branch from `main` (not mixed with dotenv/domain FK work)
- Do not commit `.env`
- HexagonalArchitectureTest must stay green (no Docker/JPA in application except existing Spring annotations allowlist)
- `frontend-design` does **not** introduce a new aesthetic; this page is an extension of AppShell
