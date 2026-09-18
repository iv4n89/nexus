import type { TrafficSeriesPoint } from '@/types/api'

export function polylinePoints(
  series: TrafficSeriesPoint[],
  field: 'requests' | 'status5xx',
  width: number,
  height: number,
): string {
  if (series.length === 0) {
    return ''
  }

  const values = series.map((point) => point[field])
  const max = Math.max(...values)

  return series
    .map((point, index) => {
      const x = series.length === 1 ? 0 : (index / (series.length - 1)) * width
      const y = max === 0 ? height : height - (values[index] / max) * height
      return `${x},${y}`
    })
    .join(' ')
}
