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
