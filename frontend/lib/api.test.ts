import { describe, expect, it } from 'vitest'
import { ApiError, shouldRedirectToLogin, shouldRetryQuery } from './api'

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

describe('shouldRetryQuery', () => {
  it('does not retry HTTP client or server errors', () => {
    expect(shouldRetryQuery(0, new ApiError('DATABASE_UNREACHABLE', 503))).toBe(false)
    expect(shouldRetryQuery(0, new ApiError('QUERY_FAILED', 400))).toBe(false)
  })

  it('keeps the error code and SQL detail in the message', () => {
    const error = new ApiError('QUERY_FAILED', 400, 'relation "jobs" does not exist')
    expect(error.code).toBe('QUERY_FAILED')
    expect(error.message).toBe('QUERY_FAILED: relation "jobs" does not exist')
  })

  it('retries transient failures twice', () => {
    expect(shouldRetryQuery(0, new Error('network'))).toBe(true)
    expect(shouldRetryQuery(2, new Error('network'))).toBe(false)
  })
})
