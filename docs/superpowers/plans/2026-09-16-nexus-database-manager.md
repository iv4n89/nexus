# Nexus Database Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do this **after** MVP Slices 1–4 at minimum (auth, Docker inventory, project pages). Sequence in the master plan: **Slice 9** after Slice 6 (deploy demoable). Re-read `docs/superpowers/specs/2026-09-16-nexus-database-manager-design.md` before coding.

**Goal:** Add an in-app project database manager (explorer + query) for Postgres, MySQL/MariaDB, and Mongo containers belonging to the open Nexus project.

**Architecture:** Auto-detect engine and credentials from Docker inspect on the server. JDBC `DriverManager` and Mongo `MongoClients.create` open a short-lived connection per request. The browser never receives URIs or passwords. VIEWER is enforced by statement classifiers, not only by Spring `hasRole`.

**Tech Stack:** Java 21, Spring Boot 3.5.16, `org.postgresql:postgresql`, `com.mysql:mysql-connector-j`, `org.mongodb:mongodb-driver-sync`, Next.js App Router, TanStack Query.

**Design:** `docs/superpowers/specs/2026-09-16-nexus-database-manager-design.md`

---

## Phase 0 — Allowed APIs

| Need | API | Source |
|---|---|---|
| Postgres connect | `DriverManager.getConnection("jdbc:postgresql://host:port/db", user, password)` plus `Properties` `options=-c statement_timeout=10000` | https://github.com/pgjdbc/pgjdbc README + use.md |
| Postgres timeout | URL/options `statement_timeout` **and** `Statement.setQueryTimeout(10)` | pgjdbc use.md / `PgStatement` |
| MySQL connect | `DriverManager.getConnection("jdbc:mysql://host:port/db", user, password)` | MySQL Connector/J |
| MySQL timeout | `Statement.setQueryTimeout(10)` | JDBC `Statement` |
| SQL run | `createStatement()`, `executeQuery` for READ, `executeUpdate` for writes; `ResultSetMetaData` for columns | JDK 21 JDBC |
| Mongo connect | `MongoClients.create("mongodb://user:pass@host:port/admin")` | https://github.com/mongodb/mongo-java-driver MongoClients |
| Mongo read | `getDatabase`, `getCollection`, `find(Document).limit(n).maxTime(10, SECONDS)`, `aggregate(pipeline).maxTime(...)` | mongo-java-driver |
| Mongo write | `insertOne`, `updateOne`/`updateMany`, `deleteOne`/`deleteMany` | mongo-java-driver |
| Mongo list | `listDatabaseNames()`, `listCollectionNames()` | mongo-java-driver |
| JSON | Spring `ObjectMapper.readValue` / `writeValueAsString` (existing JSON mapper, not YAML) | Boot JSON starter |

**Forbidden:** Adminer/CloudBeaver, Spring Data Mongo repositories, Hikari pool against managed DBs, logging the JDBC/Mongo URI, putting `env` on container REST DTOs, `csrf.spa()`, free-form Mongo JS.

**Security change (required):** MVP `POST /api/**` is ADMIN-only. VIEWER must `POST` `/api/projects/*/database/instances/*/query` so the classifier can allow READ. `/cell` stays ADMIN.

**Host network:** production backend `network_mode: host`. Nexus own Postgres publishes `5432:5432`. `DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/nexus`. Nginx `proxy_pass http://127.0.0.1:8080`. Local `spring-boot:run` on the host already reaches published lab ports.

---

## File map

```text
backend/src/main/java/com/ivan/nexus/
  domain/database/
    DatabaseEngine.java
    DatabaseStatus.java
    DatabaseInstance.java
    StatementClass.java
    ResolvedTarget.java          # host, port, user, password, defaultDatabase — never a REST type
    EngineDetector.java
    EnvCredentialParser.java
    SqlStatementClassifier.java
    MongoStatementClassifier.java
    SqlIdentifierQuoter.java
  application/database/
    DiscoverProjectDatabases.java
    GetDatabaseMetadata.java
    PreviewTable.java
    RunDatabaseQuery.java
    EditDatabaseCell.java
    ProjectDatabaseAccess.java   # resolve instance or throw
  application/project/
    ContainerInspect.java        # add if missing
    ContainerInventory.java      # add inspect()
  infrastructure/docker/
    DockerContainerInventory.java
  infrastructure/database/
    JdbcQueryExecutor.java
    MongoQueryExecutor.java
    SecretSanitizer.java
  interfaces/database/
    DatabaseController.java
    DatabaseDtos.java
frontend/
  app/projects/[id]/database/page.tsx
  features/database/
    database-page.tsx
    instance-select.tsx
    schema-tree.tsx
    data-grid.tsx
    query-editor.tsx
    confirm-destructive.tsx
    classify-sql.ts
    api.ts
projects/lab/docker-compose.yml   # add db engines with published ports
deployment/docker-compose.yml     # host network for backend
```

---

## Prerequisite types (must already exist from MVP)

`ContainerSnapshot` at minimum: `id`, `name`, `image`, `labels`.

Add `ContainerInspect` (this plan):

```java
public record PublishedPort(int privatePort, Integer hostPort, String hostIp) {}

public record ContainerInspect(
    String id,
    String image,
    Map<String, String> labels,
    Map<String, String> env,
    List<PublishedPort> publishedPorts,
    List<String> networkIps
) {}
```

`ContainerInventory.inspect(String containerId)` returns `Optional<ContainerInspect>`. Map Docker inspect `Config.Env` (`KEY=VALUE`) into `env`. Map `NetworkSettings.Ports` into `publishedPorts`. Map each network `IPAddress` into `networkIps`. **Do not** add `env` to any class returned by `ContainerController`.

`NexusErrorCode` — add: `DATABASE_NOT_FOUND`, `DATABASE_UNREACHABLE`, `QUERY_NOT_ALLOWED`, `CONFIRMATION_REQUIRED`, `QUERY_FAILED`, `QUERY_TIMEOUT`.

HTTP mapping in `GlobalExceptionHandler`:

| Code | Status |
|---|---|
| DATABASE_NOT_FOUND | 404 |
| DATABASE_UNREACHABLE | 503 |
| QUERY_NOT_ALLOWED | 403 |
| CONFIRMATION_REQUIRED | 409 |
| QUERY_FAILED | 400 |
| QUERY_TIMEOUT | 504 |

`AuditAction` — add `DB_QUERY`, `DB_CELL_EDIT`.

`DomainException` already carries `NexusErrorCode` + message.

---

### Task 1: Error codes, audit actions, sanitizer

**Files:**
- Modify: `backend/src/main/java/com/ivan/nexus/domain/shared/NexusErrorCode.java`
- Modify: `backend/src/main/java/com/ivan/nexus/interfaces/error/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/ivan/nexus/domain/audit/AuditAction.java`
- Create: `backend/src/main/java/com/ivan/nexus/infrastructure/database/SecretSanitizer.java`
- Test: `backend/src/test/java/com/ivan/nexus/infrastructure/database/SecretSanitizerTest.java`

- [ ] **Step 1: Failing sanitizer test**

```java
@Test
void stripsPasswordFromJdbcStyleMessage() {
    String raw = "FATAL: password authentication failed for user \"app\" jdbc:postgresql://10.0.0.2:5432/app?password=s3cret";
    String clean = SecretSanitizer.strip("s3cret", raw);
    assertFalse(clean.contains("s3cret"));
    assertFalse(clean.contains("jdbc:postgresql"));
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=SecretSanitizerTest`  
  Expected: FAIL (class missing)

- [ ] **Step 3: Implement**

```java
public final class SecretSanitizer {
    private SecretSanitizer() {}

    public static String strip(String secret, String message) {
        if (message == null) {
            return "Query failed";
        }
        String out = message;
        if (secret != null && !secret.isBlank()) {
            out = out.replace(secret, "***");
        }
        out = out.replaceAll("jdbc:[^\\s]+", "[uri]");
        out = out.replaceAll("mongodb(\\+srv)?://[^\\s]+", "[uri]");
        return out;
    }
}
```

- [ ] **Step 4: Add enum constants and handler mappings listed above. Tests pass.**

- [ ] **Step 5: Commit** `feat: add database manager error codes and secret sanitizer`

---

### Task 2: Engine detector (TDD)

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/DatabaseEngine.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/EngineDetector.java`
- Test: `backend/src/test/java/com/ivan/nexus/domain/database/EngineDetectorTest.java`

```java
public enum DatabaseEngine { POSTGRES, MYSQL, MONGO }
```

- [ ] **Step 1: Failing tests**

```java
@Test
void postgresOfficial() {
    assertEquals(Optional.of(DatabaseEngine.POSTGRES), EngineDetector.fromImage("postgres:16-alpine"));
}

@Test
void bitnamiPostgres() {
    assertEquals(Optional.of(DatabaseEngine.POSTGRES), EngineDetector.fromImage("bitnami/postgresql:16"));
}

@Test
void mysqlAndMariadb() {
    assertEquals(Optional.of(DatabaseEngine.MYSQL), EngineDetector.fromImage("mysql:8"));
    assertEquals(Optional.of(DatabaseEngine.MYSQL), EngineDetector.fromImage("mariadb:11"));
}

@Test
void mongo() {
    assertEquals(Optional.of(DatabaseEngine.MONGO), EngineDetector.fromImage("mongo:7"));
    assertEquals(Optional.of(DatabaseEngine.MONGO), EngineDetector.fromImage("bitnami/mongodb:7"));
}

@Test
void ignoresApps() {
    assertTrue(EngineDetector.fromImage("nginx:alpine").isEmpty());
    assertTrue(EngineDetector.fromImage("redis:7").isEmpty());
    assertTrue(EngineDetector.fromImage("nexus-backend:latest").isEmpty());
}
```

- [ ] **Step 2: Run** `./mvnw test -Dtest=EngineDetectorTest` — FAIL

- [ ] **Step 3: Implement** — take last path segment before `:`, lowercase, then:

1. if contains `postgresql` or equals/contains `postgres` → POSTGRES (`postgresql` first)
2. else if contains `mariadb` or `mysql` → MYSQL
3. else if contains `mongodb` or matches `mongo` as name/prefix → MONGO (`mongodb` before bare `mongo`)
4. else empty

`nexus-backend` has no db token → empty.

- [ ] **Step 4: Tests PASS. Commit** `feat: detect database engine from container image`

---

### Task 3: Env credential parser (TDD)

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/EnvCredentialParser.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/ParsedCredentials.java`
- Test: `backend/src/test/java/com/ivan/nexus/domain/database/EnvCredentialParserTest.java`

```java
public record ParsedCredentials(String username, String password, String defaultDatabase, boolean reachable) {}
```

`reachable=false` when the design requires a password and it is missing. Never log this record.

- [ ] **Step 1: Tests**

```java
@Test
void postgresRequiresPassword() {
    var ok = EnvCredentialParser.parse(DatabaseEngine.POSTGRES, Map.of(
        "POSTGRES_USER", "app", "POSTGRES_PASSWORD", "p", "POSTGRES_DB", "app"));
    assertTrue(ok.reachable());
    assertEquals("app", ok.username());
    var bad = EnvCredentialParser.parse(DatabaseEngine.POSTGRES, Map.of("POSTGRES_DB", "app"));
    assertFalse(bad.reachable());
}

@Test
void mysqlPrefersUserThenRoot() {
    var user = EnvCredentialParser.parse(DatabaseEngine.MYSQL, Map.of(
        "MYSQL_USER", "u", "MYSQL_PASSWORD", "p", "MYSQL_DATABASE", "d"));
    assertEquals("u", user.username());
    var root = EnvCredentialParser.parse(DatabaseEngine.MYSQL, Map.of(
        "MYSQL_ROOT_PASSWORD", "r", "MYSQL_DATABASE", "d"));
    assertEquals("root", root.username());
    assertEquals("r", root.password());
}

@Test
void mariadbAliases() {
    var parsed = EnvCredentialParser.parse(DatabaseEngine.MYSQL, Map.of(
        "MARIADB_USER", "u", "MARIADB_PASSWORD", "p", "MARIADB_DATABASE", "d"));
    assertTrue(parsed.reachable());
}

@Test
void mongoOptionalAuth() {
    var anon = EnvCredentialParser.parse(DatabaseEngine.MONGO, Map.of());
    assertTrue(anon.reachable());
    assertEquals("test", anon.defaultDatabase());
    var half = EnvCredentialParser.parse(DatabaseEngine.MONGO, Map.of("MONGO_INITDB_ROOT_USERNAME", "root"));
    assertFalse(half.reachable());
}
```

- [ ] **Step 2: Implement defaults exactly as the design spec §5. Tests PASS.**

- [ ] **Step 3: Commit** `feat: parse database credentials from container env`

---

### Task 4: SQL classifier (TDD)

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/StatementClass.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/SqlStatementClassifier.java`
- Test: `backend/src/test/java/com/ivan/nexus/domain/database/SqlStatementClassifierTest.java`

```java
public enum StatementClass { READ, WRITE, DESTRUCTIVE }
```

Classifier returns `StatementClass` or throws `DomainException(QUERY_NOT_ALLOWED)` for empty / multi-statement.

Strip `--` to EOL and `/* */` first. Trim. Reject if a `;` is followed by a non-empty statement.

- [ ] **Step 1: Tests**

```java
@Test
void selectIsRead() {
    assertEquals(StatementClass.READ, SqlStatementClassifier.classify("SELECT 1"));
}

@Test
void withSelectIsRead() {
    assertEquals(StatementClass.READ, SqlStatementClassifier.classify("WITH x AS (SELECT 1) SELECT * FROM x"));
}

@Test
void selectIntoIsWrite() {
    assertEquals(StatementClass.WRITE, SqlStatementClassifier.classify("SELECT * INTO tmp FROM users"));
}

@Test
void deleteWithWhereIsWrite() {
    assertEquals(StatementClass.WRITE, SqlStatementClassifier.classify("DELETE FROM users WHERE id = 1"));
}

@Test
void deleteWithoutWhereIsDestructive() {
    assertEquals(StatementClass.DESTRUCTIVE, SqlStatementClassifier.classify("DELETE FROM users"));
}

@Test
void dropIsDestructive() {
    assertEquals(StatementClass.DESTRUCTIVE, SqlStatementClassifier.classify("DROP TABLE users"));
}

@Test
void rejectsSecondStatement() {
    assertThrows(DomainException.class, () -> SqlStatementClassifier.classify("SELECT 1; DELETE FROM users"));
}
```

- [ ] **Step 2: Implement**

Leading keyword = first `[A-Za-z]+` after strip. Rules:

- `SELECT`: if `\bINTO\b` or `FOR UPDATE` or `FOR SHARE` → WRITE; else READ
- `WITH`: if `\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE)\b` → classify as that keyword; else READ
- `INSERT|CREATE|ALTER|GRANT|REVOKE|COMMENT` → WRITE
- `DROP|TRUNCATE` → DESTRUCTIVE
- `DELETE` / `UPDATE`: DESTRUCTIVE unless `\bWHERE\b` present → WRITE
- else `QUERY_NOT_ALLOWED`

`\bWHERE\b` / `\bINTO\b` case-insensitive. V1 accepts false positives inside string literals.

- [ ] **Step 3: Tests PASS. Commit** `feat: classify SQL statements for database manager roles`

---

### Task 5: Mongo classifier (TDD)

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/MongoStatement.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/MongoStatementClassifier.java`
- Test: `backend/src/test/java/com/ivan/nexus/domain/database/MongoStatementClassifierTest.java`

```java
public record MongoStatement(
    String op,
    String database,
    String collection,
    String filterJson,
    String projectionJson,
    String pipelineJson,
    String documentJson,
    String updateJson,
    int limit,
    boolean multi,
    StatementClass statementClass,
    boolean requiresConfirmation
) {}
```

Parse with Jackson `JsonNode`. Missing `filter` → `{}`. `limit` default 100, cap 500.

- [ ] **Step 1: Tests**

```java
ObjectMapper mapper = new ObjectMapper();

@Test
void findIsRead() throws Exception {
    var stmt = MongoStatementClassifier.parse(mapper, "{\"op\":\"find\",\"database\":\"app\",\"collection\":\"u\"}");
    assertEquals(StatementClass.READ, stmt.statementClass());
}

@Test
void aggregateOutForbidden() {
    String json = "{\"op\":\"aggregate\",\"database\":\"a\",\"collection\":\"c\",\"pipeline\":[{\"$out\":\"x\"}]}";
    assertThrows(DomainException.class, () -> MongoStatementClassifier.parse(new ObjectMapper(), json));
}

@Test
void deleteEmptyFilterIsDestructive() throws Exception {
    var stmt = MongoStatementClassifier.parse(new ObjectMapper(),
        "{\"op\":\"delete\",\"database\":\"a\",\"collection\":\"c\",\"filter\":{}}");
    assertEquals(StatementClass.DESTRUCTIVE, stmt.statementClass());
    assertTrue(stmt.requiresConfirmation());
}

@Test
void unknownOpRejected() {
    assertThrows(DomainException.class,
        () -> MongoStatementClassifier.parse(new ObjectMapper(), "{\"op\":\"drop\"}"));
}
```

- [ ] **Step 2: Implement**

- `find`, `aggregate` → READ; if pipeline JSON contains `"$out"` or `"$merge"` → `QUERY_NOT_ALLOWED`
- `insert` → WRITE, confirm false
- `update`: empty filter (no fields) → DESTRUCTIVE + confirm; else WRITE
- `delete`: empty filter → DESTRUCTIVE + confirm; else WRITE
- unknown / missing `op`, `database`, `collection` → `QUERY_NOT_ALLOWED`

Empty filter: `filter` missing, `null`, or `{}`.

- [ ] **Step 3: Commit** `feat: classify Mongo operations for database manager`

---

### Task 6: Inspect + exclude Nexus + discover instances

**Files:**
- Modify: `backend/src/main/java/com/ivan/nexus/application/project/ContainerInventory.java`
- Create: `backend/src/main/java/com/ivan/nexus/application/project/ContainerInspect.java`
- Create: `backend/src/main/java/com/ivan/nexus/application/project/PublishedPort.java`
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/docker/DockerContainerInventory.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/DatabaseStatus.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/DatabaseInstance.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/NexusDatabaseExclusions.java`
- Create: `backend/src/main/java/com/ivan/nexus/application/database/DiscoverProjectDatabases.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/ResolvedTarget.java`
- Test: `backend/src/test/java/com/ivan/nexus/application/database/DiscoverProjectDatabasesTest.java`
- Test: `backend/src/test/java/com/ivan/nexus/domain/database/NexusDatabaseExclusionsTest.java`

```java
public enum DatabaseStatus { READY, UNREACHABLE }

public record DatabaseInstance(
    String id,
    String projectId,
    String containerId,
    String service,
    DatabaseEngine engine,
    DatabaseStatus status,
    String defaultDatabase
) {}

public record ResolvedTarget(
    String host,
    int port,
    String username,
    String password,
    String defaultDatabase
) {}
```

Never serialize `ResolvedTarget`.

`NexusDatabaseExclusions.skip(image, labels)` true if `nexus.project`/`com.docker.compose.project` is `nexus`, or image contains `nexus-backend` or `nexus-postgres`.

`databaseId` = `projectId + ":" + containerId.substring(0, 12)` (hex id without `sha256:`).

Endpoint host: if any `PublishedPort` has `hostPort != null` and `privatePort` equals engine default (5432/3306/27017), host=`127.0.0.1`, port=`hostPort`. Else if `networkIps` non-empty, host=first IP, port=default. Else `UNREACHABLE` even if password exists.

Service name: `nexus.service` then `com.docker.compose.service` then container name without `/`.

- [ ] **Step 1: Exclusion + discover tests with a fake inventory** (in-memory list of inspects). Include a postgres in project `lab` (READY) and Nexus postgres (skipped) and nginx (skipped).

- [ ] **Step 2: Implement `DiscoverProjectDatabases.execute(projectId)` using existing `ProjectGrouping` to filter containers, then inspect, detect, parse env, resolve host.** Store `ResolvedTarget` in a request-scoped or method-local map — **not** in `DatabaseInstance`. Keep a package-private `InstanceResolution` record used by later use cases:

```java
public record InstanceResolution(DatabaseInstance instance, ResolvedTarget target) {}
```

`DiscoverProjectDatabases.resolve(projectId, databaseId)` returns `InstanceResolution` or throws `DATABASE_NOT_FOUND`. If status UNREACHABLE, `target` may be null.

- [ ] **Step 3: Fill `inspect` in the Docker adapter from inspect response. Do not add env to public container JSON.**

- [ ] **Step 4: Commit** `feat: discover project database instances from Docker inspect`

---

### Task 7: JDBC executor (Postgres + MySQL)

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/SqlIdentifierQuoter.java`
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/QueryResult.java`
- Create: `backend/src/main/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutor.java`
- Test: `backend/src/test/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutorIT.java`

```java
public record QueryResult(List<String> columns, List<List<Object>> rows, boolean truncated, long durationMs, int rowCount) {}
```

Cell values: `null`, `Number`, `Boolean`, or `String` (`Object.toString()` / JSON for bytes). Never include connection info.

Quoter: Postgres `"` doubled inside; MySQL `` ` `` doubled inside. Reject identifiers matching `[^a-zA-Z0-9_]` after allowing those chars only (quote them anyway). If identifier contains NUL, throw `QUERY_NOT_ALLOWED`.

- [ ] **Step 1: Integration test with Testcontainers `postgres:16-alpine`** (BOM 1.21.4):

```java
@Test
void selectAndCap() throws Exception {
    // create table, insert 3 rows, SELECT * LIMIT 500
    QueryResult result = executor.query(DatabaseEngine.POSTGRES, target, "SELECT n FROM t ORDER BY n", StatementClass.READ, 500);
    assertEquals(List.of("n"), result.columns());
}
```

Use mapped port + `127.0.0.1` + container password.

- [ ] **Step 2: Implement `JdbcQueryExecutor`**

Connect:

```java
Properties props = new Properties();
props.setProperty("user", target.username());
props.setProperty("password", target.password());
props.setProperty("connectTimeout", "5");
if (engine == DatabaseEngine.POSTGRES) {
    props.setProperty("options", "-c statement_timeout=10000");
}
String url = engine == DatabaseEngine.POSTGRES
    ? "jdbc:postgresql://%s:%d/%s".formatted(target.host(), target.port(), target.defaultDatabase())
    : "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true".formatted(target.host(), target.port(), target.defaultDatabase());
try (Connection conn = DriverManager.getConnection(url, props);
     Statement stmt = conn.createStatement()) {
    stmt.setMaxRows(limit + 1);
    stmt.setQueryTimeout(10);
    long start = System.nanoTime();
    // READ: executeQuery. Else executeUpdate and return columns ["updateCount"] one row.
    ...
} catch (SQLTimeoutException e) {
    throw new DomainException(NexusErrorCode.QUERY_TIMEOUT, "Query timed out");
} catch (SQLException e) {
    throw new DomainException(NexusErrorCode.QUERY_FAILED, SecretSanitizer.strip(target.password(), e.getMessage()));
}
```

If `rows.size() > limit`, drop last and `truncated=true`. `limit` is 100 for preview, 500 for query.

Add MySQL connector dependency in `pom.xml`: `com.mysql:mysql-connector-j` (no version, Boot BOM).

- [ ] **Step 3: Metadata helper on the same class**

Postgres tables:

```sql
SELECT table_schema, table_name, table_type
FROM information_schema.tables
WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
ORDER BY 1, 2
```

Columns + PK: `information_schema.columns` and `table_constraints`/`key_column_usage` where `constraint_type = 'PRIMARY KEY'`.

MySQL: same `information_schema`, exclude `mysql`, `sys`, `performance_schema`, `information_schema`.

Preview SQL: `SELECT * FROM quotedSchema.quotedTable LIMIT 100`.

Update cell: `UPDATE quotedSchema.quotedTable SET quotedCol = ? WHERE quotedPk1 = ? AND ...` via `PreparedStatement`. If PK map empty → `QUERY_NOT_ALLOWED`.

- [ ] **Step 4: Commit** `feat: run allowlisted SQL against discovered JDBC databases`

---

### Task 8: Mongo executor

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/infrastructure/database/MongoQueryExecutor.java`
- Test: `backend/src/test/java/com/ivan/nexus/infrastructure/database/MongoQueryExecutorIT.java`

- [ ] **Step 1: Testcontainers `mongo:7`**. Insert two docs, `find` returns columns including `_id`.

- [ ] **Step 2: Implement**

```java
String uri;
if (target.username() == null || target.username().isBlank()) {
    uri = "mongodb://%s:%d".formatted(target.host(), target.port());
} else {
    uri = "mongodb://%s:%s@%s:%d/admin".formatted(
        URLEncoder.encode(target.username(), UTF_8),
        URLEncoder.encode(target.password(), UTF_8),
        target.host(),
        target.port());
}
try (MongoClient client = MongoClients.create(uri)) {
    // find:
    Document filter = Document.parse(stmt.filterJson());
    List<Document> docs = new ArrayList<>();
    collection.find(filter)
        .projection(stmt.projectionJson() == null ? null : Document.parse(stmt.projectionJson()))
        .limit(stmt.limit())
        .maxTime(10, TimeUnit.SECONDS)
        .into(docs);
    // flatten keys union → columns, values JSON strings if Document/List
} catch (MongoInterruptedException | MongoExecutionTimeoutException e) {
    throw new DomainException(NexusErrorCode.QUERY_TIMEOUT, "Query timed out");
} catch (MongoException e) {
    throw new DomainException(NexusErrorCode.QUERY_FAILED, SecretSanitizer.strip(target.password(), e.getMessage()));
}
```

Use `MongoClients.create` from `org.mongodb:mongodb-driver-sync` (add to `pom.xml`, version from Boot BOM if present, else `5.2.1`).

`insertOne(Document.parse(documentJson))`.  
`updateOne`/`updateMany` based on `multi`.  
`deleteOne`/`deleteMany` based on `multi`.

Metadata: `listDatabaseNames()` skip `admin`, `local`, `config`; `listCollectionNames()` per db.

Cell edit: `updateOne(eq("_id", parseId(id)), set(field, value))`. `parseId`: if 24-hex then `new ObjectId(id)` else string.

- [ ] **Step 3: Commit** `feat: run allowlisted Mongo operations against discovered databases`

---

### Task 9: Use cases + REST + VIEWER query matcher

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/application/database/GetDatabaseMetadata.java`
- Create: `backend/src/main/java/com/ivan/nexus/application/database/PreviewTable.java`
- Create: `backend/src/main/java/com/ivan/nexus/application/database/RunDatabaseQuery.java`
- Create: `backend/src/main/java/com/ivan/nexus/application/database/EditDatabaseCell.java`
- Create: `backend/src/main/java/com/ivan/nexus/interfaces/database/DatabaseDtos.java`
- Create: `backend/src/main/java/com/ivan/nexus/interfaces/database/DatabaseController.java`
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/ivan/nexus/application/database/RunDatabaseQueryTest.java`

DTOs (Jackson records):

```java
public record InstanceResponse(String id, String service, DatabaseEngine engine, DatabaseStatus status, String defaultDatabase) {}
public record QueryRequest(String statement, boolean confirmDestructive) {}
public record QueryResponse(List<String> columns, List<List<Object>> rows, boolean truncated, long durationMs, int rowCount) {}
public record CellRequest(
    String schema, String table, Map<String, Object> primaryKey, String column, Object value,
    String mongoDatabase, String collection, String id, String field
) {}
```

`RunDatabaseQuery.execute(projectId, databaseId, statement, confirmDestructive, Role role)`:

1. `resolution = discover.resolve(...)`  
2. if status UNREACHABLE → `DATABASE_UNREACHABLE`  
3. if engine MONGO → parse classifier; else SQL classifier  
4. if role VIEWER and class != READ → `QUERY_NOT_ALLOWED`  
5. if class DESTRUCTIVE or mongo `requiresConfirmation` and !confirmDestructive → `CONFIRMATION_REQUIRED`  
6. run executor  
7. `RecordAudit` `DB_QUERY` with metadata keys from the design (statement truncated 2000)  
8. return `QueryResult`

Role from `Authentication.getAuthorities()` → `ADMIN` if `ROLE_ADMIN`.

- [ ] **Step 1: Unit test fake executor: VIEWER INSERT throws; ADMIN INSERT without confirm for DELETE no WHERE throws CONFIRMATION_REQUIRED; with confirm runs.**

- [ ] **Step 2: Controller**

```java
@RestController
@RequestMapping("/api/projects/{projectId}/database")
public class DatabaseController {
    @GetMapping("/instances")
    public List<InstanceResponse> instances(@PathVariable String projectId) { ... }

    @GetMapping("/instances/{databaseId}/metadata")
    public MetadataResponse metadata(...) { ... }

    @GetMapping("/instances/{databaseId}/preview")
    public QueryResponse preview(..., @RequestParam(required = false) String schema, ...) { ... }

    @PostMapping("/instances/{databaseId}/query")
    public QueryResponse query(..., @RequestBody QueryRequest body, Authentication auth) { ... }

    @PostMapping("/instances/{databaseId}/cell")
    @PreAuthorize("hasRole('ADMIN')")
    public QueryResponse cell(...) { ... }
}
```

If `@PreAuthorize` is used, add `@EnableMethodSecurity`. Alternatively rely on filter chain:

```java
.requestMatchers(HttpMethod.POST, "/api/projects/*/database/instances/*/query").hasAnyRole("ADMIN", "VIEWER")
.requestMatchers(HttpMethod.POST, "/api/projects/*/database/instances/*/cell").hasRole("ADMIN")
```

Place these **before** `.requestMatchers("/api/**").hasRole("ADMIN")`.

`MetadataResponse` for SQL: `{ "engine", "schemas": [ { "name", "tables": [ { "name", "type": "table"|"view", "primaryKey": ["id"] } ] } ] }`. Mongo: `{ "engine", "databases": [ { "name", "collections": ["users"] } ] }`.

- [ ] **Step 3: MockMvc: unauthenticated 401; VIEWER query SELECT 200 with fake; VIEWER query DELETE 403.**

- [ ] **Step 4: Commit** `feat: expose project database manager REST API`

---

### Task 10: Frontend Database page

**Files:**
- Create: `frontend/features/database/api.ts`
- Create: `frontend/features/database/classify-sql.ts`
- Create: `frontend/features/database/database-page.tsx` (`'use client'`)
- Create: `frontend/features/database/instance-select.tsx`
- Create: `frontend/features/database/schema-tree.tsx`
- Create: `frontend/features/database/data-grid.tsx`
- Create: `frontend/features/database/query-editor.tsx`
- Create: `frontend/features/database/confirm-destructive.tsx`
- Create: `frontend/app/projects/[id]/database/page.tsx`
- Modify: project nav in `frontend/components/app-shell.tsx` (or the project layout) to add Database
- Test: `frontend/features/database/classify-sql.test.ts`

Client classifier only disables Execute for VIEWER: treat leading `SELECT`/`WITH` as enabled unless `INTO` / `FOR UPDATE`. Server remains source of truth.

- [ ] **Step 1: Vitest** `classifySql("DELETE FROM t")` is not read.

- [ ] **Step 2: `api.ts` uses existing `api()` helper (CSRF + same-origin).**

```ts
export function listInstances(projectId: string) {
  return api<Instance[]>(`/api/projects/${projectId}/database/instances`)
}
```

Same for metadata, preview query params, `postQuery`, `postCell`.

- [ ] **Step 3: Page layout from design §10: instance select, empty copy, UNREACHABLE copy, tree, Browse/Query tabs, truncation banner, destructive modal. Palette from MVP visual bar. Monospace grid.**

- [ ] **Step 4: Cell edit: if `primaryKey` length > 0 and role ADMIN, `contentEditable` on td, Enter → `postCell`. Mongo: `_id` column.**

- [ ] **Step 5: Commit** `feat: add project database explorer and query UI`

---

### Task 11: Lab databases + host network

**Files:**
- Modify: `projects/lab/docker-compose.yml`
- Modify: `deployment/docker-compose.yml`
- Modify: `deployment/nginx/nginx.conf` if backend is host-networked (`proxy_pass http://127.0.0.1:8080` for `/api/`)
- Modify: `.env.example` — comment that production `DATABASE_URL` uses `127.0.0.1` when backend is host network

Lab services (in addition to existing web/api):

```yaml
db:
  image: postgres:16-alpine
  environment:
    POSTGRES_USER: lab
    POSTGRES_PASSWORD: lab
    POSTGRES_DB: lab
  ports:
    - "15432:5432"
  labels:
    nexus.project: lab
    nexus.service: db

mysql:
  image: mysql:8.4
  environment:
    MYSQL_ROOT_PASSWORD: lab
    MYSQL_DATABASE: lab
  ports:
    - "13306:3306"
  labels:
    nexus.project: lab
    nexus.service: mysql

mongo:
  image: mongo:7
  ports:
    - "27017:27017"
  labels:
    nexus.project: lab
    nexus.service: mongo
```

Mongo without auth matches optional-auth parser.

Production backend service:

```yaml
network_mode: host
environment:
  DATABASE_URL: jdbc:postgresql://127.0.0.1:5432/nexus
```

Postgres service keeps `ports: ["5432:5432"]`. Backend `ports` mapping is omitted when using host network (it binds 8080 on the host). Confirm `server.port=8080`.

- [ ] **Step 1: `docker compose -f projects/lab/docker-compose.yml up -d` then GET instances as ADMIN includes `db`, `mysql`, `mongo`.**

- [ ] **Step 2: Commit** `chore: add lab database containers and host-network backend for DB access`

---

### Task 12: Verification

- [ ] `./mvnw test` green (unit + JDBC/Mongo ITs)
- [ ] Frontend `npm test` — classify-sql
- [ ] Grep: `rg "password" frontend/features/database` shows no env/password rendering
- [ ] Grep: container REST DTO has no `env` field
- [ ] Browser, project lab:
  - VIEWER: sees tables, SELECT works, INSERT returns 403
  - ADMIN: INSERT then SELECT; DROP asks confirm
  - Unreachable (stop `lab-db`): “Cannot connect”, response JSON has no `jdbc:`
- [ ] Audit row `DB_QUERY` has statement, not password

---

## Spec coverage

| Design section | Task |
|---|---|
| 4 architecture / host network | 6, 7, 11 |
| 5 discovery + env + exclusions | 2, 3, 6 |
| 6 roles | 9 |
| 7 statement rules | 4, 5 |
| 8 limits | 7, 8 |
| 9 HTTP API | 9 |
| 10 UI | 10 |
| 11 audit | 1, 9 |
| 12 tests | 1–10, 12 |
| 13 drivers | 7, 8 |

---

## Execution notes

- Do not start this plan until `ContainerInventory` and login exist.
- Never add `ResolvedTarget` or `ParsedCredentials` to controller return types.
- Do not use Spring Data Mongo.
- Prefer copying pgjdbc URL/options and `MongoClients.create` from Phase 0 over inventing client APIs.
