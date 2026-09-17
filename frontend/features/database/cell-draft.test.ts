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
