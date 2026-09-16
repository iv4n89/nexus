# Nexus — Project Database Manager (design)

**Date:** 2026-09-16  
**Status:** approved  
**Parent spec:** `NEXUS_VPS_DEPLOYMENT_SPEC.md`  
**Implementation plan:** `docs/superpowers/plans/2026-09-16-nexus-database-manager.md`  
**Implements as:** Slice 9 of the MVP plan (after deploy vertical slice; does not block Slices 1–6)

---

## 1. Goal

Add an in-app database manager **inside the Nexus project page** so an operator can inspect and change databases that belong to that project’s Docker stack.

This is not a standalone Adminer/pgAdmin, not a sidecar, and not a manager for Nexus’s own PostgreSQL.

---

## 2. Scope

### In (V1)

- Discover Postgres, MySQL/MariaDB, and MongoDB containers in the **open project only**
- Auto-detect engine + credentials from image name and container env (server-side)
- Schema/collection explorer + data preview
- Query editor (SQL or Mongo JSON)
- ADMIN: read and write
- VIEWER: read only (`SELECT` / `WITH … SELECT`, Mongo `find` / `aggregate`)
- Confirmation for destructive statements
- Audit `DB_QUERY` without secrets
- Timeouts, 500-row cap, per-request connections

### Out (V1)

- Saving credentials in Nexus PostgreSQL, `nexus.yml`, logs, or the frontend
- Managing Nexus’s own database
- Query history persisted across sessions
- ER diagrams, users/roles admin, dump/restore, CSV import
- Redis, Elasticsearch, SQLite, SQL Server
- Cross-project queries
- Optional `nexus.yml` database blocks (auto-detect only)
- Multi-statement SQL scripts
- Shared Docker network attachment as the primary connectivity mode

---

## 3. Placement in the product

- Route: `/projects/{id}/database`
- Project nav: Overview · Services · Logs · **Database** · Deployments · Alerts
- Feature folder: `frontend/features/database/`
- Backend package: `com.ivan.nexus` under `domain/database`, `application/database`, `infrastructure/database`, `interfaces/database`

---

## 4. Architecture

The browser never receives host, username, password, or JDBC/Mongo URI.

```text
UI (project /database)
        │  REST
        ▼
Spring DatabaseController
        │
        ▼
DiscoverProjectDatabases  →  ContainerInventory (existing Docker adapter)
        │
        ▼
CredentialResolver (inspect env, memory only)
        │
        ▼
SqlSession / MongoSession  (open → run → close)
        │
        ▼
Target container  (127.0.0.1:publishedPort or container IP:internalPort)
```

Nexus backend uses **`network_mode: host`** on Linux (local + VPS) so it can reach published ports on `127.0.0.1` or the container IPv4 from Docker inspect.

Connection order:

1. If inspect has a host binding for the engine port, connect to `127.0.0.1` + `HostPort`.
2. Else connect to the first non-empty `NetworkSettings.Networks[].IPAddress` + default internal port.
3. Else mark the instance `UNREACHABLE`.

Default internal ports: Postgres `5432`, MySQL/MariaDB `3306`, MongoDB `27017`.

No long-lived pool against managed databases. Open a connection per request, apply statement timeout, close in `finally`.

---

## 5. Discovery

Use the same project grouping as the rest of Nexus (`nexus.project` → Compose project → name). Only containers of `{id}` are candidates.

### Engine by image (substring, case-insensitive, last path segment before `:`)

| Engine | Image matches (any) |
|---|---|
| POSTGRES | `postgres`, `postgresql` |
| MYSQL | `mysql`, `mariadb` |
| MONGO | `mongo`, `mongodb` |

Bitnami images match because the name contains `postgresql` / `mysql` / `mongodb`.

If several tokens match, prefer the more specific (`postgresql` over a generic miss). Nginx, Redis, and app images are not databases.

### Exclude Nexus itself

Skip a container when **any** is true:

- label `nexus.project` equals `nexus`
- label `com.docker.compose.project` equals `nexus`
- image name contains `nexus-backend` or `nexus-postgres`

### Credentials (server-side inspect `Config.Env` only)

Never copy these fields into API JSON, audit metadata, or logs.

**Postgres**

- user: `POSTGRES_USER` or `postgres`
- password: `POSTGRES_PASSWORD` (required; else `UNREACHABLE`)
- database: `POSTGRES_DB` or `postgres`

**MySQL / MariaDB**

- Prefer `MYSQL_USER` + `MYSQL_PASSWORD` + `MYSQL_DATABASE`
- Else `root` + `MYSQL_ROOT_PASSWORD` + `MYSQL_DATABASE` or `mysql`
- Also accept `MARIADB_*` equivalents
- Password required; else `UNREACHABLE`

**MongoDB**

- user: `MONGO_INITDB_ROOT_USERNAME` (optional; connect without auth if both user and password absent)
- password: `MONGO_INITDB_ROOT_PASSWORD`
- auth database: `admin` when user is set
- default database: `MONGO_INITDB_DATABASE` or `test`
- If user is set and password is missing → `UNREACHABLE`

`databaseId` is stable: `{projectId}:{containerId-12-hex}`. Display name is Compose/`nexus.service` name plus engine.

---

## 6. Permissions

| Role | Explorer | Browse preview | Query editor | Writes / DDL |
|---|---|---|---|---|
| VIEWER | yes | yes (read queries) | yes, read-only statements | no |
| ADMIN | yes | yes; cell/document edit when a primary key / `_id` exists | yes | yes, with destructive confirm |

VIEWER sending a write statement → HTTP 403 `QUERY_NOT_ALLOWED`. Classification happens on the server; the UI only disables controls.

---

## 7. Statement rules

Exactly one statement per request. Strip `--` line comments and `/* */` blocks before classification. Reject if `;` appears with a second non-empty statement.

### SQL (Postgres + MySQL)

Normalize leading keyword (first SQL token):

| Class | Tokens | VIEWER | ADMIN | UI confirm |
|---|---|---|---|---|
| READ | `SELECT`, `WITH` that is only a select (no `INTO`, no `FOR UPDATE` / `FOR SHARE`) | yes | yes | no |
| WRITE | `INSERT`, `UPDATE` with `WHERE`, `DELETE` with `WHERE`, `CREATE`, `ALTER`, `GRANT`, `REVOKE`, `COMMENT`, `SELECT … INTO`, `SELECT … FOR UPDATE` | no | yes | no |
| DESTRUCTIVE | `DROP`, `TRUNCATE`, `DELETE` without `WHERE`, `UPDATE` without `WHERE` | no | yes | **yes** |

ADMIN destructive requests must send `"confirmDestructive": true`. If false or missing, do not run; return `CONFIRMATION_REQUIRED`.

Identifier quoting uses the engine dialect when the backend builds preview/`UPDATE` SQL (`"` for Postgres, backticks for MySQL). User-typed SQL is executed as a single statement after classification, not rewritten.

### Mongo

`statement` is JSON with an `op` field (not free-form shell JS):

```json
{ "op": "find", "database": "app", "collection": "users", "filter": {}, "projection": {}, "limit": 100 }
{ "op": "aggregate", "database": "app", "collection": "users", "pipeline": [] }
{ "op": "insert", "database": "app", "collection": "users", "document": {} }
{ "op": "update", "database": "app", "collection": "users", "filter": {}, "update": {}, "multi": false }
{ "op": "delete", "database": "app", "collection": "users", "filter": {}, "multi": false }
```

| `op` | VIEWER | ADMIN | Confirm when |
|---|---|---|---|
| `find`, `aggregate` | yes | yes | never |
| `insert`, `update` | no | yes | `update` with empty `filter` |
| `delete` | no | yes | empty `filter` or `multi: true` with empty filter |

Unknown `op` → `QUERY_NOT_ALLOWED`. Aggregation `$out` / `$merge` → `QUERY_NOT_ALLOWED` for everyone in V1.

---

## 8. Limits

- Statement timeout: **10 seconds** (Postgres `SET statement_timeout`, MySQL `max_execution_time` / query timeout on the driver, Mongo `maxTimeMS`)
- Preview: **100** rows/documents
- Query results: **500** rows; `truncated: true` if more
- Mongo `find` limit default 100, max 500
- Cell edit: single row/`_id`, one column/field per request

---

## 9. HTTP API

All paths require a session. Prefix: `/api/projects/{projectId}/database`.

| Method | Path | Roles | Purpose |
|---|---|---|---|
| GET | `/instances` | ADMIN, VIEWER | Detected DBs (no secrets) |
| GET | `/instances/{databaseId}/metadata` | ADMIN, VIEWER | Schemas/tables/columns or Mongo databases/collections |
| GET | `/instances/{databaseId}/preview` | ADMIN, VIEWER | First 100 rows. Query: `schema`, `table` **or** `mongoDatabase`, `collection` |
| POST | `/instances/{databaseId}/query` | ADMIN; VIEWER if READ | Run statement |
| POST | `/instances/{databaseId}/cell` | ADMIN | Update one cell / one Mongo field |

### Instance JSON (GET `/instances`)

```json
{
  "id": "lab:a1b2c3d4e5f6",
  "service": "db",
  "engine": "POSTGRES",
  "status": "READY",
  "defaultDatabase": "app"
}
```

`status` is `READY` or `UNREACHABLE`. No host, port, user, or password.

### Query request

```json
{
  "statement": "SELECT 1",
  "confirmDestructive": false
}
```

Mongo: `statement` is the JSON object encoded as a string, or a nested object `statementJson`. V1 uses a **string** body field for both engines so the client is uniform; Mongo string must parse as the `op` document above.

### Query response

```json
{
  "columns": ["id", "email"],
  "rows": [[1, "a@b.c"]],
  "truncated": false,
  "durationMs": 12,
  "rowCount": 1
}
```

Mongo documents flatten to columns = union of keys in the page of results; missing keys are JSON `null`. Nested values are JSON strings.

### Cell edit request (ADMIN)

SQL:

```json
{
  "schema": "public",
  "table": "users",
  "primaryKey": { "id": 1 },
  "column": "email",
  "value": "x@y.z"
}
```

Mongo:

```json
{
  "mongoDatabase": "app",
  "collection": "users",
  "id": "<ObjectId hex or string _id>",
  "field": "email",
  "value": "x@y.z"
}
```

Without a primary key (SQL) or `_id` (Mongo), the Browse grid is read-only and this endpoint returns `QUERY_NOT_ALLOWED`.

Error codes: `PROJECT_NOT_FOUND`, `DATABASE_NOT_FOUND`, `DATABASE_UNREACHABLE`, `QUERY_NOT_ALLOWED`, `CONFIRMATION_REQUIRED`, `QUERY_FAILED`, `QUERY_TIMEOUT`, `FORBIDDEN`. Messages must not include passwords or URIs.

---

## 10. UI

Black/white Nexus chrome. Monospace for query + grid.

1. Instance select at the top.
2. Empty project: “No database containers in this project”.
3. `UNREACHABLE`: “Cannot connect” (no host/URI).
4. Left tree: SQL `schema → table` (views labeled `view`); Mongo `database → collection`.
5. Right tabs: **Browse** | **Query**.
6. Browse loads preview on tree click. ADMIN can edit a cell when PK/`_id` exists; blur or Enter saves via `/cell`.
7. Query: editor + Execute. VIEWER: Execute disabled unless the client-side classifier says READ (server still enforces). Destructive ADMIN: modal, then `confirmDestructive: true`.
8. Truncation banner when `truncated` is true.

No JDBC, Docker IPs, or env names on screen.

---

## 11. Audit

Action `DB_QUERY` (and `DB_CELL_EDIT` for `/cell`).

Metadata JSON: `engine`, `service`, `databaseId`, `class` (`READ`|`WRITE`|`DESTRUCTIVE`), `statement` truncated to 2000 chars, `rowCount`, `durationMs`. No credentials, hosts, or full row payloads.

---

## 12. Testing

**Unit**

- Image → engine mapping (including bitnami, ignoring nginx)
- Env → credential presence / `UNREACHABLE`
- SQL classifier: `SELECT`, `WITH`, `SELECT INTO` (forbidden to VIEWER), multi-statement, `DELETE` with and without `WHERE`
- Mongo classifier: `find`, `$out` forbidden, empty-filter `delete`

**Integration (Testcontainers Postgres, MySQL, Mongo)**

- ADMIN `INSERT` then `SELECT` sees the row
- VIEWER `INSERT` → 403, table unchanged
- Result cap: 500 + `truncated`
- Unreachable instance does not leak URI

**Frontend**

- Tree + tabs render
- VIEWER Execute disabled for `DELETE`
- Destructive modal before `DROP`

---

## 13. Drivers (implementation lock)

- Postgres: `org.postgresql:postgresql` (already on the Nexus classpath)
- MySQL/MariaDB: `com.mysql:mysql-connector-j` (MariaDB accepts the MySQL protocol)
- Mongo: `org.mongodb:mongodb-driver-sync`

Do not add a generic SQL IDE library. Do not embed Adminer.

---

## 14. Relation to the parent spec

Honours §8 / §31 / §33: no arbitrary host shell, no env secrets in the UI, operations allowlisted by role and statement class.

Extends §30 navigation with Database. Extends §32 audit with `DB_QUERY` / `DB_CELL_EDIT`.

Does not persist a copy of application data in Nexus PostgreSQL (same spirit as §15 / §26).
