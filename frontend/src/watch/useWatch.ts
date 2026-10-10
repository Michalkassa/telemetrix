import { useEffect, useMemo, useRef } from 'react'
import type { Watching } from '../replay/ReplayPlayer'
import { openWatch } from './channel'
import { ClockSync } from './sync'

// Joins a watch session: the shared clock, kept in sync over STOMP while the page is open.
export function useWatch(watchId: string | null): Watching | undefined {
  const sync = useMemo(() => (watchId ? new ClockSync() : null), [watchId])
  const channel = useRef<ReturnType<typeof openWatch> | null>(null)
  const hostKey = watchId ? loadHostKey(watchId) : null

  useEffect(() => {
    if (!watchId || !sync) return
    const opened = openWatch(watchId, hostKey, (state) => sync.observe(state, Date.now()))
    channel.current = opened
    return () => {
      opened.close()
      channel.current = null
    }
  }, [watchId, hostKey, sync])

  if (!watchId || !sync) return undefined
  return { role: hostKey ? 'host' : 'viewer', sync, send: (command) => channel.current?.send(command) }
}

// the host key stays in this tab only, so other tabs with the link are viewers
export function saveHostKey(watchId: string, hostKey: string) {
  try {
    sessionStorage.setItem(`telemetrix.watch.${watchId}`, hostKey)
  } catch {
    // storage unavailable: this tab can't host after a reload
  }
}

function loadHostKey(watchId: string): string | null {
  try {
    return sessionStorage.getItem(`telemetrix.watch.${watchId}`)
  } catch {
    return null
  }
}
