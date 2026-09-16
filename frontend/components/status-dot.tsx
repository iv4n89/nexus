export type StatusTone = 'up' | 'degraded' | 'down'

const TONE_COLOR: Record<StatusTone, string> = {
  up: '#ffffff',
  degraded: '#c6c6c6',
  down: '#ff4d4f',
}

export function toneFromStatus(status: string | null | undefined): StatusTone {
  const value = (status ?? '').toLowerCase()
  if (value === 'healthy' || value === 'running' || value === 'up') return 'up'
  if (value === 'exited' || value === 'dead' || value === 'stopped' || value === 'down') {
    return 'down'
  }
  return 'degraded'
}

export function toneFromHealthAndState(
  health: string | null | undefined,
  state: string | null | undefined,
): StatusTone {
  const h = (health ?? '').toLowerCase()
  const s = (state ?? '').toLowerCase()
  if (s === 'exited' || s === 'dead' || s === 'stopped' || s === 'created' || s === 'removing') {
    return 'down'
  }
  if (h === 'unhealthy' || h === 'starting' || s === 'restarting' || s === 'paused') {
    return 'degraded'
  }
  if (s === 'running') return 'up'
  return toneFromStatus(s || h)
}

export function StatusDot({ tone, label }: { tone: StatusTone; label?: string }) {
  return (
    <span
      role="img"
      aria-label={label ?? tone}
      className="inline-block size-2 shrink-0 rounded-full"
      style={{ backgroundColor: TONE_COLOR[tone] }}
    />
  )
}
