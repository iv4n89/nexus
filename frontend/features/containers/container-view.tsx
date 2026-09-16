'use client'

import Link from 'next/link'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { StatusDot, toneFromHealthAndState } from '@/components/status-dot'
import { me } from '@/features/auth/api'
import { ErrorList, logsHref } from '@/features/logs/error-list'
import { api } from '@/lib/api'
import { serviceLabel, stripSlash } from '@/lib/docker'
import { formatBytes, formatPercent, formatPort, formatUptime } from '@/lib/format'
import type { AuthUser, Container, ContainerMetrics, RecentError } from '@/types/api'

export function ContainerView({
  projectId,
  containerId,
}: {
  projectId: string
  containerId: string
}) {
  const [restarting, setRestarting] = useState(false)
  const [restartError, setRestartError] = useState<string | null>(null)
  const container = useQuery({
    queryKey: ['containers', containerId],
    queryFn: () => api<Container>(`/api/containers/${containerId}`),
  })
  const stats = useQuery({
    queryKey: ['containers', containerId, 'stats'],
    queryFn: () => api<ContainerMetrics>(`/api/containers/${containerId}/stats`),
  })
  const auth = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: (): Promise<AuthUser> => me(),
  })
  const serviceId = container.data ? (serviceLabel(container.data) ?? stripSlash(container.data.name)) : undefined
  const errors = useQuery({
    queryKey: ['projects', projectId, 'errors', serviceId],
    enabled: serviceId != null,
    queryFn: () =>
      api<RecentError[]>(
        `/api/projects/${encodeURIComponent(projectId)}/errors?serviceId=${encodeURIComponent(serviceId!)}`,
      ),
    refetchInterval: 30_000,
  })
  const canRestart = auth.data?.role === 'ADMIN'

  if (container.isPending) {
    return <p className="text-sm text-[#888]">Loading…</p>
  }
  if (container.isError) {
    return <p className="text-sm text-[#ff4d4f]">{container.error.message}</p>
  }

  const data = container.data

  async function onRestart() {
    if (!canRestart || restarting) {
      return
    }
    if (!window.confirm('Restart this container?')) {
      return
    }
    setRestartError(null)
    setRestarting(true)
    try {
      await api(`/api/containers/${containerId}/restart`, { method: 'POST' })
      await container.refetch()
    } catch (error) {
      setRestartError(error instanceof Error ? error.message : 'RESTART_FAILED')
    } finally {
      setRestarting(false)
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <Link href={`/projects/${projectId}`} className="text-sm text-[#888]">
        ← {projectId}
      </Link>

      <h1 className="text-xl">{stripSlash(data.name)}</h1>
      {serviceId ? <p className="font-mono text-sm text-[#888]">{serviceId}</p> : null}

      <div className="flex flex-col gap-2">
        <button
          type="button"
          disabled={!canRestart || restarting}
          onClick={() => void onRestart()}
          className={
            canRestart && !restarting
              ? 'w-fit border border-[#f5f5f5] px-4 py-2 text-sm text-[#f5f5f5]'
              : 'w-fit cursor-not-allowed border border-[#2a2a2a] px-4 py-2 text-sm text-[#888]'
          }
        >
          RESTART
        </button>
        {restartError ? <p className="text-sm text-[#ff4d4f]">{restartError}</p> : null}
        <Link href={logsHref(projectId, { serviceId, level: 'ERROR' })} className="w-fit text-sm text-[#ff4d4f]">
          LOGS
        </Link>
      </div>

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

      <section>
        <div className="mb-4 flex items-baseline justify-between gap-4">
          <h2 className="text-sm tracking-[0.25em] text-[#f5f5f5]">Errors</h2>
          <Link href={logsHref(projectId, { serviceId, level: 'ERROR' })} className="text-sm text-[#ff4d4f]">
            VIEW LOGS
          </Link>
        </div>
        {errors.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : errors.isError ? (
          <p className="text-sm text-[#ff4d4f]">{errors.error.message}</p>
        ) : (
          <ErrorList projectId={projectId} errors={errors.data ?? []} showService={false} />
        )}
      </section>
    </div>
  )
}
