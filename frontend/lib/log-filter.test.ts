import { describe, expect, it } from 'vitest'
import { logSearchNeedle, logsHref, matchesLogLine } from './log-filter'

describe('matchesLogLine', () => {
  it('keeps exception and fatal lines on ERROR without the word error', () => {
    expect(matchesLogLine('java.net.ConnectException: Connection refused', 'ERROR', '')).toBe(true)
    expect(matchesLogLine('FATAL panic in worker', 'ERROR', '')).toBe(true)
    expect(matchesLogLine('Traceback (most recent call last):', 'ERROR', '')).toBe(true)
    expect(matchesLogLine('INFO started successfully', 'ERROR', '')).toBe(false)
    expect(matchesLogLine('INFO timeout ignored', 'ERROR', '')).toBe(false)
  })

  it('keeps warning lines on WARN', () => {
    expect(matchesLogLine('WARN retry', 'WARN', '')).toBe(true)
    expect(matchesLogLine('WARNING deprecated api', 'WARN', '')).toBe(true)
    expect(matchesLogLine('INFO started', 'WARN', '')).toBe(false)
  })

  it('applies query and level together', () => {
    expect(matchesLogLine('ERROR timeout on db', 'ERROR', 'timeout')).toBe(true)
    expect(matchesLogLine('INFO timeout ignored', 'ERROR', 'timeout')).toBe(false)
  })
})

describe('logSearchNeedle', () => {
  it('strips timestamps so docker-prefixed lines still match', () => {
    expect(
      logSearchNeedle('2026-09-16T14:01:53.759Z  WARN 1 --- DockerLogProvider : Failed to fetch'),
    ).toBe('WARN 1 --- DockerLogProvider : Failed to fetch')
  })
})

describe('logsHref', () => {
  it('opens ERROR logs for a service without a sample query', () => {
    expect(logsHref('nexus', { serviceId: 'backend', level: 'ERROR' })).toBe(
      '/projects/nexus/logs?service=backend&level=ERROR',
    )
  })

  it('does not AND ERROR with a fingerprint sample that may lack the word error', () => {
    expect(
      logsHref('nexus', {
        serviceId: 'backend',
        query: '2026-09-16T14:01:53.759Z  WARN 1 --- Failed to fetch logs',
      }),
    ).toBe('/projects/nexus/logs?service=backend&q=WARN+1+---+Failed+to+fetch+logs')
  })
})
