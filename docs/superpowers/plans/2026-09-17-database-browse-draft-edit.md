# Database Browse Draft Edit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Browse blur→`POST /cell` with a local multi-row draft, a read-only SQL/Mongo preview, and one transactional `POST /cells`.

**Architecture:** The grid writes a page-level draft. A pure formatter renders the statements the operator will see. Guardar sends structured patches (not that text). The backend groups patches by primary key / `_id`, runs parameterized `UPDATE` / `updateOne` on one short-lived connection, and commits only if every statement matches exactly one row.

**Tech Stack:** Java 21, Spring Boot 3.5.16, JDBC `DriverManager`, MongoDB sync driver, Next.js 16, TanStack Query, Vitest.

**Design:** `docs/superpowers/specs/2026-09-17-database-browse-draft-edit-design.md`

---

## File map

```text
frontend/features/database/
  draft-sql.ts                 # create — identifier/literal quoting + statement text
  draft-sql.test.ts            # create
  cell-draft.ts                # create — draft map, patches, dirty count
  cell-draft.test.ts           # create
  cell-edit.ts                 # keep shouldCommitCell
  data-grid.tsx                # modify — no API; overlay + dirty border
  api.ts                       # postCells; delete postCell
  database-page.tsx            # draft toolbar, SQL bar, Guardar, dirty nav
backend/src/main/java/com/ivan/nexus/
  domain/database/CellPatchGrouper.java          # create
  application/database/EditDatabaseCells.java    # create (replace EditDatabaseCell)
  infrastructure/database/JdbcQueryExecutor.java # updateCells; remove updateCell
  infrastructure/database/MongoQueryExecutor.java# updateDocuments; remove updateCell
  interfaces/database/DatabaseDtos.java          # CellsRequest
  interfaces/database/DatabaseController.java    # POST /cells
  infrastructure/security/SecurityConfig.java    # /cells matcher
```

Do not split `database-page.tsx` in this plan. Pure logic lives in `cell-draft.ts` / `draft-sql.ts`.

---

### Task 1: SQL/Mongo preview formatter

**Files:**
- Create: `frontend/features/database/draft-sql.ts`
- Test: `frontend/features/database/draft-sql.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from 'vitest'
import { formatMongoStatements, formatSqlStatements } from './draft-sql'

describe('formatSqlStatements', () => {
  it('renders one UPDATE with two columns and NULL', () => {
    const sql = formatSqlStatements('POSTGRES', 'public', 'jobs', [
      {
        primaryKey: { id: 1 },
        columns: { status: 'running', name: null },
      },
    ])
    expect(sql).toBe(
      'UPDATE "public"."jobs" SET "status" = \'running\', "name" = NULL WHERE "id" = \'1\'',
    )
  })

  it('renders two rows in preview order and doubles quotes in values', () => {
    const sql = formatSqlStatements('POSTGRES', 'public', 'jobs', [
      { primaryKey: { id: 1 }, columns: { name: "O'Brien" } },
      { primaryKey: { id: 2 }, columns: { name: 'export-v2' } },
    ])
    expect(sql).toBe(
      'UPDATE "public"."jobs" SET "name" = \'O\'\'Brien\' WHERE "id" = \'1\';\n' +
        'UPDATE "public"."jobs" SET "name" = \'export-v2\' WHERE "id" = \'2\'',
    )
  })

  it('uses MySQL backticks', () => {
    const sql = formatSqlStatements('MYSQL', 'lab', 'order', [
      { primaryKey: { n: 1 }, columns: { note: 'x' } },
    ])
    expect(sql).toBe('UPDATE `lab`.`order` SET `note` = \'x\' WHERE `n` = \'1\'')
  })
})

describe('formatMongoStatements', () => {
  it('renders updateOne with $set of two fields', () => {
    const text = formatMongoStatements('jobs', [
      { id: '66ab', columns: { status: 'running', name: 'ingest' } },
    ])
    expect(text).toBe(
      'db.jobs.updateOne({"_id":"66ab"},{"$set":{"status":"running","name":"ingest"}})',
    )
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run features/database/draft-sql.test.ts`

Expected: FAIL — `Cannot find module './draft-sql'`

- [ ] **Step 3: Write minimal implementation**

```ts
export type SqlEngine = 'POSTGRES' | 'MYSQL'

export type SqlDraftRow = {
  primaryKey: Record<string, unknown>
  columns: Record<string, unknown>
}

export type MongoDraftRow = {
  id: string
  columns: Record<string, unknown>
}

export function formatSqlStatements(
  engine: SqlEngine,
  schema: string,
  table: string,
  rows: SqlDraftRow[],
): string {
  return rows
    .map((row) => {
      const set = Object.entries(row.columns)
        .map(([column, value]) => `${quoteIdent(engine, column)} = ${quoteLiteral(value)}`)
        .join(', ')
      const where = Object.entries(row.primaryKey)
        .map(([column, value]) => `${quoteIdent(engine, column)} = ${quoteLiteral(value)}`)
        .join(' AND ')
      return `UPDATE ${quoteIdent(engine, schema)}.${quoteIdent(engine, table)} SET ${set} WHERE ${where}`
    })
    .join(';\n')
}

export function formatMongoStatements(collection: string, rows: MongoDraftRow[]): string {
  return rows
    .map((row) => {
      const filter = JSON.stringify({ _id: row.id })
      const set = JSON.stringify({ $set: row.columns })
      return `db.${collection}.updateOne(${filter},${set})`
    })
    .join('\n')
}

function quoteIdent(engine: SqlEngine, identifier: string): string {
  if (engine === 'MYSQL') {
    return '`' + identifier.replaceAll('`', '``') + '`'
  }
  return '"' + identifier.replaceAll('"', '""') + '"'
}

function quoteLiteral(value: unknown): string {
  if (value == null) {
    return 'NULL'
  }
  return "'" + String(value).replaceAll("'", "''") + "'"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run features/database/draft-sql.test.ts`

Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add frontend/features/database/draft-sql.ts frontend/features/database/draft-sql.test.ts
git commit -m "feat: format Browse draft SQL and Mongo updateOne text"
```

---

### Task 2: Draft map and patch list

**Files:**
- Create: `frontend/features/database/cell-draft.ts`
- Test: `frontend/features/database/cell-draft.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, expect, it } from 'vitest'
import { applyCell, dirtyCount, emptyDraft, sqlPatches, toSqlRows } from './cell-draft'

describe('cell-draft', () => {
  it('does not dirty when shouldCommitCell is false', () => {
    const draft = applyCell(emptyDraft(), '[["id",1]]', 'status', '', null)
    expect(dirtyCount(draft)).toBe(0)
  })

  it('counts dirty cells and builds preview rows then patches in preview order', () => {
    let draft = emptyDraft()
    draft = applyCell(draft, '[["id",1]]', 'status', 'running', 'queued')
    draft = applyCell(draft, '[["id",1]]', 'name', 'ingest-v2', 'ingest')
    draft = applyCell(draft, '[["id",2]]', 'name', 'export-v2', 'export')
    expect(dirtyCount(draft)).toBe(3)
    const columns = ['id', 'status', 'name']
    const keys = ['[["id",1]]', '[["id",2]]']
    const pks = { '[["id",1]]': { id: 1 }, '[["id",2]]': { id: 2 } }
    expect(toSqlRows(draft, keys, pks, columns)).toEqual([
      { primaryKey: { id: 1 }, columns: { status: 'running', name: 'ingest-v2' } },
      { primaryKey: { id: 2 }, columns: { name: 'export-v2' } },
    ])
    expect(sqlPatches(draft, keys, pks, columns)).toEqual([
      { primaryKey: { id: 1 }, column: 'status', value: 'running' },
      { primaryKey: { id: 1 }, column: 'name', value: 'ingest-v2' },
      { primaryKey: { id: 2 }, column: 'name', value: 'export-v2' },
    ])
  })

  it('removes a cell reverted to the preview value and Cancel is emptyDraft', () => {
    let draft = applyCell(emptyDraft(), '[["id",1]]', 'status', 'running', 'queued')
    draft = applyCell(draft, '[["id",1]]', 'status', 'queued', 'queued')
    expect(dirtyCount(draft)).toBe(0)
    expect(emptyDraft()).toEqual({})
  })

  it('stores empty input as null', () => {
    const draft = applyCell(emptyDraft(), '[["id",1]]', 'name', '', 'ingest')
    expect(draft['[["id",1]]'].name).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run features/database/cell-draft.test.ts`

Expected: FAIL — missing module

- [ ] **Step 3: Write minimal implementation**

```ts
import { shouldCommitCell } from './cell-edit'

export type CellDraft = Record<string, Record<string, unknown>>

export type SqlPatch = {
  primaryKey: Record<string, unknown>
  column: string
  value: unknown
}

export function emptyDraft(): CellDraft {
  return {}
}

export function rowKey(primaryKey: Record<string, unknown>, pkColumns: string[]): string {
  return JSON.stringify(pkColumns.map((column) => [column, primaryKey[column]]))
}

export function applyCell(
  draft: CellDraft,
  key: string,
  column: string,
  input: string,
  original: unknown,
): CellDraft {
  if (!shouldCommitCell(input, original)) {
    return revertCell(draft, key, column)
  }
  const value = input === '' ? null : input
  const nextRow = { ...(draft[key] ?? {}), [column]: value }
  return { ...draft, [key]: nextRow }
}

export function revertCell(draft: CellDraft, key: string, column: string): CellDraft {
  const row = draft[key]
  if (!row || !(column in row)) {
    return draft
  }
  const nextRow = { ...row }
  delete nextRow[column]
  const next = { ...draft }
  if (Object.keys(nextRow).length === 0) {
    delete next[key]
  } else {
    next[key] = nextRow
  }
  return next
}

export function dirtyCount(draft: CellDraft): number {
  return Object.values(draft).reduce((sum, row) => sum + Object.keys(row).length, 0)
}

export function displayValue(draft: CellDraft, key: string, column: string, original: unknown): unknown {
  if (draft[key] && column in draft[key]) {
    return draft[key][column]
  }
  return original
}

export function toSqlRows(
  draft: CellDraft,
  rowKeysInPreviewOrder: string[],
  primaryKeys: Record<string, Record<string, unknown>>,
  columns: string[],
): { primaryKey: Record<string, unknown>; columns: Record<string, unknown> }[] {
  const rows = []
  for (const key of rowKeysInPreviewOrder) {
    const dirty = draft[key]
    if (!dirty) {
      continue
    }
    const ordered: Record<string, unknown> = {}
    for (const column of columns) {
      if (column in dirty) {
        ordered[column] = dirty[column]
      }
    }
    rows.push({ primaryKey: primaryKeys[key], columns: ordered })
  }
  return rows
}

export function sqlPatches(
  draft: CellDraft,
  rowKeysInPreviewOrder: string[],
  primaryKeys: Record<string, Record<string, unknown>>,
  columns: string[],
): SqlPatch[] {
  const patches: SqlPatch[] = []
  for (const row of toSqlRows(draft, rowKeysInPreviewOrder, primaryKeys, columns)) {
    for (const [column, value] of Object.entries(row.columns)) {
      patches.push({ primaryKey: row.primaryKey, column, value })
    }
  }
  return patches
}

export function mongoPatches(
  draft: CellDraft,
  rowKeysInPreviewOrder: string[],
  columns: string[],
): { id: string; field: string; value: unknown }[] {
  const patches = []
  for (const key of rowKeysInPreviewOrder) {
    const dirty = draft[key]
    if (!dirty) {
      continue
    }
    for (const column of columns) {
      if (column in dirty) {
        patches.push({ id: key, field: column, value: dirty[column] })
      }
    }
  }
  return patches
}

export function toMongoRows(
  draft: CellDraft,
  rowKeysInPreviewOrder: string[],
  columns: string[],
): { id: string; columns: Record<string, unknown> }[] {
  const rows = []
  for (const key of rowKeysInPreviewOrder) {
    const dirty = draft[key]
    if (!dirty) {
      continue
    }
    const ordered: Record<string, unknown> = {}
    for (const column of columns) {
      if (column in dirty) {
        ordered[column] = dirty[column]
      }
    }
    rows.push({ id: key, columns: ordered })
  }
  return rows
}
```

Mongo row keys are the `_id` string itself (pass `rowKey({ _id }, ['_id'])` or `String(row._id)` consistently in Task 8). Tests above use SQL keys.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run features/database/cell-draft.test.ts`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/features/database/cell-draft.ts frontend/features/database/cell-draft.test.ts
git commit -m "feat: track Browse cell drafts and emit ordered patches"
```

---

### Task 3: Backend patch grouping

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/domain/database/CellPatchGrouper.java`
- Test: `backend/src/test/java/com/ivan/nexus/domain/database/CellPatchGrouperTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CellPatchGrouperTest {

    @Test
    void groupsTwoColumnsOfOnePrimaryKey() {
        List<CellPatchGrouper.SqlPatch> patches = List.of(
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "status", "running"),
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "name", "ingest-v2"));
        List<CellPatchGrouper.GroupedSqlUpdate> grouped = CellPatchGrouper.groupSql(patches);
        assertEquals(1, grouped.size());
        assertEquals(Map.of("id", 1), grouped.getFirst().primaryKey());
        assertEquals("running", grouped.getFirst().columns().get("status"));
        assertEquals("ingest-v2", grouped.getFirst().columns().get("name"));
        assertEquals(List.of("status", "name"), List.copyOf(grouped.getFirst().columns().keySet()));
    }

    @Test
    void laterPatchWinsSameColumn() {
        List<CellPatchGrouper.SqlPatch> patches = List.of(
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "status", "queued"),
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "status", "running"));
        assertEquals("running", CellPatchGrouper.groupSql(patches).getFirst().columns().get("status"));
    }

    @Test
    void emptyPatchesAreNotAllowed() {
        DomainException ex = assertThrows(DomainException.class, () -> CellPatchGrouper.groupSql(List.of()));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }

    @Test
    void moreThanOneHundredRowsAreNotAllowed() {
        java.util.ArrayList<CellPatchGrouper.SqlPatch> patches = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            patches.add(new CellPatchGrouper.SqlPatch(Map.of("id", i), "n", 1));
        }
        DomainException ex = assertThrows(DomainException.class, () -> CellPatchGrouper.groupSql(patches));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=CellPatchGrouperTest test`

Expected: FAIL — cannot find `CellPatchGrouper`

- [ ] **Step 3: Write minimal implementation**

```java
package com.ivan.nexus.domain.database;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CellPatchGrouper {
    public static final int MAX_ROWS = 100;

    private CellPatchGrouper() {}

    public record SqlPatch(Map<String, Object> primaryKey, String column, Object value) {}

    public record GroupedSqlUpdate(Map<String, Object> primaryKey, Map<String, Object> columns) {}

    public record MongoPatch(String id, String field, Object value) {}

    public record GroupedMongoUpdate(String id, Map<String, Object> fields) {}

    public static List<GroupedSqlUpdate> groupSql(List<SqlPatch> patches) {
        if (patches == null || patches.isEmpty()) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        LinkedHashMap<String, GroupedSqlUpdate> grouped = new LinkedHashMap<>();
        for (SqlPatch patch : patches) {
            if (patch.primaryKey() == null || patch.primaryKey().isEmpty()
                    || patch.column() == null || patch.column().isBlank()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Primary key and column are required");
            }
            String key = canonicalPk(patch.primaryKey());
            GroupedSqlUpdate existing = grouped.get(key);
            LinkedHashMap<String, Object> columns = existing == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(existing.columns());
            columns.put(patch.column(), patch.value());
            LinkedHashMap<String, Object> pk = existing == null
                    ? new LinkedHashMap<>(patch.primaryKey())
                    : new LinkedHashMap<>(existing.primaryKey());
            grouped.put(key, new GroupedSqlUpdate(pk, columns));
        }
        if (grouped.size() > MAX_ROWS) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Too many rows");
        }
        List<GroupedSqlUpdate> result = new ArrayList<>();
        for (GroupedSqlUpdate row : grouped.values()) {
            result.add(new GroupedSqlUpdate(
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row.primaryKey())),
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row.columns()))));
        }
        return List.copyOf(result);
    }

    public static List<GroupedMongoUpdate> groupMongo(List<MongoPatch> patches) {
        if (patches == null || patches.isEmpty()) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        LinkedHashMap<String, GroupedMongoUpdate> grouped = new LinkedHashMap<>();
        for (MongoPatch patch : patches) {
            if (patch.id() == null || patch.id().isBlank() || patch.field() == null || patch.field().isBlank()) {
                throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Document id and field are required");
            }
            GroupedMongoUpdate existing = grouped.get(patch.id());
            LinkedHashMap<String, Object> fields = existing == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(existing.fields());
            fields.put(patch.field(), patch.value());
            grouped.put(patch.id(), new GroupedMongoUpdate(patch.id(), fields));
        }
        if (grouped.size() > MAX_ROWS) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Too many rows");
        }
        List<GroupedMongoUpdate> result = new ArrayList<>();
        for (GroupedMongoUpdate row : grouped.values()) {
            result.add(new GroupedMongoUpdate(
                    row.id(),
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row.fields()))));
        }
        return List.copyOf(result);
    }

    private static String canonicalPk(Map<String, Object> primaryKey) {
        return primaryKey.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + Objects.toString(entry.getValue()))
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }
}
```

Primary key and column maps stay `LinkedHashMap` (unmodifiable wrapper) so JDBC bind order matches SET/WHERE.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -Dtest=CellPatchGrouperTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/domain/database/CellPatchGrouper.java \
  backend/src/test/java/com/ivan/nexus/domain/database/CellPatchGrouperTest.java
git commit -m "feat: group database cell patches by primary key"
```

---

### Task 4: JDBC transactional `updateCells`

**Files:**
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutor.java`
- Modify: `backend/src/test/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutorIT.java`

Keep `updateCell` until Task 6 so existing callers compile.

- [ ] **Step 1: Write the failing IT methods at the end of `JdbcQueryExecutorIT` (before `target()`)**

```java
    @Test
    void updateCellsRollsBackWhenSecondRowFails() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE jobs (
                        id INT PRIMARY KEY,
                        status TEXT NOT NULL CHECK (status IN ('queued', 'running')),
                        name TEXT
                    )
                    """);
            stmt.execute("INSERT INTO jobs (id, status, name) VALUES (1, 'queued', 'ingest'), (2, 'queued', 'export')");
        }
        List<CellPatchGrouper.SqlPatch> patches = List.of(
                new CellPatchGrouper.SqlPatch(Map.of("id", 1), "status", "running"),
                new CellPatchGrouper.SqlPatch(Map.of("id", 2), "status", "bogus"));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.updateCells(
                        DatabaseEngine.POSTGRES,
                        target(),
                        "public",
                        "jobs",
                        CellPatchGrouper.groupSql(patches)));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        QueryResult still = executor.query(
                DatabaseEngine.POSTGRES,
                target(),
                "SELECT status FROM jobs ORDER BY id",
                StatementClass.READ,
                10);
        assertEquals("queued", still.rows().get(0).get(0));
        assertEquals("queued", still.rows().get(1).get(0));
    }

    @Test
    void updateCellsFailsWhenPrimaryKeyMatchesNoRow() {
        List<CellPatchGrouper.GroupedSqlUpdate> grouped = CellPatchGrouper.groupSql(List.of(
                new CellPatchGrouper.SqlPatch(Map.of("n", 99), "n", 99)));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.updateCells(DatabaseEngine.POSTGRES, target(), "public", "t", grouped));
        assertEquals(NexusErrorCode.QUERY_FAILED, ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("primary key"));
    }
```

Add imports: `CellPatchGrouper`, `Map`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=JdbcQueryExecutorIT#updateCellsRollsBackWhenSecondRowFails test`

Expected: FAIL — `cannot find symbol: method updateCells`

- [ ] **Step 3: Implement `updateCells` on `JdbcQueryExecutor`**

Replace `updateCell` body usage with this new method (leave `updateCell` delegating to `updateCells` with a one-element group so Task 6 can delete it later):

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
        Properties props = connectionProperties(engine, target);
        String url = jdbcUrl(engine, target);
        long start = System.nanoTime();
        try (Connection conn = DriverManager.getConnection(url, props)) {
            conn.setAutoCommit(false);
            try {
                for (CellPatchGrouper.GroupedSqlUpdate row : grouped) {
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
                        int updated = stmt.executeUpdate();
                        if (updated != 1) {
                            conn.rollback();
                            throw new DomainException(
                                    NexusErrorCode.QUERY_FAILED,
                                    "No row matched primary key");
                        }
                    }
                }
                conn.commit();
                return new QueryResult(
                        List.of("updateCount"),
                        List.of(List.of(grouped.size())),
                        false,
                        elapsedMs(start),
                        grouped.size());
            } catch (DomainException ex) {
                conn.rollback();
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

    static String buildUpdateSql(
            DatabaseEngine engine,
            String schema,
            String table,
            CellPatchGrouper.GroupedSqlUpdate row) {
        StringBuilder sql = new StringBuilder("UPDATE ")
                .append(SqlIdentifierQuoter.quote(engine, schema))
                .append('.')
                .append(SqlIdentifierQuoter.quote(engine, table))
                .append(" SET ");
        int i = 0;
        for (String column : row.columns().keySet()) {
            if (i++ > 0) {
                sql.append(", ");
            }
            sql.append(SqlIdentifierQuoter.quote(engine, column)).append(" = ?");
        }
        sql.append(" WHERE ");
        int j = 0;
        for (String column : row.primaryKey().keySet()) {
            if (j++ > 0) {
                sql.append(" AND ");
            }
            sql.append(SqlIdentifierQuoter.quote(engine, column)).append(" = ?");
        }
        return sql.toString();
    }
```

Point existing `updateCell` at `updateCells` with `CellPatchGrouper.groupSql(List.of(new SqlPatch(primaryKey, column, value)))`. Bind SET parameters in `row.columns().values()` order and WHERE in `row.primaryKey().values()` order.

- [ ] **Step 4: Run the two new IT methods**

Run: `cd backend && ./mvnw -Dtest=JdbcQueryExecutorIT test`

Expected: PASS (including rollback + missing PK)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutor.java \
  backend/src/test/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutorIT.java \
  backend/src/main/java/com/ivan/nexus/domain/database/CellPatchGrouper.java
git commit -m "feat: apply SQL cell drafts in one JDBC transaction"
```

---

### Task 5: Mongo `updateDocuments`

**Files:**
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/database/MongoQueryExecutor.java`
- Modify: `backend/src/test/java/com/ivan/nexus/infrastructure/database/MongoQueryExecutorIT.java`

Testcontainers `mongo:7` is standalone (no replica set).

- [ ] **Step 1: Write the failing IT**

```java
    @Test
    void twoDocumentSaveWithoutReplicaSetWritesNothing() {
        ResolvedTarget target = new ResolvedTarget(mongo.getHost(), mongo.getMappedPort(27017), null, null, "app");
        executor.execute(target, insert("jobs", new Document("_id", "a").append("status", "queued")));
        executor.execute(target, insert("jobs", new Document("_id", "b").append("status", "queued")));
        List<CellPatchGrouper.MongoPatch> patches = List.of(
                new CellPatchGrouper.MongoPatch("a", "status", "running"),
                new CellPatchGrouper.MongoPatch("b", "status", "running"));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> executor.updateDocuments(
                        target,
                        "app",
                        "jobs",
                        CellPatchGrouper.groupMongo(patches)));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        QueryResult found = executor.execute(target, new MongoStatement(
                "find", "app", "jobs", "{}", null, null, null, null, 100, false, StatementClass.READ, false));
        assertTrue(found.rows().stream().allMatch(row -> "queued".equals(String.valueOf(row.get(found.columns().indexOf("status"))))));
    }

    private static MongoStatement insert(String collection, Document document) {
        return new MongoStatement(
                "insert", "app", collection, "{}", null, null, document.toJson(), null, 100, false, StatementClass.WRITE, false);
    }
```

Add imports for `CellPatchGrouper`, `DomainException`, `NexusErrorCode`, `List`, `assertThrows`, `assertEquals`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=MongoQueryExecutorIT#twoDocumentSaveWithoutReplicaSetWritesNothing test`

Expected: FAIL — missing `updateDocuments`

- [ ] **Step 3: Implement `updateDocuments`**

```java
    public QueryResult updateDocuments(
            ResolvedTarget target,
            String database,
            String collection,
            List<CellPatchGrouper.GroupedMongoUpdate> grouped) {
        if (grouped == null || grouped.isEmpty()) {
            throw new DomainException(NexusErrorCode.QUERY_NOT_ALLOWED, "Patches are required");
        }
        long start = System.nanoTime();
        try (MongoClient client = MongoClients.create(uri(target))) {
            MongoCollection<Document> coll = client.getDatabase(database).getCollection(collection);
            if (grouped.size() == 1) {
                applyMongoUpdate(coll, null, grouped.getFirst());
                return writeResult(1, start);
            }
            try (com.mongodb.client.ClientSession session = client.startSession()) {
                try {
                    session.startTransaction();
                } catch (RuntimeException ex) {
                    throw new DomainException(
                            NexusErrorCode.QUERY_NOT_ALLOWED,
                            "Multi-document Save needs a replica set");
                }
                try {
                    for (CellPatchGrouper.GroupedMongoUpdate row : grouped) {
                        applyMongoUpdate(coll, session, row);
                    }
                    session.commitTransaction();
                    return writeResult(grouped.size(), start);
                } catch (RuntimeException ex) {
                    session.abortTransaction();
                    throw ex;
                }
            }
        } catch (DomainException ex) {
            throw ex;
        } catch (MongoException ex) {
            throw new DomainException(
                    NexusErrorCode.QUERY_FAILED,
                    SecretSanitizer.strip(target.password(), ex.getMessage()));
        }
    }

    private static void applyMongoUpdate(
            MongoCollection<Document> coll,
            com.mongodb.client.ClientSession session,
            CellPatchGrouper.GroupedMongoUpdate row) {
        Document set = new Document();
        row.fields().forEach(set::put);
        Document update = new Document("$set", set);
        com.mongodb.client.result.UpdateResult result = session == null
                ? coll.updateOne(Filters.eq("_id", parseId(row.id())), update)
                : coll.updateOne(session, Filters.eq("_id", parseId(row.id())), update);
        if (result.getMatchedCount() != 1) {
            throw new DomainException(NexusErrorCode.QUERY_FAILED, "No row matched primary key");
        }
    }
```

Keep `updateCell` as a one-document wrapper until Task 6.

- [ ] **Step 4: Run the IT**

Run: `cd backend && ./mvnw -Dtest=MongoQueryExecutorIT test`

Expected: PASS. After the error, both documents still `queued`.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/infrastructure/database/MongoQueryExecutor.java \
  backend/src/test/java/com/ivan/nexus/infrastructure/database/MongoQueryExecutorIT.java
git commit -m "feat: apply Mongo cell drafts atomically or reject multi-doc"
```

---

### Task 6: `EditDatabaseCells` (replace `EditDatabaseCell`)

**Files:**
- Create: `backend/src/main/java/com/ivan/nexus/application/database/EditDatabaseCells.java`
- Test: `backend/src/test/java/com/ivan/nexus/application/database/EditDatabaseCellsTest.java`
- Delete after switch: `EditDatabaseCell.java` (Task 7)

- [ ] **Step 1: Write the failing test**

```java
package com.ivan.nexus.application.database;

import com.ivan.nexus.application.audit.RecordAudit;
import com.ivan.nexus.domain.database.DatabaseEngine;
import com.ivan.nexus.domain.database.DatabaseInstance;
import com.ivan.nexus.domain.database.DatabaseStatus;
import com.ivan.nexus.domain.database.QueryResult;
import com.ivan.nexus.domain.database.ResolvedTarget;
import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;
import com.ivan.nexus.infrastructure.database.JdbcQueryExecutor;
import com.ivan.nexus.infrastructure.database.MongoQueryExecutor;
import com.ivan.nexus.infrastructure.persistence.user.UserJpaRepository;
import com.ivan.nexus.interfaces.database.DatabaseDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditDatabaseCellsTest {

    @Mock DiscoverProjectDatabases discover;
    @Mock JdbcQueryExecutor jdbc;
    @Mock MongoQueryExecutor mongo;
    @Mock RecordAudit recordAudit;
    @Mock UserJpaRepository users;
    @InjectMocks EditDatabaseCells edit;

    @Test
    void emptyPatchesAreNotAllowed() {
        stubReady(DatabaseEngine.POSTGRES);
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute("lab", "lab:db", body(List.of()), "ADMIN", "admin", "127.0.0.1"));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(jdbc, never()).updateCells(any(), any(), any(), any(), any());
    }

    @Test
    void patchingPrimaryKeyColumnIsNotAllowed() {
        stubReady(DatabaseEngine.POSTGRES);
        DatabaseDtos.CellsRequest body = new DatabaseDtos.CellsRequest(
                "public",
                "jobs",
                null,
                null,
                List.of(new DatabaseDtos.CellPatch(Map.of("id", 1), "id", "2", null, null)));
        DomainException ex = assertThrows(
                DomainException.class,
                () -> edit.execute("lab", "lab:db", body, "ADMIN", "admin", "127.0.0.1"));
        assertEquals(NexusErrorCode.QUERY_NOT_ALLOWED, ex.getCode());
        verify(jdbc, never()).updateCells(any(), any(), any(), any(), any());
    }

    private void stubReady(DatabaseEngine engine) {
        DatabaseInstance instance = new DatabaseInstance(
                "lab:db", "lab", "abc", "db", engine, DatabaseStatus.READY, "lab");
        when(discover.resolve("lab", "lab:db"))
                .thenReturn(new InstanceResolution(instance, new ResolvedTarget("127.0.0.1", 5432, "lab", "lab", "lab")));
    }

    private static DatabaseDtos.CellsRequest body(List<DatabaseDtos.CellPatch> patches) {
        return new DatabaseDtos.CellsRequest("public", "jobs", null, null, patches);
    }
}
```

This will not compile until `CellsRequest` / `CellPatch` / `EditDatabaseCells` exist. Add the DTO records first in Step 3 if the test cannot be compiled otherwise — still leave `execute` throwing `UnsupportedOperationException` so the test fails on behavior, or add DTOs then run: expected FAIL on missing `EditDatabaseCells`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=EditDatabaseCellsTest test`

Expected: FAIL — `EditDatabaseCells` / `CellsRequest` not found

- [ ] **Step 3: Add DTOs and the use case**

In `DatabaseDtos.java` replace `CellRequest` with:

```java
    public record CellsRequest(
            String schema,
            String table,
            String mongoDatabase,
            String collection,
            List<CellPatch> patches) {}

    public record CellPatch(
            Map<String, Object> primaryKey,
            String column,
            Object value,
            String id,
            String field) {}
```

`EditDatabaseCells.java` (mirror `EditDatabaseCell` wiring: `ControlPlaneDatabase.requireAdminForDataAccess`, `GetDatabaseMetadata.requireReady`, then engine branch):

- SQL: require non-blank schema/table; reject engine MONGO; map patches to `SqlPatch`; if any `primaryKey.containsKey(column)` throw `QUERY_NOT_ALLOWED` `"Cannot edit primary key"`; `jdbc.updateCells(...)`; audit `DB_CELL_EDIT` once with `rowCount = result.rowCount()`, `class=WRITE`, no statement, no row payloads.
- Mongo: require mongoDatabase/collection; reject non-MONGO; if `field` is `_id` throw; `mongo.updateDocuments(...)`; same audit.
- Engine mismatch (SQL fields on MONGO instance): `QUERY_NOT_ALLOWED`.

`updateCell` can now be deleted from both executors **after** this class no longer calls it. Do that in this task once `EditDatabaseCells` is the only writer.

- [ ] **Step 4: Run `EditDatabaseCellsTest`**

Run: `cd backend && ./mvnw -Dtest=EditDatabaseCellsTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/application/database/EditDatabaseCells.java \
  backend/src/test/java/com/ivan/nexus/application/database/EditDatabaseCellsTest.java \
  backend/src/main/java/com/ivan/nexus/interfaces/database/DatabaseDtos.java \
  backend/src/main/java/com/ivan/nexus/infrastructure/database/JdbcQueryExecutor.java \
  backend/src/main/java/com/ivan/nexus/infrastructure/database/MongoQueryExecutor.java
git commit -m "feat: validate and apply Browse cell batches in EditDatabaseCells"
```

---

### Task 7: HTTP `POST /cells` and remove `/cell`

**Files:**
- Modify: `backend/src/main/java/com/ivan/nexus/interfaces/database/DatabaseController.java`
- Modify: `backend/src/main/java/com/ivan/nexus/infrastructure/security/SecurityConfig.java`
- Modify: `backend/src/test/java/com/ivan/nexus/interfaces/database/DatabaseControllerTest.java`
- Delete: `backend/src/main/java/com/ivan/nexus/application/database/EditDatabaseCell.java`

- [ ] **Step 1: Write failing controller tests** (keep existing tests compiling by swapping the mock type)

Replace `@MockitoBean EditDatabaseCell editCell` with `@MockitoBean EditDatabaseCells editCells`.

Add:

```java
    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerPostCellsReturns403() throws Exception {
        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/cells")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"public\",\"table\":\"jobs\",\"patches\":[{\"primaryKey\":{\"id\":1},\"column\":\"status\",\"value\":\"running\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPostCellIsGone() throws Exception {
        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/cell")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"public\",\"table\":\"jobs\",\"primaryKey\":{\"id\":1},\"column\":\"status\",\"value\":\"running\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPostCellsReturns200() throws Exception {
        given(editCells.execute(eq("lab"), eq("lab:aaaaaaaaaaaa"), any(), eq("ADMIN"), eq("admin"), any()))
                .willReturn(new QueryResult(List.of("updateCount"), List.of(List.of(1)), false, 1, 1));
        mockMvc.perform(post("/api/projects/lab/database/instances/lab:aaaaaaaaaaaa/cells")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schema\":\"public\",\"table\":\"jobs\",\"patches\":[{\"primaryKey\":{\"id\":1},\"column\":\"status\",\"value\":\"running\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1));
    }
```

Need `import static org.mockito.ArgumentMatchers.any;` (already present).

- [ ] **Step 2: Run `DatabaseControllerTest` — new tests fail; old tests must still compile**

Run: `cd backend && ./mvnw -Dtest=DatabaseControllerTest test`

Expected: FAIL on `/cells` 404 or missing bean until Step 3

- [ ] **Step 3: Wire controller and security**

`DatabaseController`: inject `EditDatabaseCells`. Replace `POST .../cell` with:

```java
    @PostMapping("/instances/{databaseId}/cells")
    public DatabaseDtos.QueryResponse cells(
            @PathVariable String projectId,
            @PathVariable String databaseId,
            @RequestBody DatabaseDtos.CellsRequest body,
            Authentication authentication,
            HttpServletRequest request) {
        return toResponse(editCells.execute(
                projectId,
                databaseId,
                body,
                role(authentication),
                authentication.getName(),
                ClientIp.resolve(request)));
    }
```

`SecurityConfig`: change `/cell` matcher to `/cells`.

Delete `EditDatabaseCell.java`. Fix any remaining references.

- [ ] **Step 4: Run tests**

Run: `cd backend && ./mvnw -Dtest=DatabaseControllerTest,EditDatabaseCellsTest,CellPatchGrouperTest,JdbcQueryExecutorIT,MongoQueryExecutorIT test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ivan/nexus/interfaces/database/DatabaseController.java \
  backend/src/main/java/com/ivan/nexus/infrastructure/security/SecurityConfig.java \
  backend/src/test/java/com/ivan/nexus/interfaces/database/DatabaseControllerTest.java
git rm backend/src/main/java/com/ivan/nexus/application/database/EditDatabaseCell.java
git commit -m "feat: replace POST /cell with transactional POST /cells"
```

---

### Task 8: Browse UI — draft overlay, SQL bar, dirty navigation

**Files:**
- Modify: `frontend/features/database/api.ts`
- Modify: `frontend/features/database/data-grid.tsx`
- Modify: `frontend/features/database/database-page.tsx`

No React Testing Library in this repo. Behavior of dirty/cancel is covered by Task 2. This task is wiring.

- [ ] **Step 1: Replace `postCell` with `postCells`**

```ts
export function postCells(
  projectId: string,
  databaseId: string,
  body: {
    schema?: string
    table?: string
    mongoDatabase?: string
    collection?: string
    patches: Array<{
      primaryKey?: Record<string, unknown>
      column?: string
      value?: unknown
      id?: string
      field?: string
    }>
  },
) {
  return api<QueryResult>(
    `/api/projects/${projectId}/database/instances/${encodeURIComponent(databaseId)}/cells`,
    { method: 'POST', body: JSON.stringify(body) },
  )
}
```

Delete `postCell`.

- [ ] **Step 2: Change `DataGrid` so it never calls the API**

Props:

```ts
{
  result: QueryResult
  canEdit: boolean
  primaryKey: string[]
  idColumn: string | null
  draft: CellDraft
  resetToken: number
  onDraftChange: (row: Record<string, unknown>, column: string, input: string, original: unknown) => void
}
```

For each body cell:

- Build `record` from columns as today.
- `key` = `idColumn ? String(record[idColumn] ?? '') : rowKey(pkFrom(record, primaryKey), primaryKey)`
- Overlay `value = displayValue(draft, key, column, row[columnIndex])`
- `dirty = draft[key] != null && column in draft[key]`
- `td` / `input` outline when dirty: `outline outline-1 outline-[#f5f5f5]`
- `EditableCell` takes `value`, `resetToken`. `useEffect` resets local input when `value` or `resetToken` changes.
- `onBlur` / Enter: `onDraftChange(record, column, localInput, originalFromPreview)` — original is `row[columnIndex]` from `result`, not the overlay.

PK / `_id` still not editable.

Query-tab grid: `draft={emptyDraft()}`, `onDraftChange={() => undefined}`, `canEdit={false}`.

- [ ] **Step 3: Wire `DatabasePage`**

State: `draft`, `resetToken`, `navBlocked`.

`isAdmin` && PK/`_id` present → grid editable.

When `dirtyCount(draft) > 0`, render above the grid:

```tsx
<div className="flex items-center justify-between text-sm text-[#888]">
  <span>{dirtyCount(draft)} {dirtyCount(draft) === 1 ? 'cambio' : 'cambios'}</span>
  <span className="flex gap-3">
    <button type="button" onClick={onCancel} className="text-[#888]">Cancelar</button>
    <button type="button" onClick={onSave} disabled={save.isPending} className="text-[#f5f5f5]">
      Guardar
    </button>
  </span>
</div>
<pre className="overflow-auto border border-[#2a2a2a] p-3 font-mono text-sm text-[#888] whitespace-pre-wrap">
  {previewSelection.kind === 'sql'
    ? formatSqlStatements(engine === 'MYSQL' ? 'MYSQL' : 'POSTGRES', previewSelection.schema, previewSelection.table, toSqlRows(...))
    : formatMongoStatements(previewSelection.collection, toMongoRows(...))}
</pre>
```

`onCancel`: `setDraft(emptyDraft()); setResetToken((n) => n + 1); setBrowseError(null)` — no fetch.

`onSave`: `postCells` with `sqlPatches` or `mongoPatches`. `onSuccess`: clear draft, bump `resetToken`, `invalidateQueries` preview key (same as today’s cell mutation). `onError`: set `browseError` to `error.message`, do **not** invalidate, do **not** clear draft. Disable Guardar while `save.isPending`.

Dirty navigation: if `dirtyCount(draft) > 0`, `SchemaTree.onSelect`, `InstanceSelect.onChange`, and Query tab button call `setNavBlocked(true)` and return. Dialog:

```tsx
{navBlocked ? (
  <div className="border border-[#f5f5f5] p-3 text-sm">
    <p>Hay {dirtyCount(draft)} cambios sin guardar. Guarda o cancela antes de cambiar.</p>
    <div className="mt-2 text-right">
      <button type="button" onClick={() => setNavBlocked(false)}>Entendido</button>
    </div>
  </div>
) : null}
```

Remove the render-time instance-switch reset that clears selection, **or** keep it only when `dirtyCount(draft) === 0`. Do not add `beforeunload`.

Build preview row keys in table order:

```ts
result.rows.map((row) => {
  const record = Object.fromEntries(result.columns.map((c, i) => [c, row[i]]))
  if (previewSelection.kind === 'mongo') {
    return String(record._id ?? '')
  }
  const pk: Record<string, unknown> = {}
  for (const column of previewSelection.primaryKey) {
    pk[column] = record[column]
  }
  return rowKey(pk, previewSelection.primaryKey)
})
```

- [ ] **Step 4: Run frontend unit tests and lint/build if already used in CI**

Run: `cd frontend && npx vitest run features/database`

Expected: PASS (draft-sql, cell-draft, cell-edit, browse-selection, classify-sql)

Run: `cd frontend && npx tsc --noEmit` if `tsconfig` allows; otherwise `npm run lint`.

Fix type errors from removed `postCell` / new `DataGrid` props.

- [ ] **Step 5: Commit**

```bash
git add frontend/features/database/api.ts \
  frontend/features/database/data-grid.tsx \
  frontend/features/database/database-page.tsx
git commit -m "feat: Save and Cancel a local Browse grid draft"
```

---

## Self-review (spec coverage)

| Spec | Task |
|---|---|
| Local draft, Guardar/Cancelar, SQL above grid | 1, 2, 8 |
| Multi-row, one statement per row | 1–4, 8 |
| Preview text not executed | 8 sends patches |
| All-or-nothing JDBC | 4 |
| 0 matched rows → fail | 4 |
| Mongo multi-doc without replica set | 5 |
| UPDATE only; no insert/delete; PK locked | 6, 8 |
| Block table/instance/Query while dirty | 8 |
| No beforeunload | 8 |
| `POST /cells`, remove `/cell` | 7 |
| VIEWER 403 | 7 |
| Empty / PK patch validation | 3, 6 |
| One `DB_CELL_EDIT` audit on success | 6 |
| Open→run→close, no pool | 4, 5 |

No TBD. Types: `CellsRequest` / `CellPatch` / `CellPatchGrouper.SqlPatch` / `updateCells` / `updateDocuments` / `postCells` are named the same in every task. `EditDatabaseCell` is deleted only in Task 7 after Task 6 introduced `EditDatabaseCells`.
