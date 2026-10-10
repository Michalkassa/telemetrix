import { Client } from '@stomp/stompjs'
import type { ClockState } from './sync'

// The watch-together channel (docs/adr/0005): STOMP over WebSocket at /ws, same origin (Vite's proxy in
// development, nginx in Docker). Subscribing to /app/watch/{id} gets the current clock at once; then
// every change comes on /topic/watch/{id}. Only the host's commands (with its key) move the clock.

export type WatchCommand = { action: 'PLAY' | 'PAUSE' } | { action: 'SEEK' | 'SPEED'; value: number }

export interface WatchChannel {
  send(command: WatchCommand): void
  close(): void
}

export function openWatch(watchId: string, hostKey: string | null, onState: (state: ClockState) => void): WatchChannel {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const client = new Client({
    brokerURL: `${scheme}://${window.location.host}/ws`,
    reconnectDelay: 2_000,
    onConnect: () => {
      const receive = (message: { body: string }) => onState(JSON.parse(message.body) as ClockState)
      client.subscribe(`/topic/watch/${watchId}`, receive)
      client.subscribe(`/app/watch/${watchId}`, receive) // the current clock, now (also after a reconnect)
    },
  })
  client.activate()
  return {
    send(command) {
      if (hostKey && client.connected) {
        client.publish({
          destination: `/app/watch/${watchId}/command`,
          body: JSON.stringify({ hostKey, ...command }),
        })
      }
    },
    close() {
      void client.deactivate()
    },
  }
}

/** Follows one STOMP topic (e.g. /topic/races/39/timeline) until closed; reconnects by itself. */
export function openTopic(destination: string, onMessage: (body: unknown) => void): { close(): void } {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const client = new Client({
    brokerURL: `${scheme}://${window.location.host}/ws`,
    reconnectDelay: 5_000,
    onConnect: () => {
      client.subscribe(destination, (message) => onMessage(JSON.parse(message.body)))
    },
  })
  client.activate()
  return {
    close() {
      void client.deactivate()
    },
  }
}
