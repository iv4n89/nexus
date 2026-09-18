'use client'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { StatusDot, toneFromHealthAndState } from '@/components/status-dot'
import { alertsPath } from '@/features/alerts/api'
import { me } from '@/features/auth/api'
import { RollbackDialog } from '@/features/deployments/rollback-dialog'
import { ErrorList, logsHref } from '@/features/logs/error-list'
import { ProjectDomainsSection } from '@/features/projects/project-domains-section'
import { ProjectEnvSection } from '@/features/projects/project-env-section'
import { TrafficChart } from '@/features/traffic/traffic-chart'
import { api } from '@/lib/api'
import { containersForProject, displayName, projectLabel, serviceLabel } from '@/lib/docker'
import { formatBytes, formatClock, formatElapsed, formatPercent, healthOkLabel } from '@/lib/format'
import type {
  Alert,
  AuthUser,
  Container,
  DeployAccepted,
  Deployment,
  IncidentTimelineItem,
  ProjectDetail,
  ProjectHealth,
  ProjectMetrics,
  ProjectTraffic,
  RecentError,
} from '@/types/api'

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
  const router = useRouter()
  const [deploying, setDeploying] = useState(false)
  const [deployError, setDeployError] = useState<string | null>(null)
  const [rollbackOpen, setRollbackOpen] = useState(false)
  const [rollingBack, setRollingBack] = useState(false)
  const [rollbackError, setRollbackError] = useState<string | null>(null)

  const project = useQuery({
    queryKey: ['projects', projectId],
    queryFn: () => api<ProjectDetail>(`/api/projects/${projectId}`),
  })
  const auth = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: (): Promise<AuthUser> => me(),
  })
  const history = useQuery({
    queryKey: ['projects', projectId, 'deployments'],
    queryFn: () => api<Deployment[]>(`/api/projects/${projectId}/deployments`),
  })
  const metrics = useQuery({
    queryKey: ['projects', projectId, 'metrics'],
    queryFn: () => api<ProjectMetrics>(`/api/projects/${projectId}/metrics`),
  })
  const containers = useQuery({
    queryKey: ['containers'],
    queryFn: () => api<Container[]>('/api/containers'),
  })
  const queryClient = useQueryClient()
  const errors = useQuery({
    queryKey: ['projects', projectId, 'errors'],
    queryFn: () => api<RecentError[]>(`/api/projects/${projectId}/errors`),
    refetchInterval: 30_000,
  })
  const alerts = useQuery({
    queryKey: ['alerts', 'ACTIVE'],
    queryFn: () => api<Alert[]>(alertsPath('ACTIVE')),
    refetchInterval: 30_000,
  })
  const timeline = useQuery({
    queryKey: ['projects', projectId, 'timeline'],
    queryFn: () => api<IncidentTimelineItem[]>(`/api/projects/${projectId}/timeline?limit=30`),
    refetchInterval: 30_000,
  })
  const health = useQuery({
    queryKey: ['projects', projectId, 'health'],
    queryFn: () => api<ProjectHealth>(`/api/projects/${projectId}/health`),
    refetchInterval: 30_000,
  })
  const traffic = useQuery({
    queryKey: ['projects', projectId, 'traffic'],
    queryFn: () => api<ProjectTraffic>(`/api/projects/${projectId}/traffic?hours=24`),
  })
  const acknowledge = useMutation({
    mutationFn: (id: string) => api<Alert>(`/api/alerts/${id}/acknowledge`, { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['alerts'] })
    },
  })

  if (project.isPending) {
    return <p className="text-sm text-[#888]">Loading…</p>
  }
  if (project.isError) {
    return <p className="text-sm text-[#ff4d4f]">{project.error.message}</p>
  }

  const rows = serviceRows(projectId, project.data, containers.data)
  const canDeploy = auth.data?.role === 'ADMIN' && project.data.deployable
  const canRollback = canDeploy
  const canAcknowledge = auth.data?.role === 'ADMIN'
  const canEditDomains = auth.data?.role === 'ADMIN'
  const canEditEnv = auth.data?.role === 'ADMIN'
  const recentErrors = errors.data ?? project.data.recentErrors ?? []
  const projectAlerts = (alerts.data ?? []).filter((alert) => alert.projectId === projectId)
  const currentDeployment = history.data?.[0]
  const previousDeployment = history.data?.[1]

  async function onDeploy() {
    if (!canDeploy || deploying) {
      return
    }
    if (!window.confirm('Deploy this project?')) {
      return
    }
    setDeployError(null)
    setDeploying(true)
    try {
      const accepted = await api<DeployAccepted>(`/api/projects/${projectId}/deploy`, {
        method: 'POST',
      })
      router.push(`/projects/${projectId}/deployments/${accepted.id}`)
    } catch (error) {
      setDeploying(false)
      setDeployError(error instanceof Error ? error.message : 'DEPLOY_FAILED')
    }
  }

  async function onRollbackConfirm() {
    if (!canRollback || rollingBack) {
      return
    }
    setRollbackError(null)
    setRollingBack(true)
    try {
      const accepted = await api<DeployAccepted>(`/api/projects/${projectId}/rollback`, {
        method: 'POST',
      })
      setRollbackOpen(false)
      router.push(`/projects/${projectId}/deployments/${accepted.id}`)
    } catch (error) {
      setRollingBack(false)
      setRollbackError(error instanceof Error ? error.message : 'ROLLBACK_FAILED')
    }
  }

  return (
    <div className="flex flex-col gap-10">
      <section className="flex flex-col gap-4">
        <h1 className="text-xl uppercase tracking-wider">{project.data.name}</h1>
        <p className="text-sm">
          <span className="text-[#888]">Status: </span>
          {project.data.status}
        </p>
        <Link href={logsHref(projectId, { level: 'ERROR' })} className="text-sm">
          LOGS
        </Link>
        <Link href={`/projects/${projectId}/database`} className="text-sm">
          DATABASE
        </Link>
        <div className="flex gap-3">
          <button
            type="button"
            disabled={!canDeploy || deploying}
            onClick={() => void onDeploy()}
            className={
              canDeploy && !deploying
                ? 'border border-[#f5f5f5] px-4 py-2 text-sm text-[#f5f5f5]'
                : 'cursor-not-allowed border border-[#2a2a2a] px-4 py-2 text-sm text-[#888]'
            }
          >
            DEPLOY
          </button>
          <button
            type="button"
            disabled={!canRollback || rollingBack}
            onClick={() => {
              if (!canRollback || rollingBack) {
                return
              }
              setRollbackError(null)
              setRollbackOpen(true)
            }}
            className={
              canRollback && !rollingBack
                ? 'border border-[#f5f5f5] px-4 py-2 text-sm text-[#f5f5f5]'
                : 'cursor-not-allowed border border-[#2a2a2a] px-4 py-2 text-sm text-[#888]'
            }
          >
            ROLLBACK
          </button>
        </div>
        {deployError ? <p className="text-sm text-[#ff4d4f]">{deployError}</p> : null}
        <RollbackDialog
          open={rollbackOpen}
          currentId={currentDeployment?.id}
          previousId={previousDeployment?.id}
          busy={rollingBack}
          error={rollbackError}
          onCancel={() => {
            if (rollingBack) {
              return
            }
            setRollbackOpen(false)
            setRollbackError(null)
          }}
          onConfirm={() => void onRollbackConfirm()}
        />
      </section>

      <hr className="border-[#2a2a2a]" />

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">Health</h2>
        {health.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : health.isError ? (
          <p className="text-sm text-[#ff4d4f]">{health.error.message}</p>
        ) : health.data ? (
          <dl className="grid max-w-md grid-cols-[9rem_1fr] gap-y-2 font-mono text-sm">
            <dt className="text-[#888]">Containers</dt>
            <dd>
              {health.data.containersRunning}/{health.data.containersTotal} running
            </dd>
            <dt className="text-[#888]">Deployment</dt>
            <dd>
              {health.data.lastDeploymentStatus ?? '—'}
              {health.data.lastDeploymentAt
                ? ` · ${formatClock(health.data.lastDeploymentAt)}`
                : ''}
            </dd>
            <dt className="text-[#888]">Alerts</dt>
            <dd>{health.data.openAlertsCount} open</dd>
            {health.data.openSecurityFindingsCount != null ? (
              <>
                <dt className="text-[#888]">Security</dt>
                <dd>{health.data.openSecurityFindingsCount} open</dd>
              </>
            ) : null}
            {health.data.backupsAvailable ? (
              <>
                <dt className="text-[#888]">Backup</dt>
                <dd>
                  {health.data.lastBackupSuccessAt
                    ? formatClock(health.data.lastBackupSuccessAt)
                    : '—'}
                </dd>
              </>
            ) : null}
            {health.data.domainsCount != null ? (
              <>
                <dt className="text-[#888]">Domains</dt>
                <dd>{health.data.domainsCount}</dd>
              </>
            ) : null}
          </dl>
        ) : (
          <p className="text-sm text-[#888]">No health data</p>
        )}
      </section>

      <hr className="border-[#2a2a2a]" />

      <section>
        <div className="mb-4 flex items-baseline justify-between gap-4">
          <h2 className="text-xs tracking-[0.25em] text-[#888]">TRAFFIC</h2>
          <Link href="/traffic" className="text-sm">
            Full traffic
          </Link>
        </div>
        {traffic.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : traffic.isError ? (
          <p className="text-sm text-[#ff4d4f]">{traffic.error.message}</p>
        ) : traffic.data.requests === 0 && traffic.data.series.length === 0 ? (
          <p className="text-sm text-[#888]">No traffic yet</p>
        ) : (
          <div className="flex flex-col gap-4">
            <TrafficChart
              series={traffic.data.series}
              field="requests"
              stroke="#f5f5f5"
              from={traffic.data.from}
              to={traffic.data.to}
            />
            <p className="font-mono text-sm">
              <span className="text-[#888]">5xx </span>
              <span className={traffic.data.status5xx > 0 ? 'text-[#ff4d4f]' : undefined}>
                {traffic.data.status5xx}
              </span>
            </p>
            {traffic.data.services.length > 0 ? (
              <ul>
                {traffic.data.services.map((service) => (
                  <li
                    key={service.serviceId}
                    className="flex items-center justify-between border-b border-[#2a2a2a] py-2 font-mono text-sm last:border-b-0"
                  >
                    <span className="min-w-0 break-all">{service.serviceId}</span>
                    <span className="flex shrink-0 gap-4">
                      <span>{service.requests}</span>
                      <span className={service.status5xx > 0 ? 'text-[#ff4d4f]' : 'text-[#888]'}>
                        {service.status5xx}
                      </span>
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        )}
      </section>

      <ProjectEnvSection projectId={projectId} canEdit={canEditEnv} />

      <ProjectDomainsSection projectId={projectId} canEdit={canEditDomains} />

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
        <div className="mb-4 flex items-baseline justify-between gap-4">
          <h2 className="text-sm tracking-[0.25em] text-[#f5f5f5]">Errors</h2>
          <Link href={logsHref(projectId, { level: 'ERROR' })} className="text-sm text-[#ff4d4f]">
            VIEW LOGS
          </Link>
        </div>
        {errors.isPending && !errors.data ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : errors.isError ? (
          <p className="text-sm text-[#ff4d4f]">{errors.error.message}</p>
        ) : (
          <ErrorList projectId={projectId} errors={recentErrors} />
        )}
      </section>

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">Alerts</h2>
        {alerts.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : alerts.isError ? (
          <p className="text-sm text-[#ff4d4f]">{alerts.error.message}</p>
        ) : projectAlerts.length === 0 ? (
          <p className="text-sm text-[#888]">No active alerts</p>
        ) : (
          <ul>
            {projectAlerts.map((alert) => (
              <li
                key={alert.id}
                className="flex items-start justify-between gap-4 border-b border-[#2a2a2a] py-3 last:border-b-0"
              >
                <div className="min-w-0">
                  <p className="text-sm">{alert.type}</p>
                  <p className="mt-1 break-all text-sm text-[#888]">{alert.message}</p>
                  <p className="mt-1 text-sm text-[#888]">
                    {alert.status}
                    {alert.serviceId ? ` · ${alert.serviceId}` : ''}
                    {' · '}
                    {formatClock(alert.openedAt)}
                  </p>
                </div>
                {canAcknowledge && alert.status === 'ACTIVE' ? (
                  <button
                    type="button"
                    disabled={acknowledge.isPending}
                    onClick={() => acknowledge.mutate(alert.id)}
                    className="shrink-0 border border-[#f5f5f5] px-3 py-1 text-sm text-[#f5f5f5] disabled:cursor-not-allowed disabled:border-[#2a2a2a] disabled:text-[#888]"
                  >
                    ACK
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        {acknowledge.isError ? (
          <p className="mt-2 text-sm text-[#ff4d4f]">{acknowledge.error.message}</p>
        ) : null}
      </section>

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">Timeline</h2>
        {timeline.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : timeline.isError ? (
          <p className="text-sm text-[#ff4d4f]">{timeline.error.message}</p>
        ) : (timeline.data ?? []).length === 0 ? (
          <p className="text-sm text-[#888]">No recent events</p>
        ) : (
          <ul>
            {(timeline.data ?? []).map((item) => (
              <li
                key={`${item.source}-${item.refId}`}
                className="border-b border-[#2a2a2a] py-3 last:border-b-0"
              >
                <p className="font-mono text-sm text-[#888]">{formatClock(item.at)}</p>
                <p className="mt-1 text-sm">
                  <span className="text-[#888]">{item.source}</span>
                  {' · '}
                  {item.kind}
                </p>
                <p className="mt-1 break-all text-sm text-[#888]">
                  {item.message}
                  {item.serviceId ? ` · ${item.serviceId}` : ''}
                </p>
              </li>
            ))}
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

      <hr className="border-[#2a2a2a]" />

      <section>
        <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">History</h2>
        {history.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : history.isError ? (
          <p className="text-sm text-[#ff4d4f]">{history.error.message}</p>
        ) : history.data.length === 0 ? (
          <p className="text-sm text-[#888]">No deployments</p>
        ) : (
          <ul>
            {history.data.map((deployment) => {
              const duration = formatElapsed(deployment.startedAt, deployment.finishedAt)
              return (
                <li key={deployment.id} className="border-b border-[#2a2a2a] last:border-b-0">
                  <Link
                    href={`/projects/${projectId}/deployments/${deployment.id}`}
                    className="flex flex-col gap-1 py-3 font-mono text-sm"
                  >
                    <span className="flex items-center justify-between">
                      <span>{deployment.status}</span>
                      <span className="text-[#888]">{deployment.id.slice(0, 8)}</span>
                    </span>
                    <span className="text-[#888]">
                      Started {formatClock(deployment.startedAt)}
                      {duration ? ` · Duration ${duration}` : ''}
                    </span>
                    <span className="text-[#888]">Triggered by {deployment.triggeredBy}</span>
                    <span className="text-[#888]">Health {healthOkLabel(deployment.healthOk)}</span>
                  </Link>
                </li>
              )
            })}
          </ul>
        )}
      </section>
    </div>
  )
}
