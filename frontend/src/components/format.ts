// "2024-03-02" -> "2 Mar 2024". Dates are calendar days, so they're formatted in UTC, never shifted.
export function formatDate(isoDate: string): string {
  return new Date(isoDate + 'T00:00:00Z').toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  })
}

// An instant in the viewer's own time zone, e.g. "Sat 2 Mar, 18:00"
export function formatTime(isoInstant: string): string {
  return new Date(isoInstant).toLocaleString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}
