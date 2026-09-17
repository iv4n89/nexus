# Nexus — Database Browse draft edit (design)

**Date:** 2026-09-17  
**Status:** approved  
**Parent spec:** `docs/superpowers/specs/2026-09-16-nexus-database-manager-design.md`  
**Supersedes in parent:** §8 cell-edit “one column per request”; §9 `POST /cell`; §10.6 blur/Enter saves via `/cell`

---

## 1. Goal

ADMIN can edit preview cells in Browse, accumulate a local draft, see the equivalent write statements, and apply them in one all-or-nothing Save. Cancel discards the draft with no request.

This is not a query editor, not CSV import, and not insert/delete.

---

## 2. Scope

### In

- SQL (Postgres, MySQL/MariaDB) and Mongo Browse grids that already allow cell edit (primary key or `_id` present)
- Local draft of **UPDATE / `updateOne` only** across **multiple rows**
- Read-only SQL (or Mongo) preview **above the grid**, updated as the draft changes
- **Guardar** / **Cancelar** only while the draft is dirty
- One `POST /cells` per Save: structured patches, parameterized writes, single transaction
- Block changing table, instance, or Browse/Query tab while dirty
- VIEWER unchanged: read-only grid

### Out

- Insert row, delete row, edit PK / `_id`
- Editable SQL preview (the bar is display-only)
- Sending the preview text to the server
- Persisting the draft (reload of the browser drops it; no `beforeunload`)
- Nested Mongo values in the grid (same as today: scalars / flattened strings)
- Multi-document Mongo Save when the server cannot start a session transaction (standalone / no replica set): error, nothing written
- Typed parsing beyond today’s cell bind (empty input → JSON `null`; otherwise the input string)

---

## 3. UX

Replace blur → `POST /cell`.

1. ADMIN focuses a non-PK cell, types, blurs or Enter. If `shouldCommitCell` is true, the cell is dirty in the **page** draft (not a per-input-only copy).
2. Dirty cells get a visible border. A toolbar appears: change count, **Cancelar**, **Guardar**.
3. Above the grid, a monospace, non-editable block lists one statement per dirty row (SQL `UPDATE … SET col = … WHERE pk`; Mongo `updateOne` + `$set`). Order follows preview row order.
4. **Cancelar** restores preview values, hides the toolbar and SQL, no network.
5. **Guardar** POSTs patches. Success: invalidate preview, clear draft, hide toolbar. Failure: keep draft and SQL, show the API error above the grid (`CODE: detail`).
6. Tree click, instance change, or Query tab while dirty: dialog “Hay N cambios sin guardar. Guarda o cancela antes de cambiar.” Selection does not change. **Entendido** dismisses.
7. Tables/collections without PK/`_id` stay read-only. VIEWER: no inputs, no toolbar.

---

## 4. Client draft

- Row identity is the primary-key map (SQL) or `_id` string (Mongo), **not** the preview row index.
- Draft is `Map<rowId, Map<column, value>>`. Unchanged columns are omitted. Two patches in one request for the same row and column: the later array entry wins.
- Empty string commits as `null` (same as current `shouldCommitCell`).
- Reverting a cell to the preview value removes it from the draft; if the row map is empty, the row is no longer dirty.
- PK / `_id` columns are never editable and never appear in patches.
- SQL preview quoting matches the server: Postgres `"ident"` with `"` doubled; MySQL `` `ident` `` with `` ` `` doubled; string literals `'` with `'` doubled; `NULL` unquoted. Mongo: JSON with `_id` as hex/string as shown in the grid.

The preview is **not** the execution payload. Tests lock formatter output so it stays aligned with the statements the backend builds (identifiers + literal rendering). Execution uses binds, not this string.

---

## 5. HTTP API

Prefix: `/api/projects/{projectId}/database`. Session + CSRF. ADMIN only (`hasRole("ADMIN")`). VIEWER → 403.

| Method | Path | Purpose |
|---|---|---|
| POST | `/instances/{databaseId}/cells` | Apply a draft (1..N patches) in one transaction |

**Remove** `POST /instances/{databaseId}/cell`. One cell is a batch of length 1.

### SQL body

```json
{
  "schema": "public",
  "table": "jobs",
  "patches": [
    { "primaryKey": { "id": 1 }, "column": "status", "value": "running" },
    { "primaryKey": { "id": 2 }, "column": "name", "value": "export-v2" }
  ]
}
```

### Mongo body

```json
{
  "mongoDatabase": "app",
  "collection": "jobs",
  "patches": [
    { "id": "66ab…", "field": "status", "value": "running" },
    { "id": "66cd…", "field": "name", "value": "export-v2" }
  ]
}
```

Same `QueryResult` shape as today (`updateCount` / write result). Errors: existing `ApiError` (`code: detail`). No passwords, hosts, or URIs.

### Validation (`QUERY_NOT_ALLOWED` unless noted)

- `patches` empty, or more than **100 distinct** primary keys / `_id`s
- Missing schema+table (SQL) or mongoDatabase+collection (Mongo)
- Missing PK map / `id`, or empty column/field
- Patch column/field is a PK column or `_id`
- Identifier rejected by `SqlIdentifierQuoter` (null / NUL) or Mongo name rules already used by query execution
- Engine mismatch (SQL body on Mongo instance or the reverse)

`ControlPlaneDatabase.requireAdminForDataAccess` still forbids the Nexus control-plane instance.

---

## 6. Server execution

Open → run → close. No JDBC/Mongo pool. One connection (or client) per Save.

**SQL.** Group patches by primary-key map. One `UPDATE schema.table SET c1 = ?, c2 = ? WHERE pk1 = ? AND pk2 = ?` per group. `SET` column order is stable (preview column order). `autoCommit = false`. For each statement, `executeUpdate()` must equal **1**. If any statement returns 0 or >1, or throws: `ROLLBACK`, `QUERY_FAILED` (0 rows: detail “No row matched primary key”) or `QUERY_TIMEOUT`. Success: `COMMIT`.

**Mongo.** Group patches by `id`. One `updateOne` per id: filter `_id`, `$set` all dirty fields. A single id is atomic without a multi-doc transaction. Two or more ids: `client.startSession()` + transaction. If the server cannot transaction (not a replica set / not a mongos): do **not** apply any update; `QUERY_NOT_ALLOWED` with detail that multi-document Save needs a replica set (save one document at a time). Inside a transaction, each update must match 1 document; otherwise abort.

Audit **one** `DB_CELL_EDIT` per successful Save: `engine`, `service`, `databaseId`, `class: WRITE`, `rowCount` = distinct rows written, `durationMs`. No row payloads and no statement text. Failed Save: no audit row.

---

## 7. Frontend wiring

Stay in `frontend/features/database/`.

- Draft store in the database page (or a dedicated hook used only there). `DataGrid` receives draft values + `onDraftChange`; it does not call the API.
- Formatter module: pure functions, unit-tested.
- `postCells` replaces `postCell`.
- Guardar disabled while the mutation is in flight.
- On mutation error: `invalidateQueries` is **not** required (draft is source of truth). On success: invalidate preview (and metadata only if already invalidated today).

Dirty navigation: intercept instance `<select>`, tree selection, and tab buttons. No intercept of browser back/reload in V1.

---

## 8. Testing

**Unit (frontend)**

- Formatter: multi-column one row; two rows; `NULL`; quote in string; MySQL vs Postgres identifiers; Mongo `$set` two fields.
- Draft: dirty shows toolbar; revert last cell hides it; Cancel restores; blur with no `shouldCommitCell` does not dirty.

**Unit (backend)**

- Grouping two columns of one PK → one UPDATE with two binds.
- PK column in patches → `QUERY_NOT_ALLOWED`.
- Empty patches → `QUERY_NOT_ALLOWED`.

**IT**

- JDBC: two-row Save, second statement fails (constraint or bad value) → both rows unchanged after rollback.
- JDBC: PK that matches no row → `QUERY_FAILED`, no writes.
- Mongo: two `_id`s when transactions are unavailable → error, zero documents changed.
- VIEWER `POST /cells` → 403.
- ADMIN `POST /cell` → 4xx (route removed; must not write).

---

## 9. Files (expected)

- Backend: `EditDatabaseCells` (replace `EditDatabaseCell`); `JdbcQueryExecutor.updateCells`; `MongoQueryExecutor.updateDocuments`; `DatabaseDtos.CellsRequest`; `DatabaseController`; `SecurityConfig` (`/cells` instead of `/cell`).
- Frontend: `data-grid.tsx`, `database-page.tsx`, `api.ts`, new `draft-sql.ts` (name may vary), tests beside them.

---

## 10. Compatibility

Parent V1 “multi-statement SQL scripts” remains out of the **Query** tab. `/cells` is not a script endpoint; it is a structured write. Query-tab confirmation for destructive SQL is unchanged.
