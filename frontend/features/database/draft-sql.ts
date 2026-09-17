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
