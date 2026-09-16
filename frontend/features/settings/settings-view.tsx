'use client'

import { useQuery } from '@tanstack/react-query'
import { me } from '@/features/auth/api'
import { api } from '@/lib/api'
import type { AuthUser, Settings, SystemMetrics } from '@/types/api'

export function SettingsView() {
  const settings = useQuery({
    queryKey: ['settings'],
    queryFn: () => api<Settings>('/api/settings'),
  })
  const auth = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: (): Promise<AuthUser> => me(),
  })
  const docker = useQuery({
    queryKey: ['metrics', 'system'],
    queryFn: () => api<SystemMetrics>('/api/metrics/system'),
  })

  const settingsData = settings.data
  const authData = auth.data
  const error = settings.error ?? auth.error

  return (
    <div className="flex flex-col gap-10">
      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">SETTINGS</h2>
        {!settingsData || !authData ? (
          error ? (
            <p className="text-sm text-[#ff4d4f]">{error.message}</p>
          ) : (
            <p className="text-sm text-[#888]">Loading…</p>
          )
        ) : (
          <dl className="grid max-w-lg grid-cols-[9rem_1fr] gap-y-3 font-mono text-sm">
            <dt className="text-[#888]">Version</dt>
            <dd>{settingsData.version}</dd>

            <dt className="text-[#888]">User</dt>
            <dd>{authData.username}</dd>

            <dt className="text-[#888]">Role</dt>
            <dd>{authData.role}</dd>

            <dt className="text-[#888]">Activity</dt>
            <dd>{settingsData.retention.activityDays} days</dd>

            <dt className="text-[#888]">Deploy events</dt>
            <dd>{settingsData.retention.deploymentEventsDays} days</dd>

            <dt className="text-[#888]">Fingerprints</dt>
            <dd>{settingsData.retention.fingerprintDays} days</dd>

            <dt className="text-[#888]">Docker</dt>
            <dd className={docker.isError ? 'text-[#ff4d4f]' : undefined}>
              {docker.isPending
                ? '…'
                : docker.isError
                  ? docker.error.message
                  : 'reachable'}
            </dd>
          </dl>
        )}
      </section>
    </div>
  )
}
