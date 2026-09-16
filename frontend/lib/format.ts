export function usagePercent(used: number, total: number): number {
  if (!total) return 0
  return (used / total) * 100
}

export function formatPercent(value: number): string {
  const rounded = Number(value.toFixed(1))
  if (Number.isInteger(rounded)) return `${rounded}%`
  return `${rounded}%`
}

export function formatBytes(bytes: number): string {
  const sign = bytes < 0 ? '-' : ''
  const n = Math.abs(bytes)
  const gib = 1024 ** 3
  const mib = 1024 ** 2
  const kib = 1024
  if (n >= gib) return `${sign}${(n / gib).toFixed(1)} GiB`
  if (n >= mib) {
    const mibValue = n / mib
    const digits = mibValue >= 10 ? 0 : 1
    return `${sign}${mibValue.toFixed(digits)} MiB`
  }
  if (n >= kib) return `${sign}${(n / kib).toFixed(0)} KiB`
  return `${sign}${Math.round(n)} B`
}

export function formatUptime(startedAt: string | null): string {
  if (!startedAt) return '—'
  const started = Date.parse(startedAt)
  if (Number.isNaN(started)) return '—'
  const seconds = Math.max(0, Math.floor((Date.now() - started) / 1000))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${minutes}m`
  if (minutes > 0) return `${minutes}m`
  return `${seconds}s`
}

export function formatPort(mapping: { publicPort: number | null; privatePort: number }): string {
  if (mapping.publicPort == null) return String(mapping.privatePort)
  return `${mapping.publicPort} → ${mapping.privatePort}`
}

export function formatClock(iso: string | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString('en-GB', { hour12: false })
}

export function formatElapsed(startedAt: string | null, finishedAt: string | null): string | null {
  if (!startedAt || !finishedAt) return null
  const start = Date.parse(startedAt)
  const end = Date.parse(finishedAt)
  if (Number.isNaN(start) || Number.isNaN(end)) return null
  const seconds = Math.max(0, Math.round((end - start) / 1000))
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest === 0 ? `${minutes}m` : `${minutes}m ${rest}s`
}

export function healthOkLabel(healthOk: boolean | null): string {
  if (healthOk === true) return 'OK'
  if (healthOk === false) return 'FAIL'
  return '—'
}
