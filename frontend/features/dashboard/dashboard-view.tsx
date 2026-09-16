'use client'

import { useQuery } from '@tanstack/react-query'
import { ProjectList } from '@/features/projects/project-list'
import { api } from '@/lib/api'
import { formatPercent, usagePercent } from '@/lib/format'
import type { Project, SystemMetrics } from '@/types/api'

export function DashboardView() {
  const metrics = useQuery({
    queryKey: ['metrics', 'system'],
    queryFn: () => api<SystemMetrics>('/api/metrics/system'),
  })
  const projects = useQuery({
    queryKey: ['projects'],
    queryFn: () => api<Project[]>('/api/projects'),
  })

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
        <p className="text-sm">0 active alerts</p>
      </section>

      <hr className="border-[#2a2a2a]" />

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">RECENT ACTIVITY</h2>
        <p className="text-sm text-[#888]">No recent activity</p>
      </section>
    </div>
  )
}
