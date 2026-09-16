'use client'

import Link from 'next/link'
import { useMemo, useState } from 'react'
import { formatClock } from '@/lib/format'
import type { RecentError } from '@/types/api'

export function logsHref(
  projectId: string,
  options: { serviceId?: string | null; query?: string | null; level?: string } = {},
): string {
  const params = new URLSearchParams()
  if (options.serviceId) {
    params.set('service', options.serviceId)
  }
  params.set('level', options.level ?? 'ERROR')
  if (options.query) {
    params.set('q', options.query)
  }
  const query = params.toString()
  return `/projects/${projectId}/logs${query ? `?${query}` : ''}`
}

export function ErrorList({
  projectId,
  errors,
  showService = true,
}: {
  projectId: string
  errors: RecentError[]
  showService?: boolean
}) {
  const services = useMemo(() => {
    const ids = [...new Set(errors.map((error) => error.serviceId).filter(Boolean))]
    ids.sort()
    return ids
  }, [errors])
  const [serviceFilter, setServiceFilter] = useState<string>('ALL')
  const visible =
    !showService || serviceFilter === 'ALL'
      ? errors
      : errors.filter((error) => error.serviceId === serviceFilter)

  if (errors.length === 0) {
    return <p className="text-sm text-[#888]">No recent errors</p>
  }

  return (
    <div className="flex flex-col gap-4">
      {showService && services.length > 1 ? (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setServiceFilter('ALL')}
            className={`border px-3 py-1 text-sm ${
              serviceFilter === 'ALL' ? 'border-[#f5f5f5] text-[#f5f5f5]' : 'border-[#2a2a2a] text-[#888]'
            }`}
          >
            ALL
          </button>
          {services.map((service) => (
            <button
              key={service}
              type="button"
              onClick={() => setServiceFilter(service)}
              className={`border px-3 py-1 font-mono text-sm ${
                serviceFilter === service
                  ? 'border-[#ff4d4f] text-[#ff4d4f]'
                  : 'border-[#2a2a2a] text-[#888]'
              }`}
            >
              {service}
            </button>
          ))}
        </div>
      ) : null}

      {visible.length === 0 ? (
        <p className="text-sm text-[#888]">No errors for this service</p>
      ) : (
        <ul>
          {visible.map((error) => (
            <li
              key={`${error.serviceId}-${error.sampleMessage}-${error.lastSeen}`}
              className="border-b border-[#2a2a2a] last:border-b-0"
            >
              <Link
                href={logsHref(projectId, {
                  serviceId: error.serviceId,
                  query: error.sampleMessage.slice(0, 80),
                })}
                className="flex flex-col gap-2 py-4"
              >
                <div className="flex items-baseline justify-between gap-4">
                  <span className="text-sm uppercase tracking-wider text-[#ff4d4f]">
                    {showService && error.serviceId ? error.serviceId : 'error'}
                  </span>
                  <span className="shrink-0 font-mono text-sm text-[#ff4d4f]">×{error.count}</span>
                </div>
                <p className="whitespace-pre-wrap break-all font-mono text-sm text-[#f5f5f5]">
                  {error.sampleMessage}
                </p>
                <p className="font-mono text-xs text-[#888]">
                  last {formatClock(error.lastSeen)}
                  {error.firstSeen ? ` · first ${formatClock(error.firstSeen)}` : ''}
                </p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
