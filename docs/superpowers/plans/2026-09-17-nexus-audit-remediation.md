# Nexus Audit Remediation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. One git branch per block. PR to main, merge, wait for VPS deploy, then start the next block. Do not mix a refactor into a reliability or security fix.

**Goal:** Clear the production pain (503s, empty logs, SSE storms), then contain the Docker-socket blast radius, then fix the database manager and alerts, then restore hexagonal seams without changing behavior.

**Architecture:** Keep Docker Engine as observed-state source and PostgreSQL as history. Do **not** remove `/var/run/docker.sock`. Do **not** add connection pools against managed project databases (V1 spec: open → run → close). Do **not** change the black/white UI language.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Security 6.5, docker-java 3.7.1, Next.js 16 App Router, TanStack Query 5, Ava Caddy edge.

**Audit source:** canvas `nexus-full-audit` (open beside chat). Code evidence lives in the files named below.

**Out of scope:** Kubernetes, Redis, vault, WebSockets instead of SSE, Portainer feature parity.

---

## Branch map

| Block | Branch | User-visible outcome | Depends on |
|---|---|---|---|
| B1 | `feat/fix-host-load-and-sse` | Fewer 503s, logs/errors stop vanishing, EventSource stops looping | none |
| B2 | `feat/harden-edge-and-auth` | UI not on public :3000; health URLs cannot SSRF; Nexus DB not VIEWER-readable | B1 deployed (so load is quieter) |
| B3 | `feat/fix-database-manager` | Preview/query/cell behave; MySQL timeouts work | B1 (inspect load) |
| B4 | `feat/fix-alerts-and-logs` | Alerts mean what they say; fingerprints survive fetch failures | B1 (watermark) |
| B5 | `refactor/hex-seams` | Same behavior, real ports/adapters | B1–B4 (behavior frozen) |
| B6 | `refactor/frontend-modules` | Smaller views, logout, Caddy headers | B3 UI bugs already fixed |

Workflow for every block: branch from `origin/main` after the previous block is merged → TDD → commit → `gh pr create` → merge → wait for Actions deploy.

---

## Phase 0 — Allowed APIs (re-read before coding)

- Spring Security 6.5: `SecurityFilterChain`, `CookieCsrfTokenRepository`, session cookie customizer (`server.servlet.session.cookie.*`).
- docker-java 3.7.1: `listContainersCmd` already has names/labels/ports/state; do **not** inspect in `listAll()`.
- PostgreSQL JDBC: `connectTimeout` / `socketTimeout` are **seconds**.
- MySQL Connector/J: those properties are **milliseconds**.
- Next.js 16: `proxy.ts` (not `middleware.ts`); `EventSource` `onerror` + `readyState`.
- Caddy 2: `header` + `reverse_proxy` already in `deployment/caddy/nexus.caddy`.
- Bucket4j / Spring rate limit: if adding a filter, read current Spring Boot 3.5 docs; do not invent annotations.

Anti-patterns: do not add `attribution-reporting` to Permissions-Policy. Do not gzip `text/event-stream`. Do not bind frontend back to `0.0.0.0:3000`. Do not force-push. Production admin password is `/opt/nexus/.env`, not `changeme`.

---

## Block B1 — `feat/fix-host-load-and-sse`

**Why first:** This is what still looks like “the app is broken”: 503s on table preview, empty ERROR views, Chrome network storms, backend thread starvation.

### Files

- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/docker/DockerContainerInventory.java`
- Modify: `backend/src/test/java/com/ivan/nexus/infrastructure/docker/DockerContainerInventoryTest.java`
- Modify: `backend/src/main/java/com/ivan/nexus/application/database/DiscoverProjectDatabases.java`
- Modify: `backend/src/main/java/com/ivan/nexus/application/log/AnalyzeLogs.java`
- Modify: `backend/src/test/java/com/ivan/nexus/application/log/AnalyzeLogsTest.java`
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/sse/SseExecutorConfig.java`
- Modify: `backend/src/main/java/com/ivan/nexus/application/deployment/DeploymentCommandRunner.java` (executor bean name only)
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/docker/DockerConfiguration.java`
- Modify: `frontend/hooks/use-event-source.ts`
- Modify: `frontend/features/deployments/deployment-stream.tsx`
- Create: `frontend/hooks/use-event-source.test.ts` (or equivalent if hook tests already exist)

### Tasks

- [x] **B1.1 Docker list without inspect**
  - Write a failing test: `listAll()` must not call `inspectContainerCmd` for a successful list payload that already has name, labels, state, ports.
  - Map `Container` list API fields into `ContainerSnapshot`. Keep `inspect(id)` for env/ports/credentials.
  - Restart-count may be 0 until inspect; that is acceptable for list views.

- [x] **B1.2 Discover databases without N+1 inspect**
  - After `listAll()`, call `inspect` **once per matching database candidate**, not for every container in the project plus the inspect already done in list.
  - Optional short TTL cache (≤ 15s) keyed by `projectId:databaseId` for `resolve()`. Test that two `preview` calls in the same second do not list+inspect the whole host twice.

- [x] **B1.3 AnalyzeLogs watermark after success**
  - Failing test: when `logProvider.fetch` throws, the next `since` is still the previous watermark (or window start), not `now`.
  - Move `watermarks.put` to after a successful fetch. Empty result from a quiet container **does** advance the watermark.

- [x] **B1.4 SSE / deploy thread pools**
  - `sseExecutor`: `queueCapacity` small (e.g. 16) so `maxPoolSize` 8 can grow. Do not leave the default unbounded queue.
  - New `deploymentExecutor` bean (core 2, max 4, queue 8) used only by `DeploymentCommandRunner`.
  - Test: submitting two long `@Async` deploys does not block a log-follow task on `sseExecutor`.

- [x] **B1.5 Docker follow timeout**
  - `responseTimeout` of 45s kills idle `followStream`. Either disable response timeout for the follow client or set it ≥ heartbeat interval × 3 (heartbeats are 15s → ≥ 60s, prefer 0 / infinite for follow-only client).
  - Do not change ping/list timeouts to infinite.

- [x] **B1.6 EventSource**
  - `useEventSource`: `source.onerror` → close and stop if `readyState === EventSource.CLOSED` or after N failures; optional backoff.
  - `deployment-stream.tsx`: pass `enabled: status is not terminal`; never reopen after SUCCESS/FAILED/CANCELLED.
  - Test: terminal status does not keep an EventSource URL.

### Verification

- Docker inventory test: zero inspect calls on `listAll`.
- AnalyzeLogsTest: failed fetch does not skip the window.
- Frontend: open a finished deployment page — Network tab shows one stream then stop, not a reconnect loop.
- After deploy: click `jobs` / `alert_events` — no inspect storm of 503s from Caddy.

---

## Block B2 — `feat/harden-edge-and-auth`

**Why second:** Socket stays. Contain who can reach Spring, what URLs Nexus will GET, and who can read the control-plane DB.

### Files

- Modify: `deployment/docker-compose.yml` (`127.0.0.1:3000:3000`)
- Modify: `deployment/remote-deploy.sh` (do not open all of `172.16.0.0/12` if a Caddy bridge IP can be detected)
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/health/HttpHealthChecker.java`
- Modify: `backend/src/main/java/com/ivan/nexus/domain/manifest/ManifestValidator.java` (scheme + host policy)
- Test: `HttpHealthChecker` / `ManifestValidatorTest`
- Modify: `NexusDatabaseExclusions.java` **or** `RunDatabaseQuery` / `PreviewTable` / `GetDatabaseMetadata` (ADMIN-only for compose project `nexus`)
- Modify: `SecurityConfig.java`, `application.yml` (cookie Secure, SameSite=Lax)
- Login rate limit: prefer a servlet filter with in-memory counter (no Redis). Document the limit (e.g. 10 / 10 min / IP).
- Modify: `deployment/caddy/nexus.caddy` only if cookies need HTTPS hint; HSTS can wait for B6.

### Tasks

- [ ] **B2.1 Bind frontend localhost**
  - `ports: ["127.0.0.1:3000:3000"]`. Caddy on the same host still reaches it via `host.docker.internal:3000`.
  - Failing check: compose file must not contain `"3000:3000"` unbound.

- [ ] **B2.2 Narrow :8080**
  - Detect Ava Caddy container IPv4 (docker inspect) and allow only that IP (plus `172.17.0.1` host-gateway if required). Fallback comment if detection fails — do not silently keep `/12` without logging it.
  - Probe still uses a container on the Caddy network, not a random bridge.

- [ ] **B2.3 Health URL allowlist**
  - Fail closed: only `http`/`https`. Block link-local, metadata (`169.254.169.254`), and `file:`.
  - Product choice (lock in a test): allow RFC1918 + loopback **or** deny them. Recommended for this VPS: allow loopback + docker bridges so project healthchecks work; deny cloud metadata and non-http schemes.
  - `HttpHealthChecker` must reject before `URI.create` + send.

- [ ] **B2.4 Control-plane database**
  - Do **not** hide the Nexus postgres from `/projects/nexus` (that was PR #19 and the empty-state lie).
  - VIEWER: metadata maybe; `preview` / `query` / `cell` on compose project `nexus` → `FORBIDDEN`.
  - ADMIN: allowed, existing destructive confirm still required for writes.
  - Tests in `DatabaseControllerTest` / `RunDatabaseQueryTest`.

- [ ] **B2.5 Cookies + login throttle**
  - `server.servlet.session.cookie.secure: true`, `same-site: lax`, `http-only: true` for `JSESSIONID`. CSRF cookie stays readable (`withHttpOnlyFalse`).
  - Rate-limit `POST /api/auth/login` by IP (use `X-Forwarded-For` only when the peer is Caddy). Test 429 after N failures.

### Verification

- From the public internet, `:3000` is closed (Caddy :443 still serves UI).
- Manifest `health.url: http://169.254.169.254/` is rejected.
- VIEWER preview of `users` on Nexus postgres is 403; ADMIN still works.

---

## Block B3 — `feat/fix-database-manager`

**Why third:** Preview still has correctness bugs independent of host load.

### Files

- `JdbcQueryExecutor.java` + `JdbcQueryExecutorIT.java` + `JdbcQueryExecutorTest.java`
- `SqlStatementClassifier.java` + tests
- `MongoStatementClassifier.java` + tests
- `EngineDetector.java` + tests
- `DatabaseDtos.java` (columns currently dropped)
- `frontend/features/database/database-page.tsx`
- `frontend/features/database/data-grid.tsx`

### Tasks

- [ ] **B3.1 MySQL timeout units**
  - IT against MySQL testcontainer: connect succeeds with 5s, not 5ms.
  - Set `connectTimeout`/`socketTimeout` in milliseconds for MySQL only.

- [ ] **B3.2 JDBC URL injection**
  - `defaultDatabase`, username: allow `[A-Za-z0-9_]+` (plus `.` for MySQL schema if needed). Reject `;`, `?`, `/`, whitespace.
  - Test: database name `lab?allowMultiQueries=true` → `QUERY_FAILED`, no extra properties.

- [ ] **B3.3 Catalog columns**
  - `DatabaseDtos.fromSql` must include columns already loaded by `JdbcQueryExecutor.metadata`. Test the JSON shape.

- [ ] **B3.4 WRITE that still returns rows**
  - `SELECT … FOR UPDATE` / `RETURNING`: use `executeQuery` when the classifier is WRITE but the statement starts with SELECT/WITH. Test against Postgres.

- [ ] **B3.5 Mongo `$out` / `$merge`**
  - Parse pipeline as JSON; detect `$out` / `$merge` case-insensitive on object keys, not substring of `toString()`.

- [ ] **B3.6 EngineDetector**
  - `mongo-express` is not Mongo. Test image tokens more strictly (`mongo:` / `mongodb` / official mongo, not `startsWith("mongo")` on every token).

- [ ] **B3.7 Frontend browse**
  - Reset `selection`, `queryResult`, `statement` when `selectedId` changes.
  - Null cell: compare draft to canonical string (`''` for null means “no edit” if user never focused, or treat display `null` vs draft `''` as unchanged unless the user typed).
  - Cell mutation: `onError` + `invalidateQueries` for preview.

### Verification

- MySQL IT green. Postgres preview of `jobs` after deploy still works.
- Switching instance clears the tree highlight and does not fire the previous table’s preview.
- Blurring a null cell without typing does not POST `/cell`.

---

## Block B4 — `feat/fix-alerts-and-logs`

### Files

- `EvaluateAlerts.java` (remove `@Transactional` from the I/O method; persist in a narrower transaction)
- `AlertEvaluator.java` + tests for `errorRatePerMinute` using a 60s window, not “hits this 30s scan”
- `AnalyzeLogs.java`: own `@ConditionalOnProperty` (`nexus.logs.analysis-enabled`, default true), not `nexus.alerts.enabled`
- `GetProject.java`: project with `nexus.yml` and zero containers is found, status empty/not 404
- Frontend dashboard/project: `GET /alerts?status=ACTIVE` when the label says “active”
- Classifier comment strip quote-awareness if still broken after B3 (SQL `--` inside strings)

### Tasks

- [ ] **B4.1 Transaction boundary** — test that `stats()` / HTTP health are not invoked inside an open Hibernate session (easier: split methods; unit-test the persist method is `@Transactional` and `execute()` is not).
- [ ] **B4.2 Error rate** — 11 hits in 30s must **not** fire a rule of 10/min; 11 hits spanning 60s must.
- [ ] **B4.3 AnalyzeLogs independent of alerts**
- [ ] **B4.4 GetProject empty inventory**
- [ ] **B4.5 Alerts query param**

### Verification

- Disable `nexus.alerts.enabled` in a test profile; fingerprints still update if analysis is on.
- Open a project that only has `nexus.yml` — Overview, not 404.

---

## Block B5 — `refactor/hex-seams`

**Constraint:** no intentional behavior change. If a test must change, stop and split the behavior into B1–B4 instead.

### Tasks

- [ ] **B5.1** `ContainerRuntime` port; `RestartService` drops docker-java imports. Adapter in `infrastructure/docker`.
- [ ] **B5.2** `ManifestValidator.validate(manifest, Path allowedRoot)` — no Spring. Factory in infrastructure reads `NexusProperties`.
- [ ] **B5.3** Application ports: `SqlExecutor`, `MongoExecutor`, `FingerprintStore`. Use cases stop importing `*JpaRepository` and `JdbcQueryExecutor`.
- [ ] **B5.4** `GetAlerts` / `GetActivityTimeline` return application/domain types; controllers map to DTOs. `ActivityHub` stops importing `interfaces.activity.ActivityResponse`.
- [ ] **B5.5** `DeploymentController` history/get/SSE go through use cases, not JPA.
- [ ] **B5.6** Split `EvaluateAlerts`, `DeploymentCommandRunner`, `JdbcQueryExecutor` along the SRP table in the audit canvas. Keep tests green with characterization tests first if missing.

### Verification

- `./mvnw test` and `npm test` unchanged in intent. Grep: `application/**` must not import `com.github.dockerjava`. `domain/**` must not import `org.springframework`.

---

## Block B6 — `refactor/frontend-modules`

### Tasks

- [ ] Split `project-overview.tsx` into deploy panel, service list, error snapshot, alert snapshot, metrics, history.
- [ ] Split `database-page.tsx` into `InstanceBar`, `BrowsePane`, `QueryPane`.
- [ ] Logout button calling `POST /api/auth/logout` then `/login`.
- [ ] Caddy: `Strict-Transport-Security`, `X-Frame-Options DENY` or CSP `frame-ancestors 'none'`, `X-Content-Type-Options nosniff`. Do not add `attribution-reporting`.
- [ ] Single `AuthUser` type; `role: 'ADMIN' | 'VIEWER'`.
- [ ] Trust `X-Forwarded-For` for audit only when remote addr is Caddy/gateway.

### Verification

- Visual: black/white, no gradients. Browser pass on overview, logs, database, one deployment.
- Logout clears session; next navigation is login.

---

## Remaining findings (parked, not a seventh branch unless they reappear)

| Item | Why parked |
|---|---|
| Docker socket = root | Spec; contained in B2 |
| Destructive confirm is a boolean | Spec; CSRF still required |
| No pool for managed DBs | Spec V1 |
| Lab compose ports unbound | Local lab only; do not publish lab on the VPS |
| `SecretSanitizer` single password | Follow-up if SSE still leaks |
| `proxy.ts` cookie presence | UX gate; Spring remains source of truth |
| `mongo-express` if EngineDetector already fixed in B3 | — |
| Classifier `FOR UPDATE` inside string literals | B3/B4 |
| Root SSH in GitHub Actions | Operational; do not rotate keys in app PRs |

---

## Final verification (after B6)

- [ ] Public :3000 closed; :443 Nexus UI works.
- [ ] Table preview: rows or a specific `QUERY_FAILED`, never a retry storm of 503.
- [ ] Finished deployment page: one SSE, then idle.
- [ ] ERROR log view shows lines when fingerprints exist in the current container window.
- [ ] VIEWER cannot query Nexus `users`.
- [ ] `grep` application layer clean of docker-java; domain clean of Spring.
- [ ] Chrome `attribution-reporting` / `startTime` noise still ignored (not app).
