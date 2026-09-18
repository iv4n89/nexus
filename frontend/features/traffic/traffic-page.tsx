'use client'

import Link from 'next/link'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { alertsPath } from '@/features/alerts/api'
import { TrafficChart } from '@/features/traffic/traffic-chart'
import { api } from '@/lib/api'
import { formatBytes } from '@/lib/format'
import type { Alert, TrafficOverview } from '@/types/api'

function formatMs(value: number | null): string {
  if (value == null) {
    return '—'
  }
  const rounded = Number(value.toFixed(1))
  return `${rounded} ms`
}

export function TrafficPage() {
  const [hours, setHours] = useState(24)
  const traffic = useQuery({
    queryKey: ['traffic', hours],
    queryFn: () => api<TrafficOverview>(`/api/traffic?hours=${hours}`),
  })
  const alerts = useQuery({
    queryKey: ['alerts', 'ACTIVE'],
    queryFn: () => api<Alert[]>(alertsPath('ACTIVE')),
    refetchInterval: 30_000,
  })

  const trafficAlerts = (alerts.data ?? []).filter((alert) => alert.type.startsWith('TRAFFIC_'))

  return (
    <div className="flex flex-col gap-10">
      <section>
        <div className="mb-4 flex items-baseline justify-between gap-4">
          <h2 className="text-xs tracking-[0.25em] text-[#888]">TRAFFIC</h2>
          <div className="flex gap-4 text-sm">
            <button
              type="button"
              onClick={() => setHours(24)}
              className={hours === 24 ? 'text-[#f5f5f5]' : 'text-[#888]'}
            >
              24h
            </button>
            <button
              type="button"
              onClick={() => setHours(168)}
              className={hours === 168 ? 'text-[#f5f5f5]' : 'text-[#888]'}
            >
              7d
            </button>
          </div>
        </div>
        {traffic.isPending ? (
          <p className="text-sm text-[#888]">Loading…</p>
        ) : traffic.isError ? (
          <p className="text-sm text-[#ff4d4f]">{traffic.error.message}</p>
        ) : traffic.data.requests === 0 && traffic.data.series.length === 0 ? (
          <p className="text-sm text-[#888]">No traffic yet</p>
        ) : (
          <div className="flex flex-col gap-10">
            <div>
              <h3 className="mb-3 text-xs tracking-[0.25em] text-[#888]">ALERTS</h3>
              {alerts.isPending ? (
                <p className="text-sm text-[#888]">Loading…</p>
              ) : alerts.isError ? (
                <p className="text-sm text-[#ff4d4f]">{alerts.error.message}</p>
              ) : trafficAlerts.length === 0 ? (
                <p className="text-sm">0 traffic alerts</p>
              ) : (
                <ul>
                  {trafficAlerts.map((alert) => {
                    const label = [alert.type, alert.message].filter(Boolean).join(' — ')
                    return (
                      <li
                        key={alert.id}
                        className="border-b border-[#2a2a2a] py-2 text-sm text-[#ff4d4f] last:border-b-0"
                      >
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
              )}
            </div>

            <dl className="grid max-w-xs grid-cols-[6.5rem_1fr] gap-y-2 font-mono text-sm">
              <dt className="text-[#888]">Requests</dt>
              <dd>{traffic.data.requests}</dd>
              <dt className="text-[#888]">Bytes</dt>
              <dd>{formatBytes(traffic.data.bytesOut)}</dd>
              <dt className="text-[#888]">2xx</dt>
              <dd>{traffic.data.status2xx}</dd>
              <dt className="text-[#888]">4xx</dt>
              <dd>{traffic.data.status4xx}</dd>
              <dt className="text-[#888]">5xx</dt>
              <dd className={traffic.data.status5xx > 0 ? 'text-[#ff4d4f]' : undefined}>
                {traffic.data.status5xx}
              </dd>
              <dt className="text-[#888]">Avg</dt>
              <dd>{formatMs(traffic.data.latencyAvgMs)}</dd>
              <dt className="text-[#888]">Max</dt>
              <dd>{formatMs(traffic.data.latencyMaxMs)}</dd>
            </dl>

            <div>
              <h3 className="mb-3 text-xs tracking-[0.25em] text-[#888]">REQUESTS</h3>
              <TrafficChart
                series={traffic.data.series}
                field="requests"
                stroke="#f5f5f5"
                from={traffic.data.from}
                to={traffic.data.to}
              />
            </div>

            <div>
              <h3 className="mb-3 text-xs tracking-[0.25em] text-[#888]">5XX</h3>
              <TrafficChart
                series={traffic.data.series}
                field="status5xx"
                stroke="#ff4d4f"
                from={traffic.data.from}
                to={traffic.data.to}
              />
            </div>
          </div>
        )}
      </section>

      {traffic.isSuccess && !(traffic.data.requests === 0 && traffic.data.series.length === 0) ? (
        <>
          <hr className="border-[#2a2a2a]" />
          <section>
            <h2 className="mb-4 text-xs tracking-[0.25em] text-[#888]">PROJECTS</h2>
            {traffic.data.projects.length === 0 ? (
              <p className="text-sm text-[#888]">No traffic yet</p>
            ) : (
              <ul>
                {traffic.data.projects.map((project) => (
                  <li key={project.projectId} className="border-b border-[#2a2a2a] last:border-b-0">
                    <Link
                      href={`/projects/${project.projectId}`}
                      className="flex items-center justify-between py-3"
                    >
                      <span className="min-w-0 break-all">{project.projectId}</span>
                      <span className="flex shrink-0 gap-4 font-mono text-sm">
                        <span>{project.requests}</span>
                        <span className={project.status5xx > 0 ? 'text-[#ff4d4f]' : 'text-[#888]'}>
                          {project.status5xx}
                        </span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      ) : null}
    </div>
  )
}
