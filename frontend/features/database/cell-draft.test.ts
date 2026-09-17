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
