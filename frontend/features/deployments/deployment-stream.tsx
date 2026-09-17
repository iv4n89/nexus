'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useEventSource } from '@/hooks/use-event-source'
import { deploymentStreamUrl } from '@/hooks/event-source'
import { api } from '@/lib/api'
import { formatClock, healthOkLabel } from '@/lib/format'
import type { Deployment } from '@/types/api'

const MAX_LINES = 2000

export function DeploymentStream({
  projectId,
  deploymentId,
}: {
  projectId: string
  deploymentId: string
}) {
  const [lines, setLines] = useState<string[]>([])
  const scrollerRef = useRef<HTMLPreElement>(null)

  const deployment = useQuery({
    queryKey: ['projects', projectId, 'deployments', deploymentId],
    queryFn: () => api<Deployment>(`/api/projects/${projectId}/deployments/${deploymentId}`),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'PENDING' || status === 'RUNNING' ? 2000 : false
    },
  })

  const streamUrl = deploymentStreamUrl(deploymentId, deployment.data?.status)

  useEffect(() => {
    setLines([])
  }, [streamUrl])

  useEventSource(streamUrl, (data) => {
    const stamp = new Date().toLocaleTimeString('en-GB', { hour12: false })
    setLines((prev) => {
      const next = [...prev, `${stamp} ${data}`]
      return next.length > MAX_LINES ? next.slice(next.length - MAX_LINES) : next
    })
  })

  useEffect(() => {
    const node = scrollerRef.current
    if (node) {
      node.scrollTop = node.scrollHeight
    }
  }, [lines])

  return (
    <div className="flex flex-col gap-6">
      <Link href={`/projects/${projectId}`} className="text-sm text-[#888]">
        ← {projectId}
      </Link>
      <h1 className="text-xl uppercase tracking-wider">NEXUS / DEPLOY</h1>
      {deployment.isError ? (
        <p className="text-sm text-[#ff4d4f]">{deployment.error.message}</p>
      ) : (
        <dl className="grid max-w-sm grid-cols-[7rem_1fr] gap-y-1 font-mono text-sm">
          <dt className="text-[#888]">Status</dt>
          <dd>{deployment.data?.status ?? '…'}</dd>
          <dt className="text-[#888]">Started</dt>
          <dd>{formatClock(deployment.data?.startedAt ?? null)}</dd>
          <dt className="text-[#888]">Triggered</dt>
          <dd>{deployment.data?.triggeredBy ?? '—'}</dd>
          <dt className="text-[#888]">Health</dt>
          <dd>{healthOkLabel(deployment.data?.healthOk ?? null)}</dd>
        </dl>
      )}
      <pre
        ref={scrollerRef}
        className="h-[28rem] overflow-auto border border-[#2a2a2a] bg-black p-3 font-mono text-sm text-[#f5f5f5]"
      >
        {lines.map((line, index) => (
          <div key={index}>{line}</div>
        ))}
      </pre>
    </div>
  )
}
