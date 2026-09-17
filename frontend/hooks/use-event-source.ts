'use client'

import { useEffect, useRef } from 'react'
import { shouldAbandonEventSource } from '@/hooks/event-source'

export function useEventSource(
  url: string | null,
  onMessage: (data: string) => void,
  eventName = 'log',
) {
  const onMessageRef = useRef(onMessage)
  onMessageRef.current = onMessage

  useEffect(() => {
    if (url == null) {
      return
    }
    const source = new EventSource(url)
    let errorCount = 0
    const handler = (event: MessageEvent<string>) => {
      onMessageRef.current(event.data)
    }
    source.addEventListener(eventName, handler)
    source.onerror = () => {
      errorCount += 1
      if (shouldAbandonEventSource(source.readyState, errorCount)) {
        source.close()
      }
    }
    return () => {
      source.removeEventListener(eventName, handler)
      source.close()
    }
  }, [url, eventName])
}
