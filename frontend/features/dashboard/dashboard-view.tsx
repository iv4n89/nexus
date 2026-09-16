'use client'

import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { ActivityTimeline, useActivityEvents } from '@/features/activity/timeline'
import { ProjectList } from '@/features/projects/project-list'
import { api } from '@/lib/api'
import { formatPercent, usagePercent } from '@/lib/format'
import type { Alert, Project, SystemMetrics } from '@/types/api'

export function DashboardView() {
  const metrics = useQuery({
    queryKey: ['metrics', 'system'],
    queryFn: () => api<SystemMetrics>('/api/metrics/system'),
  })
  const projects = useQuery({
    queryKey: ['projects'],
    queryFn: () => api<Project[]>('/api/projects'),
  })

  const alerts = useQuery({
    queryKey: ['alerts'],
    queryFn: () => api<Alert[]>('/api/alerts'),
    refetchInterval: 30_000,
  })
  const activity = useActivityEvents(8)

  return (
    <div className="flex flex-col gap-10">
      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">VPS</h2>
        {metrics.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : metrics.isError ? (
          <p className="text-sm text-[#ff4d4f]">{metrics.error.message}</p>
        ) : (
          <dl className="grid max-w-xs grid-cols-[4.5rem_1fr] gap-y-2 font-mono text-sm">
            <dt className="text-[#888]">CPU</dt>
            <dd>{formatPercent(metrics.data.cpuPercent)}</dd>
            <dt className="text-[#888]">RAM</dt>
            <dd>
              {formatPercent(
                usagePercent(metrics.data.memoryUsedBytes, metrics.data.memoryTotalBytes),
              )}
            </dd>
            <dt className="text-[#888]">DISK</dt>
            <dd>
              {formatPercent(usagePercent(metrics.data.diskUsedBytes, metrics.data.diskTotalBytes))}
            </dd>
          </dl>
        )}
      </section>

      <hr className="border-[#2a2a2a]" />

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">PROJECTS</h2>
        {projects.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : projects.isError ? (
          <p className="text-sm text-[#ff4d4f]">{projects.error.message}</p>
        ) : (
          <ProjectList projects={projects.data} />
        )}
      </section>

      <hr className="border-[#2a2a2a]" />

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">ALERTS</h2>
        {alerts.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : alerts.isError ? (
          <p className="text-sm text-[#ff4d4f]">{alerts.error.message}</p>
        ) : alerts.data.length === 0 ? (
          <p className="text-sm">0 active alerts</p>
        ) : (
          <div className="flex flex-col gap-3">
            <p className="text-sm">
              {alerts.data.length} active alert{alerts.data.length === 1 ? '' : 's'}
            </p>
            <ul>
              {alerts.data.slice(0, 8).map((alert) => {
                const label = [alert.type, alert.message].filter(Boolean).join(' — ')
                return (
                  <li key={alert.id} className="border-b border-[#2a2a2a] py-2 text-sm last:border-b-0">
                    {alert.projectId ? (
                      <Link href={`/projects/${alert.projectId}`} className="flex justify-between gap-4">
                        <span className="min-w-0 break-all">{label}</span>
                        <span className="shrink-0 text-[#888]">{alert.projectId}</span>
                      </Link>
                    ) : (
                      <span>{label}</span>
                    )}
                  </li>
                )
              })}
            </ul>
          </div>
        )}
      </section>

      <hr className="border-[#2a2a2a]" />

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">RECENT ACTIVITY</h2>
        {activity.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : activity.isError ? (
          <p className="text-sm text-[#ff4d4f]">{activity.error?.message ?? 'Unable to load activity'}</p>
        ) : (
          <ActivityTimeline events={activity.events} />
        )}
      </section>
    </div>
  )
}
