function stripSqlComments(sql: string): string {
  let out = ''
  let i = 0
  while (i < sql.length) {
    const c = sql[i]
    if (c === "'" || c === '"') {
      const copied = copyQuoted(sql, i, c)
      out += copied.text
      i = copied.next
    } else if (c === '-' && sql[i + 1] === '-') {
      const newline = sql.indexOf('\n', i)
      if (newline < 0) {
        break
      }
      out += ' '
      i = newline
    } else if (c === '/' && sql[i + 1] === '*') {
      const end = sql.indexOf('*/', i + 2)
      if (end < 0) {
        break
      }
      out += ' '
      i = end + 2
    } else {
      out += c
      i++
    }
  }
  return out
}

function copyQuoted(sql: string, start: number, quote: string): { text: string; next: number } {
  let text = quote
  let i = start + 1
  while (i < sql.length) {
    const c = sql[i]
    text += c
    i++
    if (c === quote) {
      if (sql[i] === quote) {
        text += quote
        i++
        continue
      }
      return { text, next: i }
    }
  }
  return { text, next: i }
}

export function isReadSql(sql: string): boolean {
  const stripped = stripSqlComments(sql).trim()
  if (!stripped) {
    return false
  }
  const keyword = stripped.match(/^[A-Za-z]+/)?.[0]?.toUpperCase()
  if (keyword !== 'SELECT' && keyword !== 'WITH') {
    return false
  }
  if (/\bINTO\b/i.test(stripped) || /\bFOR\s+UPDATE\b/i.test(stripped) || /\bFOR\s+SHARE\b/i.test(stripped)) {
    return false
  }
  if (keyword === 'WITH' && /\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE)\b/i.test(stripped)) {
    return false
  }
  return true
}

export function isDestructiveSql(sql: string): boolean {
  const stripped = stripSqlComments(sql).trim()
  const keyword = stripped.match(/^[A-Za-z]+/)?.[0]?.toUpperCase()
  if (keyword === 'DROP' || keyword === 'TRUNCATE') {
    return true
  }
  if ((keyword === 'DELETE' || keyword === 'UPDATE') && !/\bWHERE\b/i.test(stripped)) {
    return true
  }
  return false
}

export function isReadMongo(statement: string): boolean {
  try {
    const parsed = JSON.parse(statement) as { op?: string }
    return parsed.op === 'find' || parsed.op === 'aggregate'
  } catch {
    return false
  }
}
