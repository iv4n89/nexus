'use client'

import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { StatusDot, toneFromHealthAndState } from '@/components/status-dot'
import { api } from '@/lib/api'
import { stripSlash } from '@/lib/docker'
import { formatBytes, formatPercent, formatPort, formatUptime } from '@/lib/format'
import type { Container, ContainerMetrics } from '@/types/api'

export function ContainerView({
  projectId,
  containerId,
}: {
  projectId: string
  containerId: string
}) {
  const container = useQuery({
    queryKey: ['containers', containerId],
    queryFn: () => api<Container>(`/api/containers/${containerId}`),
  })
  const stats = useQuery({
    queryKey: ['containers', containerId, 'stats'],
    queryFn: () => api<ContainerMetrics>(`/api/containers/${containerId}/stats`),
  })

  if (container.isPending) {
    return <p className="text-sm text-[#888]">Loading…</p>
  }
  if (container.isError) {
    return <p className="text-sm text-[#ff4d4f]">{container.error.message}</p>
  }

  const data = container.data

  return (
    <div className="flex flex-col gap-8">
      <Link href={`/projects/${projectId}`} className="text-sm text-[#888]">
        ← {projectId}
      </Link>

      <h1 className="text-xl">{stripSlash(data.name)}</h1>

      <dl className="grid max-w-lg grid-cols-[7rem_1fr] gap-y-3 font-mono text-sm">
        <dt className="text-[#888]">Image</dt>
        <dd className="break-all">{data.image}</dd>

        <dt className="text-[#888]">State</dt>
        <dd className="flex items-center gap-2">
          <StatusDot
            tone={toneFromHealthAndState(data.health, data.state)}
            label={data.health ?? data.state}
          />
          {data.state}
        </dd>

        <dt className="text-[#888]">Health</dt>
        <dd>{data.health ?? '—'}</dd>

        <dt className="text-[#888]">Uptime</dt>
        <dd>{formatUptime(data.startedAt)}</dd>

        <dt className="text-[#888]">Restarts</dt>
        <dd>{data.restartCount}</dd>

        <dt className="text-[#888]">CPU</dt>
        <dd>
          {stats.isPending
            ? '…'
            : stats.isError
              ? stats.error.message
              : formatPercent(stats.data.cpuPercent)}
        </dd>

        <dt className="text-[#888]">RAM</dt>
        <dd>
          {stats.isPending
            ? '…'
            : stats.isError
              ? stats.error.message
              : stats.data.memoryLimitBytes > 0
                ? `${formatBytes(stats.data.memoryUsedBytes)} / ${formatBytes(stats.data.memoryLimitBytes)}`
                : formatBytes(stats.data.memoryUsedBytes)}
        </dd>

        <dt className="text-[#888]">Network</dt>
        <dd>
          {stats.isPending
            ? '…'
            : stats.isError
              ? stats.error.message
              : `RX ${formatBytes(stats.data.rxBytes)}  TX ${formatBytes(stats.data.txBytes)}`}
        </dd>

        <dt className="text-[#888]">Ports</dt>
        <dd>
          {data.ports.length === 0
            ? '—'
            : data.ports.map((port) => formatPort(port)).join(', ')}
        </dd>
      </dl>
    </div>
  )
}
