import { useEffect, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { getMe, logout, onLoggedOut, refreshSession, waitForServer, type Me } from './api'
import AppShell from './AppShell'
import AuthForm from './AuthForm'
import AddExpense from './pages/AddExpense'
import Dashboard from './pages/Dashboard'
import Onboarding from './pages/Onboarding'

type Phase = 'waking' | 'serverError' | 'form' | 'ready'

export default function App() {
  const [phase, setPhase] = useState<Phase>('waking')
  const [me, setMe] = useState<Me | null>(null)
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
          const user = await getMe()
          if (controller.signal.aborted) return
          setMe(user)
          setPhase('ready')
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
        setMe(null)
        setPhase('form')
      }),
    [],
  )

  function retry() {
    setPhase('waking')
    setAttempt((n) => n + 1)
  }

  async function loadUser() {
    setMe(await getMe())
    setPhase('ready')
  }

  async function handleLogout() {
    setLoggingOut(true)
    await logout()
    setLoggingOut(false)
    setMe(null)
    setPhase('form')
  }

  if (phase !== 'ready' || !me) {
    return (
      <main className="narrow">
        <h1>Finance Tracker</h1>

        {phase === 'waking' && (
          <p>Server is waking up. On the free hosting plan this can take up to 3 minutes...</p>
        )}

        {phase === 'serverError' && (
          <>
            <p className="error">Could not reach the server. Please try again.</p>
            <button onClick={retry}>Retry</button>
          </>
        )}

        {phase === 'form' && <AuthForm onAuthenticated={loadUser} />}
      </main>
    )
  }

  // Until onboarding is done, every URL shows the onboarding screen.
  if (me.payDay === null) {
    return (
      <Routes>
        <Route path="/onboarding" element={<Onboarding onDone={loadUser} />} />
        <Route path="*" element={<Navigate to="/onboarding" replace />} />
      </Routes>
    )
  }

  return (
    <Routes>
      <Route element={<AppShell email={me.email} onLogout={handleLogout} loggingOut={loggingOut} />}>
        <Route index element={<Dashboard />} />
        <Route path="add" element={<AddExpense />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
