import { useEffect, useState } from 'react'
import { getMe, logout, onLoggedOut, refreshSession, waitForServer } from './api'
import AuthForm from './AuthForm'

type Phase = 'waking' | 'serverError' | 'form' | 'loggedIn'

export default function App() {
  const [phase, setPhase] = useState<Phase>('waking')
  const [email, setEmail] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  // Incrementing this restarts the startup sequence (used by the Retry button).
  const [attempt, setAttempt] = useState(0)

  // Startup: wait for the server, then try to restore the session once.
  useEffect(() => {
    const controller = new AbortController()

    async function start() {
      const serverUp = await waitForServer(controller.signal)
      if (controller.signal.aborted) return
      if (!serverUp) {
        setPhase('serverError')
        return
      }

      if (await refreshSession()) {
        try {
          const me = await getMe()
          if (controller.signal.aborted) return
          setEmail(me.email)
          setPhase('loggedIn')
          return
        } catch {
          // Fall through to the login form.
        }
      }
      if (!controller.signal.aborted) setPhase('form')
    }

    start()

    // Cleanup: stops the ping polling and its timers.
    return () => controller.abort()
  }, [attempt])

  // Return to the form if a session refresh ever fails.
  useEffect(
    () =>
      onLoggedOut(() => {
        setEmail('')
        setPhase('form')
      }),
    [],
  )

  function retry() {
    setPhase('waking')
    setAttempt((n) => n + 1)
  }

  async function loadUser() {
    const me = await getMe()
    setEmail(me.email)
    setPhase('loggedIn')
  }

  async function handleLogout() {
    setLoggingOut(true)
    await logout()
    setLoggingOut(false)
    setEmail('')
    setPhase('form')
  }

  return (
    <main>
      <h1>Finance Tracker</h1>

      {phase === 'waking' && <p>Server is waking up. This can take up to a minute...</p>}

      {phase === 'serverError' && (
        <>
          <p className="error">Could not reach the server. Please try again.</p>
          <button onClick={retry}>Retry</button>
        </>
      )}

      {phase === 'form' && <AuthForm onAuthenticated={loadUser} />}

      {phase === 'loggedIn' && (
        <>
          <p>Logged in as {email}</p>
          <button onClick={handleLogout} disabled={loggingOut}>
            Log out
          </button>
        </>
      )}
    </main>
  )
}
