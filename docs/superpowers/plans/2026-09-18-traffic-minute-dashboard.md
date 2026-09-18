# Traffic Minute Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ingest Caddy JSON access logs into 7-day minute buckets, expose global and per-project/service HTTP series, chart them on `/traffic`, and fire volume/5xx spike alerts.

**Architecture:** Docker log follow (existing `dockerFollowClient`) parses Caddy JSON into `HttpAccessEvent`. Application maps host to `DomainStore` project/service and upserts `TrafficMinuteBucket`. Queries bin minutes (1 min for 24h, 15 min for 7d). A scheduler evaluates closed-minute spikes via `PersistAlertEvaluation`. UI is AppShell black/white/red SVG, no chart library.

**Tech Stack:** Java 21, Spring Boot 3.5.16, docker-java, Flyway, JPA, Next.js App Router, TanStack Query, Vitest.

**Design:** `docs/superpowers/specs/2026-09-18-traffic-minute-dashboard-design.md`

---

## Phase 0 — Allowed APIs

| Need | API | Source |
|---|---|---|
| Follow container logs | `DockerClient.logContainerCmd(id).withStdOut(true).withStdErr(true).withFollowStream(true).withSince(unixSeconds).withTail(0)` | `DockerLogProvider.follow` |
| Follow client bean | `@Qualifier("dockerFollowClient") DockerClient` | `DockerLogProvider` constructor |
| List running containers | `listContainersCmd().withShowAll(false).exec()` | `DockerContainerInventory.listAll` |
| Hostname to domain | `DomainStore.findByHostname(String)` | `application/site/DomainStore.java` |
| Open/resolve alerts | `AlertStore.findOpen/open/resolve` plus `PersistAlertEvaluation.persist` | `EnforceBackupRetention`, `PersistAlertEvaluation` |
| Seed rules | `AlertRuleSeeder` iterates `AlertType.values()` | adding enum values is enough |
| UTC clock | existing `Clock` bean `Clock.systemUTC()` | `RetentionConfiguration` |
| Jackson | Spring `ObjectMapper.readTree` | Boot JSON starter |
| Instant truncate | `instant.truncatedTo(ChronoUnit.MINUTES)` | JDK 21 |

**Forbidden:** npm chart libraries, storing raw access lines, writing `traffic_hourly`, Docker/JPA types in `application` except existing Spring annotations, logging query strings, dropping `traffic_hourly`.

**Maven tests** from `backend/`:

`./mvnw -q test -Dmaven.repo.local=/home/ibetanzos/.m2/repository -Dtest=ClassName`

If the sandbox blocks Maven, rerun with full permissions.

**Frontend tests** from `frontend/`: `npx vitest run path`

---

## File map

```text
backend/src/main/resources/db/migration/V18__traffic_minute.sql
backend/src/main/resources/application.yml
backend/src/main/java/com/ivan/nexus/
  domain/traffic/
    HttpAccessEvent.java
    TrafficMinuteBucket.java
    TrafficSeriesPoint.java
    TrafficServiceBreakdown.java
    TrafficProjectRanking.java
    TrafficReport.java
    TrafficOverview.java
    TrafficSnapshot.java
    TrafficSpikeRules.java
  domain/alert/AlertType.java
  application/traffic/
    TrafficStore.java
    TrafficIngestor.java
    MinuteTrafficIngestor.java
    ResolveTrafficTarget.java
    GetProjectTraffic.java
    GetGlobalTraffic.java
    EvaluateTrafficAlerts.java
    EnforceTrafficRetention.java
  infrastructure/traffic/
    CaddyJsonAccessLogParser.java
    CaddyAccessLogFollower.java
    TrafficMinuteEntity.java
    TrafficMinuteJpaRepository.java
    JpaTrafficStore.java
  infrastructure/config/NexusProperties.java
  interfaces/traffic/TrafficController.java
frontend/
  components/app-shell.tsx
  types/api.ts
  app/(app)/traffic/page.tsx
  features/traffic/traffic-page.tsx
  features/traffic/traffic-chart.tsx
  features/traffic/series.ts
  features/traffic/series.test.ts
  features/projects/project-overview.tsx
```

Delete after replacement: `HourlyTrafficIngestor.java`. Keep unused `TrafficHourly*` JPA classes.

---

### Task 1: Minute bucket domain

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/domain/traffic/TrafficMinuteBucket.java`
- Create: `backend/src/test/java/com/ivan/nexus/domain/traffic/TrafficMinuteBucketTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ivan.nexus.domain.traffic;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficMinuteBucketTest {
    @Test
    void ingestAccumulatesStatusBytesAndLatency() {
        Instant start = Instant.parse("2026-09-18T10:31:00Z");
        TrafficMinuteBucket bucket = TrafficMinuteBucket.empty(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        "lab", "web", "app.example.com", start)
                .ingest(200, 100, 20)
                .ingest(500, 50, 80);

        assertThat(bucket.requests()).isEqualTo(2);
        assertThat(bucket.bytesOut()).isEqualTo(150);
        assertThat(bucket.status2xx()).isEqualTo(1);
        assertThat(bucket.status5xx()).isEqualTo(1);
        assertThat(bucket.latencyAvgMs()).isEqualTo(50.0);
        assertThat(bucket.latencyMaxMs()).isEqualTo(80.0);
    }

    @Test
    void normalizeBlanks() {
        TrafficMinuteBucket bucket = TrafficMinuteBucket.empty(
                UUID.randomUUID(), "lab", null, null, Instant.parse("2026-09-18T10:31:00Z"));
        assertThat(bucket.serviceId()).isEqualTo("app");
        assertThat(bucket.host()).isEqualTo("");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q test -Dmaven.repo.local=/home/ibetanzos/.m2/repository -Dtest=TrafficMinuteBucketTest`

Expected: FAIL cannot find `TrafficMinuteBucket`

- [ ] **Step 3: Write minimal implementation**

Record fields: `id, projectId, serviceId, host, bucketStart, requests, bytesIn, bytesOut, status2xx, status3xx, status4xx, status5xx, latencyAvgMs, latencyMaxMs`.

```java
public TrafficMinuteBucket ingest(int status, long bytes, long latencyMs) {
    long nextRequests = requests + 1;
    long nextBytesOut = bytesOut + Math.max(0, bytes);
    long next2xx = status2xx + (status >= 200 && status < 300 ? 1 : 0);
    long next3xx = status3xx + (status >= 300 && status < 400 ? 1 : 0);
    long next4xx = status4xx + (status >= 400 && status < 500 ? 1 : 0);
    long next5xx = status5xx + (status >= 500 && status < 600 ? 1 : 0);
    double nextAvg = ((latencyAvgMs * requests) + latencyMs) / nextRequests;
    double nextMax = latencyMaxMs == null ? latencyMs : Math.max(latencyMaxMs, latencyMs);
    return new TrafficMinuteBucket(
            id, projectId, serviceId, host, bucketStart,
            nextRequests, bytesIn, nextBytesOut,
            next2xx, next3xx, next4xx, next5xx,
            nextAvg, nextMax);
}

public static String normalizeServiceId(String serviceId) {
    return serviceId == null || serviceId.isBlank() ? "app" : serviceId.trim();
}

public static String normalizeHost(String host) {
    return host == null ? "" : host.trim().toLowerCase();
}
```

`empty(...)` zeros counters and sets `latencyMaxMs` to null. Constants: `UNMAPPED_PROJECT = "_unmapped"`, `UNKNOWN_SERVICE = "_unknown"`.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/domain/traffic/TrafficMinuteBucket.java \
  backend/src/test/java/com/ivan/nexus/domain/traffic/TrafficMinuteBucketTest.java
git commit -m "feat: add minute traffic bucket aggregate"
```

---

### Task 2: Snapshot, series binning, report types

**Files:**
- Create: `TrafficSeriesPoint.java`, `TrafficServiceBreakdown.java`, `TrafficProjectRanking.java`, `TrafficReport.java`, `TrafficOverview.java`
- Modify: `TrafficSnapshot.java` — do not reorder the existing record components. Add factory `aggregateMinutes(String projectId, Instant from, Instant to, List<TrafficMinuteBucket> buckets)` that fills existing fields and sets `latencyP95Ms` to the max of `latencyMaxMs` (deploy-delta compatibility).
- Create: `backend/src/test/java/com/ivan/nexus/domain/traffic/TrafficSeriesBinningTest.java`

Series and services live on `TrafficReport` / `TrafficOverview`, not on `TrafficSnapshot`. Global totals use projectId `_global`.

- [ ] **Step 1: Failing test**

```java
@Test
void binsFifteenMinutesWhenWindowExceeds24h() {
    List<TrafficMinuteBucket> minutes = List.of(
            minute("lab", "web", "2026-09-18T10:00:00Z", 10, 0),
            minute("lab", "web", "2026-09-18T10:14:00Z", 5, 1),
            minute("lab", "api", "2026-09-18T10:15:00Z", 7, 0));
    List<TrafficSeriesPoint> series = TrafficSeriesPoint.bin(minutes, Duration.ofMinutes(15));
    assertThat(series).hasSize(2);
    assertThat(series.getFirst().requests()).isEqualTo(15);
    assertThat(series.getFirst().status5xx()).isEqualTo(1);
}

@Test
void reportSplitsServices() {
    TrafficReport report = TrafficReport.fromMinutes(
            "lab",
            Instant.parse("2026-09-18T10:00:00Z"),
            Instant.parse("2026-09-18T11:00:00Z"),
            minutes,
            Duration.ofMinutes(1));
    assertThat(report.totals().requests()).isEqualTo(22);
    assertThat(report.services()).extracting(TrafficServiceBreakdown::serviceId)
            .containsExactly("web", "api");
}
```

`TrafficSeriesPoint.bin`: map each `bucketStart` to epoch-second `/ binSeconds * binSeconds`. Sum requests/5xx/bytes; request-weighted avg latency; max of max.

`TrafficReport.fromMinutes` calls `TrafficSnapshot.aggregateMinutes`, `bin`, and groups by `serviceId`.

`TrafficOverview.fromMinutes` ranks projects by requests descending.

- [ ] **Step 2: Run test, expect FAIL**
- [ ] **Step 3: Implement types and factories**
- [ ] **Step 4: PASS**
- [ ] **Step 5: Commit** `feat: bin minute traffic into reports and series`

---

### Task 3: Spike predicates

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/domain/traffic/TrafficSpikeRules.java`
- Create: `backend/src/test/java/com/ivan/nexus/domain/traffic/TrafficSpikeRulesTest.java`

- [ ] **Step 1: Failing tests**

```java
@Test
void volumeFiresAtThreeTimesMedianWhenAtLeast30Requests() {
    assertThat(TrafficSpikeRules.volumeSpike(90, baselineOf(30, 12))).isTrue();
}

@Test
void volumeDoesNotFireBelow30Requests() {
    assertThat(TrafficSpikeRules.volumeSpike(29, baselineOf(1, 12))).isFalse();
}

@Test
void volumeDoesNotFireWithThinBaseline() {
    assertThat(TrafficSpikeRules.volumeSpike(90, baselineOf(30, 11))).isFalse();
}

@Test
void volumeResolvesBelowOnePointFiveMedian() {
    assertThat(TrafficSpikeRules.volumeResolved(44, baselineOf(30, 12))).isTrue();
}

@Test
void fiveXxFiresOnRateOrAbsolute() {
    assertThat(TrafficSpikeRules.fiveXxSpike(10, 1)).isTrue();
    assertThat(TrafficSpikeRules.fiveXxSpike(100, 10)).isTrue();
    assertThat(TrafficSpikeRules.fiveXxSpike(9, 0)).isFalse();
}

@Test
void fiveXxResolvesUnderTwoPercentAndUnder10() {
    assertThat(TrafficSpikeRules.fiveXxResolved(100, 1)).isTrue();
    assertThat(TrafficSpikeRules.fiveXxResolved(100, 3)).isFalse();
}
```

Constants: `MIN_VOLUME_REQUESTS=30`, `VOLUME_MULTIPLIER=3.0`, `VOLUME_RESOLVE_MULTIPLIER=1.5`, `MIN_BASELINE_SAMPLES=12`, `MIN_5XX_REQUESTS=10`, `FIVE_XX_RATE=0.05`, `FIVE_XX_ABSOLUTE=10`, `FIVE_XX_RESOLVE_RATE=0.02`.

Median: sort a copy; even size averages the two middle values as double.

```java
public static boolean sameUtcMinuteOfDay(Instant a, Instant b) {
    OffsetDateTime da = a.atOffset(ZoneOffset.UTC);
    OffsetDateTime db = b.atOffset(ZoneOffset.UTC);
    return da.getHour() == db.getHour() && da.getMinute() == db.getMinute();
}
```

- [ ] **Step 2: FAIL**
- [ ] **Step 3: Implement `TrafficSpikeRules`**
- [ ] **Step 4: PASS**
- [ ] **Step 5: Commit** `feat: encode traffic volume and 5xx spike rules`

---

### Task 4: Flyway plus JPA minute store

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__traffic_minute.sql`
- Create: `TrafficMinuteEntity.java`, `TrafficMinuteJpaRepository.java`
- Modify: `TrafficStore.java`, `JpaTrafficStore.java`, `JpaTrafficStoreTest.java`

SQL:

```sql
CREATE TABLE traffic_minute (
    id UUID PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    service_id VARCHAR(128) NOT NULL,
    host VARCHAR(253) NOT NULL DEFAULT '',
    bucket_start TIMESTAMPTZ NOT NULL,
    requests BIGINT NOT NULL DEFAULT 0,
    bytes_in BIGINT NOT NULL DEFAULT 0,
    bytes_out BIGINT NOT NULL DEFAULT 0,
    status_2xx BIGINT NOT NULL DEFAULT 0,
    status_3xx BIGINT NOT NULL DEFAULT 0,
    status_4xx BIGINT NOT NULL DEFAULT 0,
    status_5xx BIGINT NOT NULL DEFAULT 0,
    latency_avg_ms DOUBLE PRECISION NOT NULL DEFAULT 0,
    latency_max_ms DOUBLE PRECISION,
    CONSTRAINT uq_traffic_minute_bucket UNIQUE (project_id, service_id, host, bucket_start)
);
CREATE INDEX idx_traffic_minute_bucket ON traffic_minute (bucket_start);
CREATE INDEX idx_traffic_minute_project_bucket
    ON traffic_minute (project_id, bucket_start DESC);
```

`TrafficStore`:

```java
Optional<TrafficMinuteBucket> findBucket(String projectId, String serviceId, String host, Instant bucketStart);
TrafficMinuteBucket save(TrafficMinuteBucket bucket);
List<TrafficMinuteBucket> findByProjectSince(String projectId, Instant since);
List<TrafficMinuteBucket> findSince(Instant since);
TrafficSnapshot snapshot(String projectId, Instant from, Instant to);
int deleteOlderThan(Instant cutoff);
```

Repository: unique-key finder, `findByProjectIdAndBucketStartGreaterThanEqualOrderByBucketStartAsc`, `findByBucketStartGreaterThanEqualOrderByBucketStartAsc`, modifying delete `bucketStart < cutoff`.

`JpaTrafficStore.snapshot`: buckets with `from <= bucketStart < to`, then `TrafficSnapshot.aggregateMinutes`.

Leave `TrafficHourlyEntity` and `TrafficHourlyJpaRepository` unused.

This task will not compile until Task 5 rewrites the ingestor. Either keep a temporary unused hourly adapter or do Task 4 and Task 5 in the same commit if compilation breaks. Prefer one commit spanning 4+5 if `HourlyTrafficIngestor` still implements the old `TrafficStore`.

- [ ] **Step 1: Update `JpaTrafficStoreTest` to minute entity**
- [ ] **Step 2: FAIL**
- [ ] **Step 3: Entity, repository, store, SQL**
- [ ] **Step 4: PASS `JpaTrafficStoreTest`**
- [ ] **Step 5: Commit** `feat: persist traffic_minute buckets`

---

### Task 5: Minute ingestor

**Files:**
- Create: `MinuteTrafficIngestor.java`
- Modify: `TrafficIngestor.java` to a single method `ingestRaw(String projectId, String serviceId, String host, int status, long bytes, long latencyMs)`
- Delete: `HourlyTrafficIngestor.java`
- Rewrite: `HourlyTrafficIngestorTest.java` into `MinuteTrafficIngestorTest.java`
- Update any FakeTrafficStore in `CorrelateDeployTrafficTest` / `HourlyTrafficIngestorTest`

```java
Instant bucketStart = clock.instant().truncatedTo(ChronoUnit.MINUTES);
String service = TrafficMinuteBucket.normalizeServiceId(serviceId);
String normalizedHost = TrafficMinuteBucket.normalizeHost(host);
TrafficMinuteBucket bucket = store.findBucket(projectId, service, normalizedHost, bucketStart)
        .orElseGet(() -> TrafficMinuteBucket.empty(
                UUID.randomUUID(), projectId, service, normalizedHost, bucketStart));
store.save(bucket.ingest(status, bytes, latencyMs));
```

Test clock `2026-09-18T10:31:40Z` must land in bucket `10:31:00Z`.

- [ ] Commit `feat: ingest Caddy events into the current UTC minute`

---

### Task 6: Resolve host to project/service

**Files:**
- Create: `ResolveTrafficTarget.java` with record `ResolvedTarget(String projectId, String serviceId, String host)`
- Create: `ResolveTrafficTargetTest.java`

```java
public ResolvedTarget execute(String rawHost) {
    String host = TrafficMinuteBucket.normalizeHost(stripPort(rawHost));
    if (host.isBlank()) {
        return new ResolvedTarget(
                TrafficMinuteBucket.UNMAPPED_PROJECT,
                TrafficMinuteBucket.UNKNOWN_SERVICE,
                "");
    }
    return domains.findByHostname(host)
            .map(d -> new ResolvedTarget(
                    d.projectId(),
                    TrafficMinuteBucket.normalizeServiceId(d.serviceName()),
                    host))
            .orElse(new ResolvedTarget(
                    TrafficMinuteBucket.UNMAPPED_PROJECT,
                    TrafficMinuteBucket.UNKNOWN_SERVICE,
                    host));
}
```

`stripPort`: if the suffix after the last colon is all digits, drop it.

Tests: mapped domain; unknown host; blank; `0nexus.duckdns.org:443`.

- [ ] Commit `feat: map access-log hosts to project domains`

---

### Task 7: Caddy JSON parser

**Files:**
- Create: `domain/traffic/HttpAccessEvent.java` with `host, status, bytesOut, latencyMs`
- Create: `infrastructure/traffic/CaddyJsonAccessLogParser.java`
- Create: `CaddyJsonAccessLogParserTest.java`

Use `ObjectMapper.readTree`. Skip if the payload does not start with `{`. Fields:

- host: `request.host` else `request.headers.Host[0]`
- status: `status`
- size: `size` else 0
- duration: `duration` in **seconds**, convert with `Math.round(duration * 1000)`

If the line matches a docker timestamp prefix (`2026-09-18T10:31:00.000000000Z `), take the substring after the first space.

Return `Optional<HttpAccessEvent>`. Console lines: empty.

- [ ] Commit `feat: parse Caddy JSON access log lines`

---

### Task 8: Log follower

**Files:**
- Create: `infrastructure/traffic/CaddyAccessLogFollower.java`
- Create: `CaddyAccessLogFollowerTest.java` (no live Docker)

- `@Component` `@ConditionalOnProperty(name="nexus.traffic.ingest-enabled", havingValue="true", matchIfMissing=true)`
- `@PostConstruct` daemon thread `nexus-caddy-traffic`
- `@PreDestroy` close callback / interrupt
- Loop: resolve container, follow with `withFollowStream(true)`, `withTimestamps(true)`, `withTail(0)`, `withSince((int) Instant.now().getEpochSecond())`
- Each line: parse, `resolve.execute(host)`, `ingestor.ingestRaw(...)`; catch per line
- Container: property `nexus.traffic.caddy-container` if set; else running list, label `com.docker.compose.service=caddy`; else name `caddy` or regex `.*[-_]caddy[-_]1`
- Missing container: warn, backoff 1s..30s, retry. Do not throw `CONTAINER_NOT_FOUND`.

Package-visible `selectContainer` for the unit test.

- [ ] Commit `feat: follow the Caddy container for HTTP access logs`

---

### Task 9: Query use cases and hours validation

**Files:**
- Modify: `GetProjectTraffic.java` — return `TrafficReport`. If hours is outside 1..168 throw `new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED, "hours must be between 1 and 168")`. Window: `from = to.minus(hours, ChronoUnit.HOURS)` without truncating to the hour.
- Create: `GetGlobalTraffic.java` — `execute(int hours)` returns `TrafficOverview` from `store.findSince(from)`
- Bin: `hours <= 24 ? Duration.ofMinutes(1) : Duration.ofMinutes(15)`
- Tests: `GetProjectTrafficTest`, `GetGlobalTrafficTest`

- [ ] Commit `feat: query global and project traffic series from minutes`

---

### Task 10: REST

**Files:**
- Modify: `TrafficController.java`, `TrafficControllerTest.java`

```java
@GetMapping("/api/traffic")
public GlobalTrafficResponse global(@RequestParam(defaultValue = "24") int hours) {
    return GlobalTrafficResponse.from(getGlobalTraffic.execute(hours));
}

@GetMapping("/api/projects/{id}/traffic")
public ProjectTrafficResponse traffic(
        @PathVariable("id") String projectId,
        @RequestParam(defaultValue = "24") int hours) {
    return ProjectTrafficResponse.from(getProjectTraffic.execute(projectId, hours));
}
```

`ProjectTrafficResponse`: existing snapshot fields plus `latencyMaxMs` (same numeric as max-of-max / current `latencyP95Ms`), `series`, `services`. Keep `topEndpoints` (may be empty).

`GlobalTrafficResponse`: `from, to, hours, requests, bytesOut, status2xx, status4xx, status5xx, latencyAvgMs, latencyMaxMs, series, projects`.

Tests: VIEWER GET `/api/traffic`; project JSON has `$.series`; `hours=0` and `hours=169` return 400 `OPERATION_NOT_ALLOWED`.

Mockito `GetGlobalTraffic`. Keep deploy-delta test.

- [ ] Commit `feat: expose global and project traffic series APIs`

---

### Task 11: Alert types and evaluator

**Files:**
- Modify: `AlertType.java` — append `TRAFFIC_SPIKE`, `TRAFFIC_5XX_SPIKE`
- Create: `EvaluateTrafficAlerts.java` with `@Scheduled(fixedDelayString="${nexus.traffic.alert-interval-ms:60000}")`
- Create: `EvaluateTrafficAlertsTest.java`
- Grep and update any test that lists every `AlertType`

`execute()`:

1. `closed = now.truncatedTo(MINUTES).minus(1, MINUTES)`
2. `buckets = store.findSince(closed.minus(7, DAYS))`
3. Per `projectId`: sum the closed minute; baseline = other buckets with `sameUtcMinuteOfDay`; fire or resolve volume
4. Per `projectId` plus `serviceId`: 5xx fire/resolve
5. `persistAlertEvaluation.persist(new AlertEvaluation(firings, resolveKeys), rules.findEnabled(), now)`
6. try/catch per project

`AlertKey(TRAFFIC_SPIKE, projectId, null)` and `AlertKey(TRAFFIC_5XX_SPIKE, projectId, serviceId)`.

Seeder inserts missing global types automatically.

- [ ] Commit `feat: alert on traffic volume and 5xx spikes`

---

### Task 12: Retention and config

**Files:**
- Create: `EnforceTrafficRetention.java` `@Scheduled(cron="${nexus.traffic.retention-cron:0 15 3 * * *}")` calling `store.deleteOlderThan(clock.instant().minus(7, DAYS))`
- Create: `EnforceTrafficRetentionTest.java`
- Modify: `NexusProperties` — add inner class `Traffic` and `getTraffic()`
- Modify: `application.yml`

```yaml
  traffic:
    ingest-enabled: ${NEXUS_TRAFFIC_INGEST_ENABLED:true}
    caddy-container: ${NEXUS_TRAFFIC_CADDY_CONTAINER:}
    retention-days: 7
    retention-cron: ${NEXUS_TRAFFIC_RETENTION_CRON:0 15 3 * * *}
    alert-interval-ms: ${NEXUS_TRAFFIC_ALERT_INTERVAL_MS:60000}
```

Follower reads `NexusProperties.getTraffic()`.

- [ ] Commit `feat: drop traffic minutes older than seven days`

---

### Task 13: Frontend charts and `/traffic`

**Files:**
- Modify: `frontend/components/app-shell.tsx` — `{ href: '/traffic', label: 'Traffic' }`; `wide` also when pathname includes `/traffic`
- Modify: `frontend/types/api.ts` — `TrafficSeriesPoint`, `TrafficOverview`, `ProjectTraffic`
- Create: `frontend/features/traffic/series.ts` with `polylinePoints(series, field, width, height)`
- Create: `frontend/features/traffic/series.test.ts`
- Create: `frontend/features/traffic/traffic-chart.tsx`
- Create: `frontend/features/traffic/traffic-page.tsx`
- Create: `frontend/app/(app)/traffic/page.tsx`

`traffic-page.tsx`: query `/api/traffic?hours=` with toggle 24 / 168; `/api/alerts?status=ACTIVE` filter types starting with `TRAFFIC_`; totals; two charts; project table linking `/projects/${id}`. 5xx total uses `text-[#ff4d4f]` when greater than 0. Empty: `No traffic yet`.

Chart: SVG `viewBox="0 0 640 160"`, grid stroke `#2a2a2a`, polyline `fill="none"` `strokeWidth={1.5}`, no gradients. Requests stroke `#f5f5f5`, 5xx `#ff4d4f`.

Vitest: empty series returns empty string; two points increase x.

- [ ] Commit `feat: add Traffic page with request and 5xx charts`

---

### Task 14: Project overview Traffic block

**Files:**
- Modify: `frontend/features/projects/project-overview.tsx`

Query key `['projects', projectId, 'traffic']` against `/api/projects/${projectId}/traffic?hours=24`.

Section after Health: heading TRAFFIC, compact requests chart, 5xx count, service rows (`serviceId`, requests, 5xx), link to `/traffic` labeled `Full traffic`. Empty: `No traffic yet`.

- [ ] Commit `feat: show 24h traffic on the project overview`

---

### Task 15: Hexagonal and verification

- [ ] Run backend tests listed below and frontend `npx vitest run features/traffic`
- [ ] Grep `HourlyTrafficIngestor` — gone
- [ ] Grep `NEXUS_TRAFFIC_INGEST_ENABLED` in `application.yml`
- [ ] ArchUnit green (parser/follower stay in infrastructure)

Backend test list:

`TrafficMinuteBucketTest,TrafficSeriesBinningTest,TrafficSpikeRulesTest,JpaTrafficStoreTest,MinuteTrafficIngestorTest,ResolveTrafficTargetTest,CaddyJsonAccessLogParserTest,CaddyAccessLogFollowerTest,GetProjectTrafficTest,GetGlobalTrafficTest,EvaluateTrafficAlertsTest,EnforceTrafficRetentionTest,TrafficControllerTest,HexagonalArchitectureTest,CorrelateDeployTrafficTest`

- [ ] Commit leftover fixes: `test: cover traffic ingest dashboard and spike alerts`

---

## Spec coverage

| Spec section | Task |
|---|---|
| Caddy docker follow | 8 |
| Host map DomainStore | 6 |
| Minute upsert | 1, 5 |
| 7-day retention | 12 |
| TRAFFIC_SPIKE / 5xx | 3, 11 |
| GET /api/traffic and project series | 9, 10 |
| deploy-delta minutes | 4-5 snapshot |
| /traffic UI plus SVG | 13 |
| Project block | 14 |
| Flag default true | 12 |
| hours 1..168 | 9-10 |
| Closed minute only | 11 |
| Unmapped hosts | 6 |
| No raw logs / no chart lib | 7, 13 |
| Keep traffic_hourly table | 4 |

## Type names (lock)

`TrafficMinuteBucket`, `HttpAccessEvent`, `TrafficSeriesPoint`, `TrafficServiceBreakdown`, `TrafficProjectRanking`, `TrafficReport`, `TrafficOverview`, `ResolveTrafficTarget.ResolvedTarget`, `TrafficSpikeRules`, `MinuteTrafficIngestor`, `GetGlobalTraffic`, `EvaluateTrafficAlerts`, `EnforceTrafficRetention`, `CaddyJsonAccessLogParser`, `CaddyAccessLogFollower`.

Do not revive `HourlyTrafficIngestor`. Do not label UI latency as p95.
