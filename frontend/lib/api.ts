export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function isAuthError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.status === 403)
}

export function shouldRedirectToLogin(status: number, pathname: string): boolean {
  return status === 401 && pathname !== '/login' && !pathname.startsWith('/login/')
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
    const error = new ApiError(body?.error?.code ?? res.statusText, res.status)
    if (typeof window !== 'undefined' && shouldRedirectToLogin(error.status, window.location.pathname)) {
      window.location.replace('/login')
      return new Promise<T>(() => {})
    }
    throw error
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}
