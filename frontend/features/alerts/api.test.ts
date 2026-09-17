import { describe, expect, it } from 'vitest'
import { alertsPath } from './api'

describe('alertsPath', () => {
  it('requests ACTIVE alerts when the UI says active', () => {
    expect(alertsPath()).toBe('/api/alerts?status=ACTIVE')
  })
})
