import { describe, expect, it } from 'vitest'
import { polylinePoints } from './series'

function point(t: string, requests: number, status5xx = 0) {
  return {
    t,
    requests,
    status5xx,
    latencyAvgMs: 0,
    latencyMaxMs: null,
  }
}

describe('polylinePoints', () => {
  it('returns an empty string for an empty series', () => {
    expect(polylinePoints([], 'requests', 640, 160)).toBe('')
  })

  it('increases x across two points', () => {
    const series = [point('2026-01-01T00:00:00Z', 10), point('2026-01-01T01:00:00Z', 20)]
    const coords = polylinePoints(series, 'requests', 640, 160)
      .split(' ')
      .map((pair) => Number(pair.split(',')[0]))
    expect(coords).toHaveLength(2)
    expect(coords[0]).toBeLessThan(coords[1])
  })
})
