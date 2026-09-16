export const LOG_LEVELS = ['ALL', 'INFO', 'WARN', 'ERROR'] as const

export type LogLevel = (typeof LOG_LEVELS)[number]

const ISO_TIMESTAMP = /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})?/g

export function parseLogLevel(value: string | null): LogLevel {
  const upper = value?.toUpperCase()
  return LOG_LEVELS.find((level) => level === upper) ?? 'ALL'
}

export function matchesLogLine(line: string, level: LogLevel, query: string): boolean {
  if (level !== 'ALL' && !matchesLogLevel(line, level)) {
    return false
  }
  const needle = query.trim().toLowerCase()
  return !needle || line.toLowerCase().includes(needle)
}

export function matchesLogLevel(line: string, level: LogLevel): boolean {
  const haystack = line.toLowerCase()
  switch (level) {
    case 'ALL':
      return true
    case 'ERROR':
      return (
        haystack.includes('error') ||
        haystack.includes('exception') ||
        haystack.includes('fatal') ||
        haystack.includes('traceback') ||
        haystack.includes('emerg')
      )
    case 'WARN':
      return haystack.includes('warn')
    case 'INFO':
      return haystack.includes('info')
  }
}

export function logSearchNeedle(sample: string, max = 80): string {
  const stripped = sample.replace(ISO_TIMESTAMP, ' ').replace(/\s+/g, ' ').trim()
  return (stripped || sample).slice(0, max)
}

export function logsHref(
  projectId: string,
  options: { serviceId?: string | null; query?: string | null; level?: string } = {},
): string {
  const params = new URLSearchParams()
  if (options.serviceId) {
    params.set('service', options.serviceId)
  }
  const query = options.query ? logSearchNeedle(options.query) : ''
  if (query) {
    params.set('q', query)
  }
  const level = options.level ?? (query ? undefined : 'ERROR')
  if (level && level.toUpperCase() !== 'ALL') {
    params.set('level', level)
  }
  const encoded = params.toString()
  return `/projects/${projectId}/logs${encoded ? `?${encoded}` : ''}`
}
