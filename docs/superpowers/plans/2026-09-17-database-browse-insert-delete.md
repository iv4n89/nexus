# Database Browse Insert/Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let ADMIN insert and delete SQL Browse rows in the same local draft as cell edits, and apply INSERT/UPDATE/DELETE in one `POST /cells` transaction.

**Architecture:** Extend the page draft with `inserts` and `deletes`. The SQL bar is display-only (DELETE → UPDATE → INSERT). Guardar sends structured lists, not that text. The backend plans the three lists (cap 100, skip updates for deleted PKs), then runs parameterized statements on one JDBC connection with `autoCommit=false`. Mongo stays cell-edit only.

**Tech Stack:** Java 21, Spring Boot 3.5.16, JDBC `DriverManager`, Next.js 16, TanStack Query, Vitest.

**Design:** `docs/superpowers/specs/2026-09-17-database-browse-insert-delete-design.md`

**Base branch:** `feat/database-browse-draft-edit` (POST `/cells` already exists). Do not implement on `docs/database-browse-draft-edit`. Copy this plan and the spec onto the feature branch if they are not there yet.

---

## File map

```text
frontend/features/database/
  draft-sql.ts                 # formatSqlDraft (DELETE/UPDATE/INSERT)
  draft-sql.test.ts
  cell-draft.ts                # BrowseDraft: cells + inserts + deletes
  cell-draft.test.ts
  data-grid.tsx                # × column, insert rows, strikethrough
  api.ts                       # postCells inserts/deletes
  database-page.tsx            # + Fila, payload, SQL bar
backend/src/main/java/com/ivan/nexus/
  domain/database/CellPatchGrouper.java          # planSql / SqlWriteBatch
  infrastructure/database/JdbcQueryExecutor.java # applyCells
  application/database/EditDatabaseCells.java
  interfaces/database/DatabaseDtos.java          # inserts + deletes
```

Do not split `database-page.tsx`. Do not add Mongo insert/delete. Do not add `confirmDestructive` on `/cells`.

---

### Task 1: SQL formatter for DELETE and INSERT

**Files:**
- Modify: `frontend/features/database/draft-sql.ts`
- Test: `frontend/features/database/draft-sql.test.ts`

Keep `formatSqlStatements` as UPDATE-only (existing tests stay). Add `formatSqlDraft`.

- [ ] **Step 1: Write the failing tests**

Append to `frontend/features/database/draft-sql.test.ts`:

```ts
import { formatSqlDraft } from './draft-sql'

describe('formatSqlDraft', () => {
  it('orders DELETE then UPDATE then INSERT', () => {
    const sql = formatSqlDraft('POSTGRES', 'public', 'users', {
      deletes: [{ id: 3 }],
      updates: [{ primaryKey: { id: 2 }, columns: { role: 'ADMIN' } }],
      inserts: [{ email: 'nuevo@x' }],
    })
    expect(sql).toBe(
      'DELETE FROM "public"."users" WHERE "id" = \'3\';\n' +
        'UPDATE "public"."users" SET "role" = \'ADMIN\' WHERE "id" = \'2\';\n' +
        'INSERT INTO "public"."users" ("email") VALUES (\'nuevo@x\')',
    )
  })

  it('formats empty insert as DEFAULT VALUES on Postgres', () => {
    const sql = formatSqlDraft('POSTGRES', 'public', 'users', {
      deletes: [],
      updates: [],
      inserts: [{}],
    })
    expect(sql).toBe('INSERT INTO "public"."users" DEFAULT VALUES')
  })

  it('formats empty insert as () VALUES () on MySQL', () => {
    const sql = formatSqlDraft('MYSQL', 'lab', 'users', {
      deletes: [],
      updates: [],
      inserts: [{}],
    })
    expect(sql).toBe('INSERT INTO `lab`.`users` () VALUES ()')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run features/database/draft-sql.test.ts`

Expected: FAIL — `formatSqlDraft` is not exported.

- [ ] **Step 3: Write minimal implementation**

Add to `frontend/features/database/draft-sql.ts` (keep existing `formatSqlStatements` / `formatMongoStatements` / quote helpers):

```ts
export type SqlDraftParts = {
  deletes: Record<string, unknown>[]
  updates: SqlDraftRow[]
  inserts: Record<string, unknown>[]
}

export function formatSqlDraft(
  engine: SqlEngine,
  schema: string,
  table: string,
  parts: SqlDraftParts,
): string {
  const statements: string[] = []
  const tableRef = `${quoteIdent(engine, schema)}.${quoteIdent(engine, table)}`
  for (const primaryKey of parts.deletes) {
    const where = Object.entries(primaryKey)
      .map(([column, value]) => `${quoteIdent(engine, column)} = ${quoteLiteral(value)}`)
      .join(' AND ')
    statements.push(`DELETE FROM ${tableRef} WHERE ${where}`)
  }
  const updates = formatSqlStatements(engine, schema, table, parts.updates)
  if (updates) {
    statements.push(updates)
  }
  for (const values of parts.inserts) {
    const entries = Object.entries(values)
    if (entries.length === 0) {
      statements.push(
        engine === 'MYSQL'
          ? `INSERT INTO ${tableRef} () VALUES ()`
          : `INSERT INTO ${tableRef} DEFAULT VALUES`,
      )
      continue
    }
    const cols = entries.map(([column]) => quoteIdent(engine, column)).join(', ')
    const vals = entries.map(([, value]) => quoteLiteral(value)).join(', ')
    statements.push(`INSERT INTO ${tableRef} (${cols}) VALUES (${vals})`)
  }
  return statements.join(';\n')
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run features/database/draft-sql.test.ts`

Expected: PASS (old UPDATE tests plus new draft tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/features/database/draft-sql.ts frontend/features/database/draft-sql.test.ts
git commit -m "feat: preview DELETE and INSERT in the Browse SQL bar"
```

---

### Task 2: Browse draft inserts and deletes

**Files:**
- Modify: `frontend/features/database/cell-draft.ts`
- Test: `frontend/features/database/cell-draft.test.ts`

Keep `CellDraft`, `applyCell`, `dirtyCount` (cell-level), `sqlPatches`, `toSqlRows` as they are. Add a wrapper used by the page.

- [ ] **Step 1: Write the failing tests**

Append to `frontend/features/database/cell-draft.test.ts`:

```ts
import {
  addInsertRow,
  affectedRowCount,
  applyInsertCell,
  emptyBrowseDraft,
  MAX_AFFECTED_ROWS,
  removeInsertRow,
  sqlWritePayload,
  toggleDelete,
} from './cell-draft'

describe('browse draft writes', () => {
  const keys = ['[["id",1]]', '[["id",2]]']
  const pks = { '[["id",1]]': { id: 1 }, '[["id",2]]': { id: 2 } }
  const columns = ['id', 'email', 'role']

  it('adds an empty insert that counts as one dirty row', () => {
    const draft = addInsertRow(emptyBrowseDraft(), 'i1')
    expect(affectedRowCount(draft)).toBe(1)
    expect(sqlWritePayload(draft, keys, pks, columns)).toEqual({
      patches: [],
      inserts: [{ values: {} }],
      deletes: [],
    })
  })

  it('omits empty insert cells and includes typed PK', () => {
    let draft = addInsertRow(emptyBrowseDraft(), 'i1')
    draft = applyInsertCell(draft, 'i1', 'email', 'nuevo@x')
    draft = applyInsertCell(draft, 'i1', 'id', '')
    expect(draft.inserts[0].values).toEqual({ email: 'nuevo@x' })
  })

  it('toggle delete skips UPDATE patches for that PK', () => {
    let draft = emptyBrowseDraft()
    draft = {
      ...draft,
      cells: applyCell(draft.cells, '[["id",2]]', 'role', 'ADMIN', 'VIEWER'),
    }
    draft = toggleDelete(draft, '[["id",2]]')
    expect(sqlWritePayload(draft, keys, pks, columns)).toEqual({
      patches: [],
      inserts: [],
      deletes: [{ id: 2 }],
    })
    draft = toggleDelete(draft, '[["id",2]]')
    expect(sqlWritePayload(draft, keys, pks, columns).patches).toEqual([
      { primaryKey: { id: 2 }, column: 'role', value: 'ADMIN' },
    ])
  })

  it('removeInsertRow drops a new row with no DELETE', () => {
    let draft = addInsertRow(emptyBrowseDraft(), 'i1')
    draft = removeInsertRow(draft, 'i1')
    expect(affectedRowCount(draft)).toBe(0)
    expect(sqlWritePayload(draft, keys, pks, columns).deletes).toEqual([])
  })

  it('does not add an insert at the 100-row cap', () => {
    let draft = emptyBrowseDraft()
    for (let i = 0; i < MAX_AFFECTED_ROWS; i++) {
      draft = addInsertRow(draft, `i${i}`)
    }
    const blocked = addInsertRow(draft, 'overflow')
    expect(blocked.inserts).toHaveLength(MAX_AFFECTED_ROWS)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run features/database/cell-draft.test.ts`

Expected: FAIL — `emptyBrowseDraft` is not exported.

- [ ] **Step 3: Write minimal implementation**

Append to `frontend/features/database/cell-draft.ts` (do not change `applyCell` empty→null behavior):

```ts
export const MAX_AFFECTED_ROWS = 100

export type InsertRow = {
  localId: string
  values: Record<string, unknown>
}

export type BrowseDraft = {
  cells: CellDraft
  inserts: InsertRow[]
  deletes: string[]
}

export function emptyBrowseDraft(): BrowseDraft {
  return { cells: emptyDraft(), inserts: [], deletes: [] }
}

export function affectedRowCount(draft: BrowseDraft): number {
  const deleteSet = new Set(draft.deletes)
  const updateKeys = Object.keys(draft.cells).filter((key) => !deleteSet.has(key))
  return draft.inserts.length + new Set([...updateKeys, ...draft.deletes]).size
}

export function addInsertRow(draft: BrowseDraft, localId = crypto.randomUUID()): BrowseDraft {
  if (affectedRowCount(draft) >= MAX_AFFECTED_ROWS) {
    return draft
  }
  return { ...draft, inserts: [...draft.inserts, { localId, values: {} }] }
}

export function applyInsertCell(
  draft: BrowseDraft,
  localId: string,
  column: string,
  input: string,
): BrowseDraft {
  return {
    ...draft,
    inserts: draft.inserts.map((row) => {
      if (row.localId !== localId) {
        return row
      }
      const values = { ...row.values }
      if (input === '') {
        delete values[column]
      } else {
        values[column] = input
      }
      return { ...row, values }
    }),
  }
}

export function removeInsertRow(draft: BrowseDraft, localId: string): BrowseDraft {
  return { ...draft, inserts: draft.inserts.filter((row) => row.localId !== localId) }
}

export function toggleDelete(draft: BrowseDraft, key: string): BrowseDraft {
  if (draft.deletes.includes(key)) {
    return { ...draft, deletes: draft.deletes.filter((item) => item !== key) }
  }
  const next = { ...draft, deletes: [...draft.deletes, key] }
  if (affectedRowCount(next) > MAX_AFFECTED_ROWS) {
    return draft
  }
  return next
}

export function sqlWritePayload(
  draft: BrowseDraft,
  rowKeysInPreviewOrder: string[],
  primaryKeys: Record<string, Record<string, unknown>>,
  columns: string[],
): {
  patches: SqlPatch[]
  inserts: { values: Record<string, unknown> }[]
  deletes: Record<string, unknown>[]
} {
  const deleteSet = new Set(draft.deletes)
  const patches = sqlPatches(
    draft.cells,
    rowKeysInPreviewOrder.filter((key) => !deleteSet.has(key)),
    primaryKeys,
    columns,
  )
  const deletes = rowKeysInPreviewOrder
    .filter((key) => deleteSet.has(key))
    .map((key) => primaryKeys[key])
    .filter((pk): pk is Record<string, unknown> => pk != null)
  return {
    patches,
    inserts: draft.inserts.map((row) => ({ values: row.values })),
    deletes,
  }
}

export function toSqlDraftParts(
  draft: BrowseDraft,
  rowKeysInPreviewOrder: string[],
  primaryKeys: Record<string, Record<string, unknown>>,
  columns: string[],
): { deletes: Record<string, unknown>[]; updates: ReturnType<typeof toSqlRows>; inserts: Record<string, unknown>[] } {
  const payload = sqlWritePayload(draft, rowKeysInPreviewOrder, primaryKeys, columns)
  const deleteSet = new Set(draft.deletes)
  return {
    deletes: payload.deletes,
    updates: toSqlRows(
      draft.cells,
      rowKeysInPreviewOrder.filter((key) => !deleteSet.has(key)),
      primaryKeys,
      columns,
    ),
    inserts: payload.inserts.map((row) => row.values),
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run features/database/cell-draft.test.ts`

Expected: PASS (old cell tests plus browse write tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/features/database/cell-draft.ts frontend/features/database/cell-draft.test.ts
git commit -m "feat: track Browse insert rows and delete marks in the local draft"
```

---

### Task 3: `planSql` write batch and 100-row cap

**Files:**
- Modify: `backend/src/main/java/com/ivan/nexus/domain/database/CellPatchGrouper.java`
- Test: `backend/src/test/java/com/ivan/nexus/domain/database/CellPatchGrouperTest.java`

`groupSql(List.of())` must keep throwing (UPDATE-only path). `planSql` allows empty patches when inserts or deletes exist.

- [ ] **Step 1: Write the failing tests**

Append to `CellPatchGrouperTest.java`:

```java
    @Test
    void planSqlOrdersDeletesThenUpdatesThenInsertsAndSkipsPatchedDeletes() {
        CellPatchGrouper.SqlWriteBatch batch = CellPatchGrouper.planSql(
                List.of(
                        new CellPatchGrouper.SqlPatch(Map.of("id", 2), "role", "ADMIN"),
                        new CellPatchGrouper.SqlPatch(Map.of("id", 3), "role", "VIEWER")),
                List.of(new CellPatchGrouper.SqlInsert(Map.of("email", "nuevo@x"))),
                List.of(Map.of("id", 3)));
        assertEquals(1, batch.deletes().size());
        assertEquals(Map.of("id", 3), batch.deletes().getFirst().primaryKey());
        assertEquals(1, batch.updates().size());
        assertEquals(Map.of("id", 2), batch.updates().getFirst().primaryKey());
        assertEquals(1, batch.inserts().size());
        assertEquals("nuevo@x", batch.inserts().getFirst().values().get("email"));
    }

    @Test
    void planSqlAllowsEmptyPatchesWhenInsertsExist() {
        CellPatchGrouper.SqlWriteBatch batch = CellPatchGrouper.planSql(
                List.of(),
                List.of(new CellPatchGrouper.SqlInsert(Map.of())),
                List.of());
        assertEquals(1, batch.inserts().size());
        assertEquals(Map.of(), batch.inserts().getFirst().values());
    }

    @Test
    void planSqlRejectsEmptyBatch() {
        DomainException ex = assertThrows(
                DomainException.class, () -> CellPatchGrouper.planSql(List.of(), List.of(), List.of()));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void planSqlRejectsMoreThanOneHundredAffectedRows() {
        java.util.ArrayList<CellPatchGrouper.SqlInsert> inserts = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            inserts.add(new CellPatchGrouper.SqlInsert(Map.of("n", i)));
        }
        DomainException ex = assertThrows(
                DomainException.class, () -> CellPatchGrouper.planSql(List.of(), inserts, List.of()));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void planSqlRejectsEmptyDeleteMap() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> CellPatchGrouper.planSql(List.of(), List.of(), List.of(Map.of())));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=CellPatchGrouperTest test`

Expected: FAIL — `planSql` / `SqlWriteBatch` do not exist.

- [ ] **Step 3: Write minimal implementation**

Add these types and `planSql` to `CellPatchGrouper.java`. Keep `groupSql` / `groupMongo` unchanged. Reuse `canonicalPk`.

```java
    public record SqlInsert(Map<String, Object> values) {}

    public record SqlDelete(Map<String, Object> primaryKey) {}

    public record SqlWriteBatch(
            List<SqlDelete> deletes, List<GroupedSqlUpdate> updates, List<SqlInsert> inserts) {}

    public static SqlWriteBatch planSql(
            List<SqlPatch> patches, List<SqlInsert> inserts, List<Map<String, Object>> deletes) {
        List<SqlPatch> patchList = patches == null ? List.of() : patches;
        List<SqlInsert> insertList = inserts == null ? List.of() : inserts;
        List<Map<String, Object>> deleteList = deletes == null ? List.of() : deletes;
        LinkedHashMap<String, SqlDelete> uniqueDeletes = new LinkedHashMap<>();
        for (Map<String, Object> primaryKey : deleteList) {
            if (primaryKey == null || primaryKey.isEmpty()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Primary key is required");
            }
            uniqueDeletes.put(canonicalPk(primaryKey), new SqlDelete(new LinkedHashMap<>(primaryKey)));
        }
        List<SqlPatch> remaining = new ArrayList<>();
        for (SqlPatch patch : patchList) {
            if (patch.primaryKey() != null && uniqueDeletes.containsKey(canonicalPk(patch.primaryKey()))) {
                continue;
            }
            remaining.add(patch);
        }
        List<GroupedSqlUpdate> updates =
                remaining.isEmpty() ? List.of() : groupSql(remaining);
        List<SqlInsert> normalizedInserts = new ArrayList<>();
        for (SqlInsert insert : insertList) {
            Map<String, Object> values = insert.values() == null ? Map.of() : insert.values();
            for (String column : values.keySet()) {
                if (column == null || column.isBlank()) {
                    throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Column is required");
                }
            }
            normalizedInserts.add(new SqlInsert(new LinkedHashMap<>(values)));
        }
        if (uniqueDeletes.isEmpty() && updates.isEmpty() && normalizedInserts.isEmpty()) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        int affected = uniqueDeletes.size() + updates.size() + normalizedInserts.size();
        if (affected > MAX_ROWS) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Too many rows");
        }
        List<SqlDelete> plannedDeletes = new ArrayList<>();
        for (SqlDelete delete : uniqueDeletes.values()) {
            plannedDeletes.add(new SqlDelete(
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(delete.primaryKey()))));
        }
        return new SqlWriteBatch(List.copyOf(plannedDeletes), updates, List.copyOf(normalizedInserts));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -Dtest=CellPatchGrouperTest test`

Expected: PASS. Existing empty-`groupSql` and 101-update tests still pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/domain/database/CellPatchGrouper.java \
  backend/src/test/java/com/ivan/nexus/domain/database/CellPatchGrouperTest.java
git commit -m "feat: plan SQL cell writes as DELETE, UPDATE, and INSERT"
```

---

### Task 4: JDBC `applyCells` in one transaction

**Files:**
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutor.java`
- Test: `backend/src/test/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutorIT.java`

Keep `updateCells` working. Point it at `applyCells` with inserts/deletes empty, **or** leave `updateCells` as-is and add `applyCells` beside it. Prefer: `updateCells` delegates to `applyCells` so one transaction loop exists.

- [ ] **Step 1: Write the failing tests**

Append to `JdbcQueryExecutorIT.java` (same Testcontainers Postgres as existing tests). Each test that `CREATE TABLE`s in the method must use a unique table name (`users_write`, `flags_default`) because `@BeforeAll` already created `t` and other tests create `jobs`.

```java
    @Test
    void applyCellsDeletesUpdatesAndInsertsInOneTransaction() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE users_write (
                        id INT PRIMARY KEY,
                        email TEXT UNIQUE,
                        role TEXT NOT NULL
                    )
                    """);
            stmt.execute(
                    "INSERT INTO users_write (id, email, role) VALUES (2, 'bob@x', 'VIEWER'), (3, 'old@x', 'VIEWER')");
        }
        CellPatchGrouper.SqlWriteBatch batch = CellPatchGrouper.planSql(
                List.of(new CellPatchGrouper.SqlPatch(Map.of("id", 2), "role", "ADMIN")),
                List.of(new CellPatchGrouper.SqlInsert(Map.of("id", 4, "email", "nuevo@x", "role", "VIEWER"))),
                List.of(Map.of("id", 3)));
        QueryResult result = executor.applyCells(
                DatabaseEngine.POSTGRES, target(), "public", "users_write", batch);
        assertEquals(3, result.rowCount());
        QueryResult rows = executor.query(
                DatabaseEngine.POSTGRES,
                target(),
                "SELECT id, email, role FROM users_write ORDER BY id",
                StatementClass.READ,
                10);
        assertEquals(2, rows.rows().size());
        assertEquals(2, ((Number) rows.rows().get(0).get(0)).intValue());
        assertEquals("ADMIN", rows.rows().get(0).get(2));
        assertEquals(4, ((Number) rows.rows().get(1).get(0)).intValue());
        assertEquals("nuevo@x", rows.rows().get(1).get(1));
    }

    @Test
    void applyCellsRollsBackWhenDeleteMatchesNoRow() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users_rollback (id INT PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO users_rollback (id, name) VALUES (1, 'ada')");
        }
        CellPatchGrouper.SqlWriteBatch batch = CellPatchGrouper.planSql(
                List.of(),
                List.of(new CellPatchGrouper.SqlInsert(Map.of("id", 2, "name", "bob"))),
                List.of(Map.of("id", 99)));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.applyCells(
                        DatabaseEngine.POSTGRES, target(), "public", "users_rollback", batch));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        QueryResult still = executor.query(
                DatabaseEngine.POSTGRES,
                target(),
                "SELECT count(*) FROM users_rollback",
                StatementClass.READ,
                10);
        assertEquals(1, ((Number) still.rows().get(0).get(0)).intValue());
    }

    @Test
    void applyCellsInsertsDefaultValues() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE flags_default (
                        id SERIAL PRIMARY KEY,
                        name TEXT NOT NULL DEFAULT 'x'
                    )
                    """);
        }
        CellPatchGrouper.SqlWriteBatch batch = CellPatchGrouper.planSql(
                List.of(), List.of(new CellPatchGrouper.SqlInsert(Map.of())), List.of());
        QueryResult result = executor.applyCells(
                DatabaseEngine.POSTGRES, target(), "public", "flags_default", batch);
        assertEquals(1, result.rowCount());
        QueryResult rows = executor.query(
                DatabaseEngine.POSTGRES,
                target(),
                "SELECT name FROM flags_default",
                StatementClass.READ,
                10);
        assertEquals("x", rows.rows().get(0).get(0));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=JdbcQueryExecutorIT#applyCellsDeletesUpdatesAndInsertsInOneTransaction test`

Expected: FAIL — `applyCells` does not exist.

- [ ] **Step 3: Write minimal implementation**

In `JdbcQueryExecutor.java`, replace the body of `updateCells` so it delegates:

```java
    public QueryResult updateCells(
            DatabaseEngine engine,
            ResolvedTarget target,
            String schema,
            String table,
            List<CellPatchGrouper.GroupedSqlUpdate> grouped) {
        if (grouped == null || grouped.isEmpty()) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        return applyCells(
                engine,
                target,
                schema,
                table,
                new CellPatchGrouper.SqlWriteBatch(List.of(), grouped, List.of()));
    }

    public QueryResult applyCells(
            DatabaseEngine engine,
            ResolvedTarget target,
            String schema,
            String table,
            CellPatchGrouper.SqlWriteBatch batch) {
        if (batch == null
                || (batch.deletes().isEmpty() && batch.updates().isEmpty() && batch.inserts().isEmpty())) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        Properties props = connectionProperties(engine, target);
        String url = jdbcUrl(engine, target);
        long start = System.nanoTime();
        int written = batch.deletes().size() + batch.updates().size() + batch.inserts().size();
        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.setAutoCommit(false);
            try {
                for (CellPatchGrouper.SqlDelete row : batch.deletes()) {
                    String sql = buildDeleteSql(engine, schema, table, row);
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        int index = 1;
                        for (Object value : row.primaryKey().values()) {
                            stmt.setObject(index++, value);
                        }
                        expectOneRow(conn, stmt);
                    }
                }
                for (CellPatchGrouper.GroupedSqlUpdate row : batch.updates()) {
                    String sql = buildUpdateSql(engine, schema, table, row);
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        int index = 1;
                        for (Object value : row.columns().values()) {
                            stmt.setObject(index++, value);
                        }
                        for (Object value : row.primaryKey().values()) {
                            stmt.setObject(index++, value);
                        }
                        expectOneRow(conn, stmt);
                    }
                }
                for (CellPatchGrouper.SqlInsert row : batch.inserts()) {
                    String sql = buildInsertSql(engine, schema, table, row);
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                        int index = 1;
                        for (Object value : row.values().values()) {
                            stmt.setObject(index++, value);
                        }
                        expectOneRow(conn, stmt);
                    }
                }
                conn.commit();
                return new QueryResult(
                        List.of("updateCount"),
                        List.of(List.of(written)),
                        false,
                        elapsedMs(start),
                        written);
            } catch (DomainException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    // already rolled back for unmatched primary key
                }
                throw ex;
            } catch (SQLTimeoutException ex) {
                conn.rollback();
                throw new DomainException(NexusErrorCode.QUERY_TIMEOUT, "Query timed out");
            } catch (SQLException ex) {
                conn.rollback();
                throw new DomainException(
                        NexusErrorCode.QUERY_FAILED,
                        SecretSanitizer.strip(target.password(), ex.getMessage()));
            }
        } catch (SQLException ex) {
            throw new DomainException(
                    NexusErrorCode.QUERY_FAILED,
                    SecretSanitizer.strip(target.password(), ex.getMessage()));
        }
    }

    private static void expectOneRow(Connection conn, PreparedStatement stmt) throws SQLException {
        int updated = stmt.executeUpdate();
        if (updated != 1) {
            conn.rollback();
            throw new DomainException(NexusErrorCode.QUERY_FAILED, "No row matched primary key");
        }
    }
```

Keep `buildUpdateSql`. Add:

    static String buildDeleteSql(
            DatabaseEngine engine, String schema, String table, CellPatchGrouper.SqlDelete row) {
        StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(SqlIdentifierQuoter.quote(engine, schema))
                .append('.')
                .append(SqlIdentifierQuoter.quote(engine, table))
                .append(" WHERE ");
        int i = 0;
        for (String column : row.primaryKey().keySet()) {
            if (i++ > 0) {
                sql.append(" AND ");
            }
            sql.append(SqlIdentifierQuoter.quote(engine, column)).append(" = ?");
        }
        return sql.toString();
    }

    static String buildInsertSql(
            DatabaseEngine engine, String schema, String table, CellPatchGrouper.SqlInsert row) {
        String tableRef = SqlIdentifierQuoter.quote(engine, schema)
                + "."
                + SqlIdentifierQuoter.quote(engine, table);
        if (row.values() == null || row.values().isEmpty()) {
            if (engine == DatabaseEngine.MYSQL) {
                return "INSERT INTO " + tableRef + " () VALUES ()";
            }
            return "INSERT INTO " + tableRef + " DEFAULT VALUES";
        }
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        int i = 0;
        for (String column : row.values().keySet()) {
            if (i++ > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(SqlIdentifierQuoter.quote(engine, column));
            placeholders.append("?");
        }
        return "INSERT INTO " + tableRef + " (" + columns + ") VALUES (" + placeholders + ")";
    }
```

`updateCells` still rejects an empty `grouped` list before delegating, so existing callers keep the same error.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -Dtest=JdbcQueryExecutorIT test`

Expected: PASS, including existing rollback and missing-PK tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutor.java \
  backend/src/test/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutorIT.java
git commit -m "feat: apply Browse INSERT UPDATE DELETE in one JDBC transaction"
```

---

### Task 5: `POST /cells` accepts inserts and deletes

**Files:**
- Modify: `backend/src/main/java/com/ivan/nexus/interfaces/database/DatabaseDtos.java`
- Modify: `backend/src/main/java/com/ivan/nexus/application/database/EditDatabaseCells.java`
- Test: `backend/src/test/java/com/ivan/nexus/application/database/EditDatabaseCellsTest.java`

Jackson will bind missing `inserts`/`deletes` as null — treat null as empty in `planSql`.

- [ ] **Step 1: Write the failing tests**

Replace `EditDatabaseCellsTest` constructors to include the new record fields, then add:

```java
    @Test
    void mongoRejectsInserts() {
        stubReady(DatabaseEngine.MONGO);
        DatabaseDtos.CellsRequest body = new DatabaseDtos.CellsRequest(
                null,
                null,
                "app",
                "jobs",
                List.of(),
                List.of(new DatabaseDtos.SqlInsertValues(Map.of("n", 1))),
                List.of());
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute("lab", "lab:db", body, "ADMIN", "admin", "127.0.0.1"));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(mongo, never()).updateDocuments(any(), any(), any(), any());
        verify(jdbc, never()).applyCells(any(), any(), any(), any(), any());
    }

    @Test
    void insertMayIncludePrimaryKeyColumn() {
        stubReady(DatabaseEngine.POSTGRES);
        when(jdbc.applyCells(any(), any(), any(), any(), any()))
                .thenReturn(new QueryResult(List.of("updateCount"), List.of(List.of(1)), false, 1, 1));
        when(users.findByUsername("admin")).thenReturn(java.util.Optional.empty());
        DatabaseDtos.CellsRequest body = new DatabaseDtos.CellsRequest(
                "public",
                "users",
                null,
                null,
                List.of(),
                List.of(new DatabaseDtos.SqlInsertValues(Map.of("id", 9, "email", "nuevo@x"))),
                List.of());
        edit.execute("lab", "lab:db", body, "ADMIN", "admin", "127.0.0.1");
        verify(jdbc).applyCells(any(), any(), eq("public"), eq("users"), any());
    }
```

Update existing `body(...)` helper:

```java
    private static DatabaseDtos.CellsRequest body(List<DatabaseDtos.CellPatch> patches) {
        return new DatabaseDtos.CellsRequest("public", "jobs", null, null, patches, List.of(), List.of());
    }
```

And the PK-column test constructor: add `List.of(), List.of()` after `patches`.

Add `QueryResult` import if missing.

If `updateCells` is still the method Mockito verifies in `emptyPatchesAreNotAllowed`, switch that test to `verify(jdbc, never()).applyCells(...)` **or** keep `updateCells` and have `execute` call `applyCells` only. Prefer `applyCells` for all SQL writes (including patches-only). Then `emptyPatchesAreNotAllowed` still throws from `planSql` and never calls JDBC.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=EditDatabaseCellsTest test`

Expected: FAIL — `CellsRequest` has no `inserts`/`deletes` arity.

- [ ] **Step 3: Write minimal implementation**

`DatabaseDtos.java` — extend the record (this is the only `CellsRequest` call sites besides this test):

```java
    public record CellsRequest(
            String schema,
            String table,
            String mongoDatabase,
            String collection,
            List<CellPatch> patches,
            List<SqlInsertValues> inserts,
            List<Map<String, Object>> deletes) {}

    public record SqlInsertValues(Map<String, Object> values) {}
```

`EditDatabaseCells.execute` SQL branch:

```java
        List<DatabaseDtos.SqlInsertValues> insertValues =
                body.inserts() == null ? List.of() : body.inserts();
        List<Map<String, Object>> deleteMaps = body.deletes() == null ? List.of() : body.deletes();
        if (engine == DatabaseEngine.MONGO) {
            if (!insertValues.isEmpty() || !deleteMaps.isEmpty()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Statement is not allowed");
            }
            // existing mongo patches path, groupMongo
        } else {
            List<CellPatchGrouper.SqlPatch> sqlPatches = new ArrayList<>();
            for (DatabaseDtos.CellPatch patch : patches(body)) {
                if (patch.primaryKey() != null && patch.primaryKey().containsKey(patch.column())) {
                    throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Cannot edit primary key");
                }
                sqlPatches.add(new CellPatchGrouper.SqlPatch(patch.primaryKey(), patch.column(), patch.value()));
            }
            List<CellPatchGrouper.SqlInsert> sqlInserts = new ArrayList<>();
            for (DatabaseDtos.SqlInsertValues insert : insertValues) {
                sqlInserts.add(new CellPatchGrouper.SqlInsert(
                        insert.values() == null ? Map.of() : insert.values()));
            }
            result = jdbc.applyCells(
                    engine,
                    resolution.target(),
                    body.schema(),
                    body.table(),
                    CellPatchGrouper.planSql(sqlPatches, sqlInserts, deleteMaps));
        }
```

Do **not** treat a PK column inside `insert.values` as forbidden.

If `JdbcQueryExecutor.updateCells` is unused after this, leave the method (YAGNI: do not delete in this task unless compile fails). Call `applyCells` from `execute`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -Dtest=EditDatabaseCellsTest,CellPatchGrouperTest,DatabaseControllerTest test`

Expected: PASS. VIEWER `/cells` 403 unchanged (body JSON without new fields still deserializes; extra fields ignored / missing = null).

If `DatabaseControllerTest` ADMIN `/cells` JSON is only `patches`, Jackson sets `inserts`/`deletes` to null — that is required.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/interfaces/database/DatabaseDtos.java \
  backend/src/main/java/com/ivan/nexus/application/database/EditDatabaseCells.java \
  backend/src/test/java/com/ivan/nexus/application/database/EditDatabaseCellsTest.java
git commit -m "feat: accept SQL inserts and deletes on POST /cells"
```

---

### Task 6: Grid + Fila, ×, and Guardar payload

**Files:**
- Modify: `frontend/features/database/api.ts`
- Modify: `frontend/features/database/data-grid.tsx`
- Modify: `frontend/features/database/database-page.tsx`

No new page-level test file unless one already exists. Behavior is locked by Task 1–2 unit tests; this task wires them.

- [ ] **Step 1: Extend `postCells` body type**

In `frontend/features/database/api.ts`, add optional `inserts` and `deletes` to the `body` argument:

```ts
    inserts?: Array<{ values: Record<string, unknown> }>
    deletes?: Array<Record<string, unknown>>
```

`patches` may be `[]` when the Save is insert/delete only. Keep `patches` required in the type as `patches: Array<...>`.

- [ ] **Step 2: Extend `DataGrid`**

Add props (defaults so Query-tab usage stays a one-liner):

```ts
  insertRows?: { localId: string; values: Record<string, unknown> }[]
  deletedKeys?: string[]
  showRowActions?: boolean
  onInsertChange?: (localId: string, column: string, input: string) => void
  onToggleDelete?: (key: string) => void
  onRemoveInsert?: (localId: string) => void
```

Behavior:

- If `showRowActions`, add an empty header `<th>` and a last-cell button `×` (`type="button"`, class `text-[#888]`) per row.
- Existing row: `onToggleDelete(key)`. If `deletedKeys.includes(key)`, add `line-through text-[#666]` on the `<tr>` and do not make cells editable.
- Insert rows: render after preview rows. Cells use `insert.values[column]` (missing key → empty input). PK and `_id` **are** editable on insert rows. `onInsertChange(localId, column, input)` on blur/Enter. `×` calls `onRemoveInsert(localId)` (never `onToggleDelete`).
- Query tab: `showRowActions={false}`, `insertRows={[]}`, `canEdit={false}` — unchanged appearance except no extra column.

Keep `EditableCell`. For insert cells, `original` is unused (`applyInsertCell` omit-if-empty). Pass `dirty={column in row.values}`.

- [ ] **Step 3: Wire `database-page.tsx`**

Replace `useState<CellDraft>(emptyDraft())` with `useState(emptyBrowseDraft())`.

- `changes = affectedRowCount(draft)`
- `guardNav` / Cancelar / save success: `emptyBrowseDraft()`
- Mongo `onDraftChange`: `setDraft(current => ({ ...current, cells: applyCell(current.cells, key, ...) }))`
- SQL existing-cell `onDraftChange`: same, only if the key is not an insert `localId`
- SQL insert: `onInsertChange` → `applyInsertCell`
- `onToggleDelete` / `onRemoveInsert` / `addInsertRow` as in Task 2
- `onSave` SQL:

```ts
    const keys = previewRowKeys(preview.data, previewSelection)
    const payload = sqlWritePayload(
      draft,
      keys,
      sqlPrimaryKeys(preview.data, previewSelection.primaryKey),
      preview.data.columns,
    )
    save.mutate({
      schema: previewSelection.schema,
      table: previewSelection.table,
      patches: payload.patches,
      inserts: payload.inserts,
      deletes: payload.deletes,
    })
```

- Mongo `onSave`: still `{ mongoDatabase, collection, patches: mongoPatches(draft.cells, ...) }` — do not send `inserts`/`deletes`.
- SQL bar when `changes > 0` and SQL: `formatSqlDraft(engine === 'MYSQL' ? 'MYSQL' : 'POSTGRES', schema, table, toSqlDraftParts(...))`. Mongo bar: `formatMongoStatements` from `draft.cells` as today.
- Toolbar: **+ Fila** is visible for `isAdmin && previewSelection.kind === 'sql' && previewSelection.primaryKey.length > 0`, even when `changes === 0`. Disabled when `save.isPending` or `affectedRowCount(draft) >= MAX_AFFECTED_ROWS`. Label `+ Fila`. Guardar/Cancelar still only when `changes > 0`. Cancelar disabled while `save.isPending`.
- `DataGrid` Browse SQL: `showRowActions={Boolean(isAdmin) && previewSelection.primaryKey.length > 0}`, `insertRows={draft.inserts}`, `deletedKeys={draft.deletes}`.
- Mongo Browse: `showRowActions={false}`, `insertRows={[]}`.

Do not intercept `beforeunload`. Do not add a delete confirm modal.

- [ ] **Step 4: Run frontend tests and typecheck**

Run: `cd frontend && npx vitest run features/database`

Expected: PASS.

Run: `cd frontend && npx tsc --noEmit`

Expected: PASS for files you touched. If `app/layout.tsx` `LayoutProps` fails, that is pre-existing — do not fix unless you edited that file.

- [ ] **Step 5: Commit**

```bash
git add frontend/features/database/api.ts \
  frontend/features/database/data-grid.tsx \
  frontend/features/database/database-page.tsx
git commit -m "feat: insert and delete SQL Browse rows in the draft Save"
```

---

## Spec coverage

| Spec | Task |
|---|---|
| + Fila, ×, strikethrough, no extra modal | 6 |
| Empty insert cells omitted; empty row DEFAULT VALUES / `() VALUES ()` | 1, 2, 4 |
| DELETE → UPDATE → INSERT bar and execution | 1, 3, 4 |
| Same PK in patches and deletes → DELETE only | 2, 3 |
| × on new row is not DELETE | 2, 6 |
| Cap 100 | 2, 3 |
| `POST /cells` inserts/deletes; Mongo rejected | 5 |
| JDBC one transaction, executeUpdate==1, audit `DB_CELL_EDIT` via existing `result.rowCount()` | 4, 5 |
| VIEWER 403, Query tab unchanged, no PK edit on existing rows | 5, 6 |
| Cancelar disabled while Save pending | 6 (already on page; keep) |

---

## Done when

- `cd frontend && npx vitest run features/database` passes
- `cd backend && ./mvnw -Dtest=CellPatchGrouperTest,EditDatabaseCellsTest,DatabaseControllerTest,JdbcQueryExecutorIT test` passes
- ADMIN SQL Browse: add a row, edit a cell, mark another row ×, Guardar; SQL bar matches the three statements; preview reloads
- Cancelar restores the grid with no request
- Mongo Browse still has cell edit only (no + Fila, no ×)
