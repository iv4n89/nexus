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
