'use client'

import Link from 'next/link'
import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useEventSource } from '@/hooks/use-event-source'
import { api } from '@/lib/api'
import { containersForProject, displayName, serviceLabel } from '@/lib/docker'
import { LOG_LEVELS, matchesLogLine, parseLogLevel, type LogLevel } from '@/lib/log-filter'
import type { Container, LogSnapshot } from '@/types/api'

const FROM_OPTIONS = [
  { label: 'all', seconds: 0 },
  { label: '15 min', seconds: 15 * 60 },
  { label: '30 min', seconds: 30 * 60 },
  { label: '1 hour', seconds: 60 * 60 },
  { label: '6 hours', seconds: 6 * 60 * 60 },
  { label: '24 hours', seconds: 24 * 60 * 60 },
] as const
const MAX_LINES = 2000
const DEFAULT_FROM_SECONDS = 0

function emptyLogsMessage(level: LogLevel, fromSeconds: number): string {
  const window =
    fromSeconds <= 0
      ? 'in the current container logs'
      : `in the last ${FROM_OPTIONS.find((option) => option.seconds === fromSeconds)?.label ?? 'window'}`
  if (level === 'ALL') {
    return `No log lines ${window}. After a deploy the container starts with a fresh log buffer.`
  }
  return `No ${level} lines ${window}. Try ALL, or a wider From — stored fingerprints can outlive the current container.`
}

export function LogViewer({ projectId }: { projectId: string }) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const containers = useQuery({
    queryKey: ['containers'],
    queryFn: () => api<Container[]>('/api/containers'),
  })
  const [level, setLevel] = useState<LogLevel>(() => parseLogLevel(searchParams.get('level')))
  const [search, setSearch] = useState(() => searchParams.get('q') ?? '')
  const [appliedSearch, setAppliedSearch] = useState(() => searchParams.get('q') ?? '')
  const [containerId, setContainerId] = useState<string | null>(searchParams.get('container'))
  const [fromSeconds, setFromSeconds] = useState(DEFAULT_FROM_SECONDS)
  const [lines, setLines] = useState<string[]>([])
  const scrollerRef = useRef<HTMLPreElement>(null)

  useEffect(() => {
    const handle = window.setTimeout(() => setAppliedSearch(search), 300)
    return () => window.clearTimeout(handle)
  }, [search])

  const ofProject = useMemo(
    () => (containers.data ? containersForProject(containers.data, projectId) : []),
    [containers.data, projectId],
  )

  useEffect(() => {
    if (containers.isPending) {
      return
    }
    if (ofProject.length === 0) {
      setContainerId(null)
      return
    }
    const wantedService = searchParams.get('service')
    const wantedContainer = searchParams.get('container')
    setContainerId((current) => {
      if (wantedContainer && ofProject.some((container) => container.id === wantedContainer)) {
        return wantedContainer
      }
      if (wantedService) {
        const match = ofProject.find((container) => serviceLabel(container) === wantedService)
        if (match) {
          return match.id
        }
      }
      if (current && ofProject.some((container) => container.id === current)) {
        return current
      }
      return ofProject[0].id
    })
  }, [ofProject, searchParams, containers.isPending])

  const selected = ofProject.find((container) => container.id === containerId)
  const selectedService = selected ? serviceLabel(selected) : null

  useEffect(() => {
    if (containers.isPending || (ofProject.length > 0 && !containerId)) {
      return
    }
    const params = new URLSearchParams()
    if (selectedService) {
      params.set('service', selectedService)
    } else if (containerId) {
      params.set('container', containerId)
    }
    if (level !== 'ALL') {
      params.set('level', level)
    }
    if (appliedSearch.trim()) {
      params.set('q', appliedSearch.trim())
    }
    const next = params.toString()
    const current = searchParams.toString()
    if (next !== current) {
      router.replace(next ? `${pathname}?${next}` : pathname, { scroll: false })
    }
  }, [
    appliedSearch,
    containerId,
    containers.isPending,
    level,
    ofProject.length,
    pathname,
    router,
    searchParams,
    selectedService,
  ])

  const since = useMemo(
    () => (fromSeconds > 0 ? Math.floor(Date.now() / 1000) - fromSeconds : undefined),
    [fromSeconds, containerId],
  )

  const snapshot = useQuery({
    queryKey: ['logs', 'search', containerId, since, level, appliedSearch],
    enabled: containerId != null,
    queryFn: () => {
      const params = new URLSearchParams({
        tail: String(MAX_LINES),
        timestamps: 'true',
      })
      if (since != null) {
        params.set('since', String(since))
      }
      if (level !== 'ALL') {
        params.set('level', level)
      }
      if (appliedSearch.trim()) {
        params.set('q', appliedSearch.trim())
      }
      return api<LogSnapshot>(
        `/api/containers/${encodeURIComponent(containerId!)}/logs/search?${params}`,
      )
    },
  })

  useEffect(() => {
    setLines(snapshot.data?.lines ?? [])
  }, [snapshot.data])

  const streamUrl =
    containerId == null || !snapshot.isSuccess
      ? null
      : `/api/containers/${encodeURIComponent(containerId)}/logs/stream?tail=1${
          since != null ? `&since=${since}` : ''
        }`

  useEventSource(streamUrl, (data) => {
    if (!matchesLogLine(data, level, appliedSearch)) {
      return
    }
    setLines((prev) => {
      if (prev[prev.length - 1] === data) {
        return prev
      }
      const next = [...prev, data]
      return next.length > MAX_LINES ? next.slice(next.length - MAX_LINES) : next
    })
  })

  useEffect(() => {
    const node = scrollerRef.current
    if (node) {
      node.scrollTop = node.scrollHeight
    }
  }, [lines])

  if (containers.isPending) {
    return <p className="text-sm text-[#888]">Loading…</p>
  }
  if (containers.isError) {
    return <p className="text-sm text-[#ff4d4f]">{containers.error.message}</p>
  }
  if (ofProject.length === 0) {
    return (
      <div className="flex flex-col gap-4">
        <Link href={`/projects/${projectId}`} className="text-sm text-[#888]">
          ← {projectId}
        </Link>
        <p className="text-sm text-[#888]">No containers</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <Link href={`/projects/${projectId}`} className="text-sm text-[#888]">
        ← {projectId}
      </Link>
      <h1 className="text-xl uppercase tracking-wider">Logs</h1>
      {selectedService ? (
        <p className="font-mono text-sm text-[#888]">{selectedService}</p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        {LOG_LEVELS.map((tab) => (
          <button
            key={tab}
            type="button"
            onClick={() => setLevel(tab)}
            className={`border px-3 py-1 text-sm ${
              level === tab
                ? 'border-[#f5f5f5] text-[#f5f5f5]'
                : 'border-[#2a2a2a] text-[#888]'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      <label className="flex items-center gap-3 text-sm">
        <span className="text-[#888]">Search:</span>
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          className="min-w-0 flex-1 border border-[#2a2a2a] bg-black px-2 py-1 text-[#f5f5f5]"
        />
      </label>

      <label className="flex items-center gap-3 text-sm">
        <span className="text-[#888]">Service:</span>
        <select
          value={containerId ?? ''}
          onChange={(event) => setContainerId(event.target.value)}
          className="border border-[#2a2a2a] bg-black px-2 py-1 text-[#f5f5f5]"
        >
          {ofProject.map((container) => (
            <option key={container.id} value={container.id}>
              {displayName(container)}
            </option>
          ))}
        </select>
      </label>

      <label className="flex items-center gap-3 text-sm">
        <span className="text-[#888]">From:</span>
        <select
          value={fromSeconds}
          onChange={(event) => setFromSeconds(Number(event.target.value))}
          className="border border-[#2a2a2a] bg-black px-2 py-1 text-[#f5f5f5]"
        >
          {FROM_OPTIONS.map((option) => (
            <option key={option.seconds} value={option.seconds}>
              {option.label}
            </option>
          ))}
        </select>
      </label>

      {snapshot.isError ? (
        <p className="text-sm text-[#ff4d4f]">{snapshot.error.message}</p>
      ) : null}

      <pre
        ref={scrollerRef}
        className="h-[32rem] overflow-auto border border-[#2a2a2a] bg-black p-3 font-mono text-sm leading-6 text-[#f5f5f5]"
      >
        {snapshot.isPending && lines.length === 0 ? (
          <div className="text-[#888]">Loading…</div>
        ) : lines.length === 0 ? (
          <div className="whitespace-pre-wrap text-[#888]">
            {emptyLogsMessage(level, fromSeconds)}
          </div>
        ) : (
          lines.map((line, index) => (
            <div
              key={index}
              className={
                /error|exception|fatal|failed|timeout/i.test(line)
                  ? 'whitespace-pre-wrap break-all text-[#ff4d4f]'
                  : 'whitespace-pre-wrap break-all'
              }
            >
              {line}
            </div>
          ))
        )}
      </pre>
    </div>
  )
}
