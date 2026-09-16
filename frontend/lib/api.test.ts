import { describe, expect, it } from 'vitest'
import { shouldRedirectToLogin } from './api'

describe('shouldRedirectToLogin', () => {
  it('sends expired sessions to login from the app', () => {
    expect(shouldRedirectToLogin(401, '/')).toBe(true)
    expect(shouldRedirectToLogin(401, '/projects/nexus')).toBe(true)
  })

  it('keeps login failures on the login page', () => {
    expect(shouldRedirectToLogin(401, '/login')).toBe(false)
  })

  it('does not treat forbidden as a missing session', () => {
    expect(shouldRedirectToLogin(403, '/settings')).toBe(false)
  })
})
