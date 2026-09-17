import { describe, expect, it } from 'vitest'
import { isDestructiveSql, isReadSql } from './classify-sql'

describe('classifySql', () => {
  it('treats SELECT as read', () => {
    expect(isReadSql('SELECT 1')).toBe(true)
  })

  it('does not treat DELETE as read', () => {
    expect(isReadSql('DELETE FROM t')).toBe(false)
  })

  it('marks DROP as destructive', () => {
    expect(isDestructiveSql('DROP TABLE users')).toBe(true)
  })

  it('does not treat dashes inside strings as comments', () => {
    expect(isReadSql("SELECT '-- not a comment'")).toBe(true)
  })
})
