'use client'

import { useEffect, useRef } from 'react'

export function useEventSource(url: string | null, onMessage: (data: string) => void) {
  const onMessageRef = useRef(onMessage)
  onMessageRef.current = onMessage

  useEffect(() => {
    if (url == null) {
      return
    }
    const source = new EventSource(url)
    const handler = (event: MessageEvent<string>) => {
      onMessageRef.current(event.data)
    }
    source.addEventListener('log', handler)
    return () => {
      source.close()
    }
  }, [url])
}
