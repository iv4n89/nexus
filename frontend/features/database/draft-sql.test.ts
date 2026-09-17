import { describe, expect, it } from 'vitest'
import { formatMongoStatements, formatSqlDraft, formatSqlStatements } from './draft-sql'

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
