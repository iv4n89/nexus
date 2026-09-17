# Nexus — Database Browse insert and delete (design)

**Date:** 2026-09-17  
**Status:** approved  
**Parent spec:** `docs/superpowers/specs/2026-09-17-database-browse-draft-edit-design.md`  
**Supersedes in parent:** §1 “not insert/delete”; §2 Out “Insert row, delete row”; §2 In “UPDATE / updateOne only”; §5 SQL body (patches only); §6 SQL execution (UPDATE only)

---

## 1. Goal

ADMIN can add rows and mark existing rows for deletion in SQL Browse, in the same local draft as cell edits. Guardar applies INSERT, UPDATE, and DELETE in one transaction. Cancelar discards the draft with no request.

This is not CSV import, not a query editor, and not Mongo insert/delete.

---

## 2. Scope

### In

- SQL Browse (Postgres, MySQL/MariaDB) on tables that already have a primary key
- Local draft: new rows, cell edits, and delete marks
- **+ Fila** and per-row **×**
- Read-only SQL bar shows DELETE, then UPDATE, then INSERT
- One `POST /cells` per Guardar: structured `patches`, `inserts`, and `deletes`; parameterized writes; single JDBC transaction
- VIEWER, Mongo Browse, and SQL tables without a PK: unchanged from the parent spec

### Out

- Mongo insert or delete (cell edit only; `inserts`/`deletes` on a Mongo instance → `QUERY_NOT_ALLOWED`)
- Editable SQL bar, or sending the bar text to the server
- Persisting the draft (reload drops it; no `beforeunload`)
- Extra confirm modal or `confirmDestructive` on `/cells`
- Editing PK of an **existing** preview row
- CSV import, nested values, typed parsing beyond today’s string / `null` bind
- Query-tab INSERT/DELETE (already exist; unchanged)

---

## 3. UX

ADMIN, Browse, SQL table with PK:

1. **+ Fila** is always visible (not only when dirty). Each click appends one empty row at the bottom of the current preview. Every column on that row is editable, including PK.
2. **×** on the right of every row.
   - Existing row: toggle delete mark. Marked rows are struck through and not editable. Click × again to unmark.
   - New row: remove it from the draft. No `DELETE` statement.
3. **Guardar** / **Cancelar** appear only while the draft is dirty. Cancelar disabled while Guardar is in flight (same as parent).
4. SQL bar (display-only, above the grid) lists statements in execution order: **DELETE → UPDATE → INSERT**.
5. Guardar POSTs the structured draft. Success: invalidate preview, clear draft. Failure: keep draft and SQL, show `CODE: detail` above the grid.
6. Dirty navigation dialog unchanged. The count is affected **rows** (inserts + updates not marked delete + deletes).
7. No extra modal for delete. The SQL bar is the confirmation.

Mongo, VIEWER, and tables without PK: no + Fila, no ×. Mongo cell-edit draft unchanged.

Spanish labels stay: **+ Fila**, **Guardar**, **Cancelar**, **Entendido**.

---

## 4. Client draft

Same page-level store as cell edits. Three collections:

| Collection | Identity | Meaning |
|---|---|---|
| `inserts` | Client-generated local id (not a PK) | New rows, in add order |
| `cells` | Existing PK `rowKey` (parent spec) | Dirty columns on preview rows |
| `deletes` | Existing PK `rowKey` | Preview rows marked for deletion |

Rules:

- An insert row stores only **non-empty** cells. Empty string is omitted, not sent as `null` (including each column of a composite PK independently). Clearing a typed insert cell removes that column. This differs from existing-row edits, where empty still commits as `null`.
- × on a new row drops it from `inserts`. It never enters `deletes`.
- If a PK is in `deletes`, do not emit an UPDATE for that row. Cell edits may remain in `cells` until unmarked or cancelled; the SQL bar and the request omit them.
- Dirty when `inserts.length + (cell rows not in deletes) + deletes.size > 0`. An empty new row is dirty (`INSERT … DEFAULT VALUES` / MySQL `() VALUES ()`).
- Cap **100 affected rows**: `inserts.length` + distinct PKs in (`cells` not in `deletes`) ∪ `deletes`. + Fila is disabled at the cap. The server enforces the same cap.
- PK columns stay read-only on existing rows.
- Reload drops the draft.

SQL formatter (still not the execution payload):

- DELETE: `DELETE FROM schema.table WHERE pk = …` per marked row, preview order. Quoting matches parent (Postgres `"`, MySQL `` ` ``, literals `'`).
- UPDATE: unchanged from parent, preview order, skip deleted PKs.
- INSERT: one statement per insert row, add order. Only stored (non-empty) columns. Empty column map: Postgres `INSERT INTO … DEFAULT VALUES`; MySQL/MariaDB `INSERT INTO … () VALUES ()`.
- Tests lock this output.

---

## 5. HTTP API

Same path: `POST /api/projects/{projectId}/database/instances/{databaseId}/cells`. Session + CSRF. ADMIN only. VIEWER → 403.

SQL body:

```json
{
  "schema": "public",
  "table": "users",
  "patches": [
    { "primaryKey": { "id": 2 }, "column": "role", "value": "ADMIN" }
  ],
  "inserts": [
    { "values": { "email": "nuevo@x" } }
  ],
  "deletes": [
    { "id": 3 }
  ]
}
```

- `patches`: unchanged. PK columns in a patch remain `QUERY_NOT_ALLOWED`.
- `inserts`: list of `{ "values": { <column>: <value>, … } }`. `values` may be `{}` (all defaults). PK columns **are** allowed. Omit `inserts` or send `[]` when none.
- `deletes`: list of primary-key maps (same shape as `primaryKey` on a patch). Omit or `[]` when none.
- At least one of `patches`, `inserts`, `deletes` must be non-empty.
- Same PK in `patches` and `deletes`: execute DELETE only; ignore those patches.
- Duplicate delete maps: one DELETE.
- Affected-row count (inserts + distinct PKs in remaining patches ∪ deletes) > **100** → `QUERY_NOT_ALLOWED`.
- Mongo body stays patches-only. If `inserts` or `deletes` is present and non-empty → `QUERY_NOT_ALLOWED`.
- Engine mismatch, missing schema+table, empty delete map, empty insert column name, identifier rejected by `SqlIdentifierQuoter`: `QUERY_NOT_ALLOWED`.
- `ControlPlaneDatabase.requireAdminForDataAccess` unchanged.

Response: existing `QueryResult`. `rowCount` = statements that wrote (DELETE + UPDATE + INSERT). Errors: existing `ApiError`. No passwords, hosts, URIs, or statement text.

---

## 6. Server execution

Open → run → close. No pool. One JDBC connection. `autoCommit = false`.

Order (matches the SQL bar):

1. **DELETE** `schema.table WHERE pk1 = ? AND …` per delete map, request order.
2. **UPDATE** as today (group patches by PK, skip PKs also in deletes).
3. **INSERT** parameterized `INSERT INTO schema.table (c1, c2) VALUES (?, ?)` per insert, request order. Column order is the `values` object order. Empty `values`: Postgres `DEFAULT VALUES`; MySQL/MariaDB `() VALUES ()` (no binds).

Each `executeUpdate()` must equal **1**. 0 or >1, or throw: `ROLLBACK`, `QUERY_FAILED` (0 rows: “No row matched primary key”) or `QUERY_TIMEOUT`. Success: `COMMIT`.

Audit **one** `DB_CELL_EDIT` per successful Save: `engine`, `service`, `databaseId`, `class: WRITE`, `rowCount` = written rows, `durationMs`. No payloads, no SQL text. Failed Save: no audit row.

Mongo document updates: unchanged from parent.

---

## 7. Frontend wiring

Stay in `frontend/features/database/`.

- Extend `cell-draft.ts` (or equivalent) with `inserts` / `deletes`; keep `applyCell` for existing rows.
- Insert-cell commit is **omit-if-empty**, not `shouldCommitCell` → `null`.
- `draft-sql.ts`: DELETE, UPDATE, INSERT; empty-insert forms per engine.
- `postCells` body includes `inserts` and `deletes`.
- `DataGrid`: × column; new rows; strikethrough; PK editable only on insert rows. No API calls.
- `database-page.tsx`: + Fila; Guardar/Cancelar; SQL bar; dirty nav count = affected rows.
- Guardar disabled while in flight; Cancelar disabled while in flight.

---

## 8. Testing

**Unit (frontend)**

- + Fila dirties the draft; empty insert formats as `DEFAULT VALUES` / `() VALUES ()`.
- Non-empty insert omits blank columns and blank PK; typed PK is included.
- × on existing row: strikethrough, toggle off; SQL is DELETE, not UPDATE, even if `cells` had edits.
- × on new row: row gone, no DELETE.
- Formatter order: DELETE, UPDATE, INSERT. Quoting matches parent.
- Cap 100: + Fila no-ops / disabled.

**Unit (backend)**

- Empty patches+inserts+deletes → `QUERY_NOT_ALLOWED`.
- PK column in `patches` → `QUERY_NOT_ALLOWED`; PK column in `inserts.values` allowed.
- Same PK in patches and deletes → one DELETE, no UPDATE.
- Mongo body with non-empty `inserts` or `deletes` → `QUERY_NOT_ALLOWED`.
- >100 affected rows → `QUERY_NOT_ALLOWED`.

**IT**

- JDBC: DELETE + UPDATE + INSERT in one Save; all three visible after commit.
- JDBC: second statement fails (constraint or 0-row DELETE) → rollback, no writes.
- JDBC: empty `values` insert on a table where every column has a default commits one row.
- VIEWER `POST /cells` → 403.

---

## 9. Files (expected)

- Backend: `CellsRequest` (+ `inserts`, `deletes`); `EditDatabaseCells`; `JdbcQueryExecutor` (same connection as `updateCells`); `CellPatchGrouper` or sibling grouper for the 100-row cap across all three; tests beside them.
- Frontend: `cell-draft.ts`, `draft-sql.ts`, `data-grid.tsx`, `database-page.tsx`, `api.ts`, tests beside them.

---

## 10. Compatibility

Parent cell-edit behavior is unchanged for Mongo and for SQL when `inserts` and `deletes` are empty. Query-tab destructive confirmation is unchanged. `/cells` remains a structured write, not a script endpoint.
