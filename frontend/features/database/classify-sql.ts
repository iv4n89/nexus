export function isReadSql(sql: string): boolean {
  const stripped = sql
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/--[^\n]*/g, ' ')
    .trim()
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
  const stripped = sql
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/--[^\n]*/g, ' ')
    .trim()
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
