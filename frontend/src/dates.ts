// Dates travel as ISO strings ("2026-11-10"), the same format the API uses.
// The app is India-only, so "today" is always today in Asia/Kolkata, like the backend's clock.

const ISO_IN_KOLKATA = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Kolkata',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

export function todayIst(): string {
  return ISO_IN_KOLKATA.format(new Date())
}

// Date arithmetic is done in UTC so a daylight-saving change on the viewer's machine can't shift it.
function parseIso(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
}

export function addDays(iso: string, days: number): string {
  const date = parseIso(iso)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

const DISPLAY = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'UTC',
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

/** "2026-11-10" -> "10 Nov 2026" */
export function formatDate(iso: string): string {
  return DISPLAY.format(parseIso(iso))
}
