import { api } from '@/lib/api'

export type AuthUser = {
  username: string
  role: string
}

export async function fetchCsrf(): Promise<void> {
  await api<{ token: string }>('/api/auth/csrf')
}

export async function login(username: string, password: string): Promise<AuthUser> {
  await fetchCsrf()
  return api<AuthUser>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export async function me(): Promise<AuthUser> {
  return api<AuthUser>('/api/auth/me')
}
