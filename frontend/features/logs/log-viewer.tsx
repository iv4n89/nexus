'use client'

import Link from 'next/link'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useEventSource } from '@/hooks/use-event-source'
import { api } from '@/lib/api'
import { containersForProject, displayName } from '@/lib/docker'
import type { Container } from '@/types/api'

const LEVELS = ['ALL', 'INFO', 'WARN', 'ERROR'] as const
const FROM_OPTIONS = [
  { label: '15 min', seconds: 15 * 60 },
  { label: '30 min', seconds: 30 * 60 },
  { label: '1 hour', seconds: 60 * 60 },
] as const
const MAX_LINES = 2000
const DEFAULT_FROM_SECONDS = 30 * 60

type Level = (typeof LEVELS)[number]

export function LogViewer({ projectId }: { projectId: string }) {
  const containers = useQuery({
    queryKey: ['containers'],
    queryFn: () => api<Container[]>('/api/containers'),
  })
  const [level, setLevel] = useState<Level>('ALL')
  const [search, setSearch] = useState('')
  const [containerId, setContainerId] = useState<string | null>(null)
  const [fromSeconds, setFromSeconds] = useState(DEFAULT_FROM_SECONDS)
  const [lines, setLines] = useState<string[]>([])
  const scrollerRef = useRef<HTMLPreElement>(null)

  const ofProject = useMemo(
    () => (containers.data ? containersForProject(containers.data, projectId) : []),
    [containers.data, projectId],
  )

  useEffect(() => {
    if (ofProject.length === 0) {
      setContainerId(null)
      return
    }
    setContainerId((current) => {
      if (current && ofProject.some((container) => container.id === current)) {
        return current
      }
      return ofProject[0].id
    })
  }, [ofProject])

  const since = useMemo(
    () => Math.floor(Date.now() / 1000) - fromSeconds,
    [fromSeconds, containerId],
  )

  const streamUrl =
    containerId == null
      ? null
      : `/api/containers/${encodeURIComponent(containerId)}/logs/stream?tail=100&since=${since}`

  useEffect(() => {
    setLines([])
  }, [streamUrl])

  useEventSource(streamUrl, (data) => {
    setLines((prev) => {
      const next = [...prev, data]
      return next.length > MAX_LINES ? next.slice(next.length - MAX_LINES) : next
    })
  })

  const visibleLines = useMemo(() => {
    const query = search.trim().toLowerCase()
    const levelNeedle = level === 'ALL' ? null : level.toLowerCase()
    return lines.filter((line) => {
      const hay = line.toLowerCase()
      if (levelNeedle && !hay.includes(levelNeedle)) {
        return false
      }
      if (query && !hay.includes(query)) {
        return false
      }
      return true
    })
  }, [lines, level, search])

  useEffect(() => {
    const node = scrollerRef.current
    if (node) {
      node.scrollTop = node.scrollHeight
    }
  }, [visibleLines])

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

      <div className="flex flex-wrap gap-2">
        {LEVELS.map((tab) => (
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
          className="border border-[#2a2a2a] bg-black px-2 py-1 text-[#f5f5f5]"
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

      <pre
        ref={scrollerRef}
        className="h-[28rem] overflow-auto border border-[#2a2a2a] bg-black p-3 font-mono text-sm text-[#f5f5f5]"
      >
        {visibleLines.map((line, index) => (
          <div key={index}>{line}</div>
        ))}
      </pre>
    </div>
  )
}
