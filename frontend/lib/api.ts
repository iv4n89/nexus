export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(code: string, status: number, detail?: string) {
    super(detail ? `${code}: ${detail}` : code)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export function isAuthError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403)
}

export function shouldRedirectToLogin(status: number, pathname: string): boolean {
  return status === 401 && pathname !== '/login' && !pathname.startsWith('/login/')
}

export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400) {
    return false
  }
  return failureCount < 2
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const csrf = document.cookie
    .split('; ')
    .find((c) => c.startsWith('XSRF-TOKEN='))
    ?.split('=')[1]

  const res = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-XSRF-TOKEN': decodeURIComponent(csrf) } : {}),
      ...(init.headers ?? {}),
    },
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    const code = body?.error?.code ?? res.statusText
    const detail = typeof body?.error?.message === 'string' ? body.error.message : undefined
    const error = new ApiError(code, res.status, detail)
    if (typeof window !== 'undefined' && shouldRedirectToLogin(error.status, window.location.pathname)) {
      window.location.replace('/login')
      return new Promise<T>(() => {})
    }
    throw error
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
