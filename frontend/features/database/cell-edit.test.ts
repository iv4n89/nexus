import { describe, expect, it } from 'vitest'
import { shouldCommitCell } from './cell-edit'

describe('shouldCommitCell', () => {
  it('does not POST when a null cell is blurred without typing', () => {
    expect(shouldCommitCell('', null)).toBe(false)
    expect(shouldCommitCell('null', null)).toBe(false)
  })

  it('commits when the user actually changes the value', () => {
    expect(shouldCommitCell('hello', null)).toBe(true)
    expect(shouldCommitCell('2', 1)).toBe(true)
    expect(shouldCommitCell('1', 1)).toBe(false)
  })
})
