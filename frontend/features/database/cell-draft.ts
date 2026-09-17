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
