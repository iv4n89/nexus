'use client'

import Link from 'next/link'
import { useQuery } from '@tanstack/react-query'
import { StatusDot, toneFromHealthAndState } from '@/components/status-dot'
import { api } from '@/lib/api'
import { containersForProject, displayName, projectLabel, serviceLabel } from '@/lib/docker'
import { formatBytes, formatPercent } from '@/lib/format'
import type { Container, ProjectDetail, ProjectMetrics } from '@/types/api'

type ServiceRow = {
  key: string
  name: string
  state: string
  health: string | null
  href: string | null
}

function serviceRows(
  projectId: string,
  project: ProjectDetail,
  containers: Container[] | undefined,
): ServiceRow[] {
  const ofProject = containers ? containersForProject(containers, projectId) : []
  if (ofProject.length > 0) {
    return ofProject.map((container) => ({
      key: container.id,
      name: displayName(container),
      state: container.state,
      health: container.health,
      href: `/projects/${projectId}/services/${container.id}`,
    }))
  }

  return project.services.map((service) => {
    const match = (containers ?? []).find(
      (container) =>
        projectLabel(container) === projectId && serviceLabel(container) === service.id,
    )
    return {
      key: service.id,
      name: service.name,
      state: service.state,
      health: service.health,
      href: match ? `/projects/${projectId}/services/${match.id}` : null,
    }
  })
}

export function ProjectOverview({ projectId }: { projectId: string }) {
  const project = useQuery({
    queryKey: ['projects', projectId],
    queryFn: () => api<ProjectDetail>(`/api/projects/${projectId}`),
  })
  const metrics = useQuery({
    queryKey: ['projects', projectId, 'metrics'],
    queryFn: () => api<ProjectMetrics>(`/api/projects/${projectId}/metrics`),
  })
  const containers = useQuery({
    queryKey: ['containers'],
    queryFn: () => api<Container[]>('/api/containers'),
  })

  if (project.isPending) {
    return <p className="text-sm text-[#888]">Loading…</p>
  }
  if (project.isError) {
    return <p className="text-sm text-[#ff4d4f]">{project.error.message}</p>
  }

  const rows = serviceRows(projectId, project.data, containers.data)

  return (
    <div className="flex flex-col gap-10">
      <section className="flex flex-col gap-4">
        <h1 className="text-xl uppercase tracking-wider">{project.data.name}</h1>
        <p className="text-sm">
          <span className="text-[#888]">Status: </span>
          {project.data.status}
        </p>
        <div className="flex gap-3">
          <button
            type="button"
            disabled
            className="cursor-not-allowed border border-[#2a2a2a] px-4 py-2 text-sm text-[#888]"
          >
            DEPLOY
          </button>
          <button
            type="button"
            disabled
            className="cursor-not-allowed border border-[#2a2a2a] px-4 py-2 text-sm text-[#888]"
          >
            ROLLBACK
          </button>
        </div>
      </section>

      <hr className="border-[#2a2a2a]" />

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">Services</h2>
        {containers.isPending && rows.length === 0 ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : rows.length === 0 ? (
          <p className="text-sm text-[#888]">No services</p>
        ) : (
          <ul>
            {rows.map((row) => {
              const content = (
                <>
                  <span>{row.name}</span>
                  <span className="flex items-center gap-3 font-mono text-sm">
                    <StatusDot
                      tone={toneFromHealthAndState(row.health, row.state)}
                      label={row.health ?? row.state}
                    />
                    <span>{row.state}</span>
                  </span>
                </>
              )
              return (
                <li key={row.key} className="border-b border-[#2a2a2a] last:border-b-0">
                  {row.href ? (
                    <Link href={row.href} className="flex items-center justify-between py-3">
                      {content}
                    </Link>
                  ) : (
                    <div className="flex items-center justify-between py-3">{content}</div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <section>
        {metrics.isPending ? (
          <p className="text-sm text-[#888]">Loading metrics…</p>
        ) : metrics.isError ? (
          <p className="text-sm text-[#ff4d4f]">{metrics.error.message}</p>
        ) : (
          <dl className="grid max-w-xs grid-cols-[6rem_1fr] gap-y-2 font-mono text-sm">
            <dt className="text-[#888]">CPU</dt>
            <dd>{formatPercent(metrics.data.cpuPercent)}</dd>
            <dt className="text-[#888]">Memory</dt>
            <dd>{formatBytes(metrics.data.memoryUsedBytes)}</dd>
            <dt className="text-[#888]">Restarts</dt>
            <dd>{metrics.data.restartCount}</dd>
          </dl>
        )}
      </section>
    </div>
  )
}
