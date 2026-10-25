import { useState, type FormEvent } from 'react'
import { login, register } from './api'

type Mode = 'login' | 'register'

type Props = {
  // Called after a successful login/register. May throw; the message is shown under the form.
  onAuthenticated: () => Promise<void>
}

export default function AuthForm({ onAuthenticated }: Props) {
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const isRegister = mode === 'register'

  function toggleMode() {
    setMode(isRegister ? 'login' : 'register')
    setError(null)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return

    setSubmitting(true)
    setError(null)
    try {
      await (isRegister ? register : login)(email, password)
      // On success the parent switches views and this form unmounts.
      await onAuthenticated()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <h2>{isRegister ? 'Create account' : 'Log in'}</h2>

      <label htmlFor="auth-email">Email</label>
      <input
        id="auth-email"
        type="email"
        autoComplete={isRegister ? 'email' : 'username'}
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />

      <label htmlFor="auth-password">Password</label>
      <input
        id="auth-password"
        type="password"
        autoComplete={isRegister ? 'new-password' : 'current-password'}
        minLength={isRegister ? 8 : undefined}
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
      />

      <button type="submit" disabled={submitting}>
        {isRegister ? 'Create account' : 'Log in'}
      </button>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <button type="button" className="link" onClick={toggleMode} disabled={submitting}>
        {isRegister ? 'Already have an account? Log in' : 'Need an account? Create one'}
      </button>
    </form>
  )
}
