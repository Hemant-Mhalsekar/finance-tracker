import { useEffect, useState } from 'react'

type Health = { status?: string; db: string; time?: string }
type Phase = 'loading' | 'waking' | 'done' | 'failed'

const RETRY_DELAY_MS = 5_000
const REQUEST_TIMEOUT_MS = 15_000
const MAX_TOTAL_MS = 90_000

export default function App() {
  const [phase, setPhase] = useState<Phase>('loading')
  const [health, setHealth] = useState<Health | null>(null)
  // Incrementing this restarts the whole check cycle (used by the Retry button).
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const startedAt = Date.now()
    let stopped = false
    let retryTimer: number | undefined
    let requestTimer: number | undefined
    let controller: AbortController | undefined

    async function check() {
      controller = new AbortController()
      requestTimer = window.setTimeout(() => controller?.abort(), REQUEST_TIMEOUT_MS)

      try {
        const res = await fetch('/api/health', { signal: controller.signal })
        // A proxy error page (502/503/504) is not JSON, so this throws and we fall through to retry.
        const body: unknown = await res.json()
        if (typeof body === 'object' && body !== null && 'db' in body) {
          // Real answer from the backend (even HTTP 503 with db "down"): show it, no retry.
          if (!stopped) {
            setHealth(body as Health)
            setPhase('done')
          }
          return
        }
      } catch {
        // Network error, timeout or non-JSON response: treat as "server waking up".
      } finally {
        window.clearTimeout(requestTimer)
      }

      if (stopped) return
      if (Date.now() - startedAt + RETRY_DELAY_MS >= MAX_TOTAL_MS) {
        setPhase('failed')
        return
      }
      setPhase('waking')
      retryTimer = window.setTimeout(check, RETRY_DELAY_MS)
    }

    check()

    // Cleanup: no timers or requests survive unmount or a restart.
    return () => {
      stopped = true
      window.clearTimeout(retryTimer)
      window.clearTimeout(requestTimer)
      controller?.abort()
    }
  }, [attempt])

  function retry() {
    setHealth(null)
    setPhase('loading')
    setAttempt((n) => n + 1)
  }

  return (
    <main>
      <h1>Finance Tracker</h1>

      {phase === 'loading' && <p>Checking server...</p>}

      {phase === 'waking' && <p>Server is waking up. This can take up to a minute...</p>}

      {phase === 'failed' && (
        <>
          <p className="error">Could not reach the server. Please try again.</p>
          <button onClick={retry}>Retry</button>
        </>
      )}

      {phase === 'done' && health && (
        <dl>
          <dt>Status</dt>
          <dd>{health.status}</dd>
          <dt>Database</dt>
          <dd>{health.db}</dd>
          <dt>Server time</dt>
          <dd>{health.time}</dd>
        </dl>
      )}
    </main>
  )
}
