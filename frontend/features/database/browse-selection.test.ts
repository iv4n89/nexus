import { describe, expect, it } from 'vitest'
import { boundTreeSelection } from './browse-selection'
import type { TreeSelection } from './schema-tree'

const jobs: TreeSelection = {
  kind: 'sql',
  schema: 'public',
  table: 'jobs',
  primaryKey: ['id'],
}

describe('boundTreeSelection', () => {
  it('drops the previous table when the instance changes before state resets', () => {
    expect(boundTreeSelection(jobs, 'lab:postgres', 'lab:mysql')).toBeNull()
  })

  it('keeps the selection on the instance it was chosen from', () => {
    expect(boundTreeSelection(jobs, 'lab:postgres', 'lab:postgres')).toEqual(jobs)
  })
})
