import type { TrafficSeriesPoint } from '@/types/api'

export function polylinePoints(
  series: TrafficSeriesPoint[],
  field: 'requests' | 'status5xx',
  width: number,
  height: number,
  from?: string,
  to?: string,
): string {
  if (series.length === 0) {
    return ''
  }

  const values = series.map((point) => point[field])
  const max = Math.max(...values)

  const fromMs = from ? Date.parse(from) : NaN
  const toMs = to ? Date.parse(to) : NaN
  const useTimestamps =
    Number.isFinite(fromMs) && Number.isFinite(toMs) && toMs > fromMs

  return series
    .map((point, index) => {
      let x: number
      if (useTimestamps) {
        const tMs = Date.parse(point.t)
        const ratio = Number.isFinite(tMs) ? (tMs - fromMs) / (toMs - fromMs) : 0
        x = Math.min(width, Math.max(0, ratio * width))
      } else {
        x = series.length === 1 ? 0 : (index / (series.length - 1)) * width
      }
      const y = max === 0 ? height : height - (values[index] / max) * height
      return `${x},${y}`
    })
    .join(' ')
}
