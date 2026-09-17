import type { AlertStatus } from '@/types/api'

export function alertsPath(status: AlertStatus = 'ACTIVE') {
  return `/api/alerts?status=${status}`
}
