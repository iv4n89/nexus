import type { TrafficSeriesPoint } from '@/types/api'
import { polylinePoints } from './series'

const WIDTH = 640
const HEIGHT = 160

export function TrafficChart({
  series,
  field,
  stroke,
}: {
  series: TrafficSeriesPoint[]
  field: 'requests' | 'status5xx'
  stroke: string
}) {
  const points = polylinePoints(series, field, WIDTH, HEIGHT)

  return (
    <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="w-full" role="img">
      <g stroke="#2a2a2a">
        <line x1={0} y1={0} x2={WIDTH} y2={0} />
        <line x1={0} y1={HEIGHT / 4} x2={WIDTH} y2={HEIGHT / 4} />
        <line x1={0} y1={HEIGHT / 2} x2={WIDTH} y2={HEIGHT / 2} />
        <line x1={0} y1={(HEIGHT * 3) / 4} x2={WIDTH} y2={(HEIGHT * 3) / 4} />
        <line x1={0} y1={HEIGHT} x2={WIDTH} y2={HEIGHT} />
        <line x1={0} y1={0} x2={0} y2={HEIGHT} />
        <line x1={WIDTH} y1={0} x2={WIDTH} y2={HEIGHT} />
      </g>
      {points ? <polyline points={points} fill="none" stroke={stroke} strokeWidth={1.5} /> : null}
    </svg>
  )
}
