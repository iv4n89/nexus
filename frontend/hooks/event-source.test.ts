import { describe, expect, it } from 'vitest'
import { deploymentStreamUrl, shouldAbandonEventSource } from './event-source'

describe('deploymentStreamUrl', () => {
  it('opens the stream while a deploy is in progress or still loading', () => {
    expect(deploymentStreamUrl('abc', undefined)).toBe('/api/deployments/abc/stream')
    expect(deploymentStreamUrl('abc', 'PENDING')).toBe('/api/deployments/abc/stream')
    expect(deploymentStreamUrl('abc', 'RUNNING')).toBe('/api/deployments/abc/stream')
  })

  it('stops following terminal deployments', () => {
    expect(deploymentStreamUrl('abc', 'SUCCESS')).toBeNull()
    expect(deploymentStreamUrl('abc', 'FAILED')).toBeNull()
    expect(deploymentStreamUrl('abc', 'CANCELLED')).toBeNull()
  })
})

describe('shouldAbandonEventSource', () => {
  it('closes when the browser marks the stream CLOSED', () => {
    expect(shouldAbandonEventSource(2, 1)).toBe(true)
  })

  it('closes after repeated failures so the browser cannot reconnect forever', () => {
    expect(shouldAbandonEventSource(0, 2)).toBe(false)
    expect(shouldAbandonEventSource(0, 3)).toBe(true)
  })
})
