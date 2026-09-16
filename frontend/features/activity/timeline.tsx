'use client'

import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useEventSource } from '@/hooks/use-event-source'
import { api } from '@/lib/api'
import { formatClock } from '@/lib/format'
import type { ActivityEvent } from '@/types/api'

export function formatActivityLine(event: ActivityEvent): string {
  const who = [event.projectId, event.serviceId].filter(Boolean).join('-')
  const text = who ? `${who} ${event.message}` : event.message
  return `${formatClock(event.createdAt)}  ${text}`
}

export function ActivityTimeline({ events }: { events: ActivityEvent[] }) {
  if (events.length === 0) {
    return <p className="text-sm text-[#888]">No recent activity</p>
  }
  return (
    <pre className="overflow-auto border border-[#2a2a2a] bg-black p-3 font-mono text-sm text-[#f5f5f5]">
      {events.map((event) => (
        <div key={event.id}>{formatActivityLine(event)}</div>
      ))}
    </pre>
  )
}

export function useActivityEvents(limit: number) {
  const [events, setEvents] = useState<ActivityEvent[]>([])
  const query = useQuery({
    queryKey: ['activity', limit],
    queryFn: () => api<ActivityEvent[]>(`/api/activity?limit=${limit}`),
  })

  useEffect(() => {
    if (!query.data) {
      return
    }
    setEvents((prev) => {
      const seen = new Set(query.data.map((event) => event.id))
      const live = prev.filter((event) => !seen.has(event.id))
      return [...live, ...query.data].slice(0, limit)
    })
  }, [query.data, limit])

  useEventSource(
    '/api/events/stream',
    (data) => {
      try {
        const event = JSON.parse(data) as ActivityEvent
        setEvents((prev) => {
          if (prev.some((item) => item.id === event.id)) {
            return prev
          }
          return [event, ...prev].slice(0, limit)
        })
      } catch {
        return
      }
    },
    'activity',
  )

  return {
    events,
    isPending: query.isPending,
    isError: query.isError,
    error: query.error,
  }
}

export function ActivityFeed() {
  const { events, isPending, isError, error } = useActivityEvents(50)

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl uppercase tracking-wider">NEXUS / LIVE</h1>
      {isPending ? (
        <p className="text-sm text-[#888]">Loading…</p>
      ) : isError ? (
        <p className="text-sm text-[#ff4d4f]">{error?.message ?? 'Unable to load activity'}</p>
      ) : (
        <ActivityTimeline events={events} />
      )}
    </div>
  )
}
