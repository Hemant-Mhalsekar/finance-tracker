// The access token lives only in this module variable (never in any browser storage).
// It is lost on page reload; the HttpOnly refresh cookie is used to get a new one.
let accessToken: string | null = null

const AUTH_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout']

const PING_INTERVAL_MS = 3_000
const PING_TIMEOUT_MS = 10_000
const PING_MAX_TOTAL_MS = 90_000

// --- "logged out" notifications ---

const loggedOutListeners = new Set<() => void>()

/** Subscribe to "the session ended" events. Returns an unsubscribe function. */
export function onLoggedOut(listener: () => void): () => void {
  loggedOutListeners.add(listener)
  return () => {
    loggedOutListeners.delete(listener)
  }
}

function notifyLoggedOut() {
  loggedOutListeners.forEach((listener) => listener())
}

// --- core fetch ---

export async function apiFetch(path: string, options: RequestInit = {}): Promise<Response> {
  const isAuthPath = AUTH_PATHS.includes(path)

  function send(): Promise<Response> {
    const headers = new Headers(options.headers)
    // Auth endpoints are public; an old token there would only cause a pointless 401.
    if (accessToken && !isAuthPath) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }
    return fetch(path, { ...options, headers, credentials: 'same-origin' })
  }

  const response = await send()
  if (response.status !== 401 || isAuthPath) {
    return response
  }

  // Access token probably expired: refresh once and retry the original request once.
  if (await refreshSession()) {
    return send()
  }
  notifyLoggedOut()
  return response
}

// --- refresh ---

let refreshInFlight: Promise<boolean> | null = null

/** Gets a new access token using the refresh cookie. Simultaneous callers share one request. */
export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = doRefresh().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

async function doRefresh(): Promise<boolean> {
  try {
    const response = await apiFetch('/api/auth/refresh', { method: 'POST' })
    const token = response.ok ? await readAccessToken(response) : null
    if (!token) {
      accessToken = null
      return false
    }
    accessToken = token
    return true
  } catch {
    accessToken = null
    return false
  }
}

// --- cold start ---

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const timer = window.setTimeout(done, ms)
    function done() {
      window.clearTimeout(timer)
      signal?.removeEventListener('abort', done)
      resolve()
    }
    signal?.addEventListener('abort', done)
  })
}

/**
 * Polls GET /api/ping until the server answers "ok". Only for cold starts.
 * Pass a signal to stop polling early (for example on unmount).
 */
export async function waitForServer(signal?: AbortSignal): Promise<boolean> {
  const deadline = Date.now() + PING_MAX_TOTAL_MS

  while (!signal?.aborted && Date.now() < deadline) {
    const controller = new AbortController()
    const abort = () => controller.abort()
    const timeout = window.setTimeout(abort, Math.min(PING_TIMEOUT_MS, deadline - Date.now()))
    signal?.addEventListener('abort', abort)

    try {
      const response = await fetch('/api/ping', { signal: controller.signal })
      if (response.status === 200 && (await response.text()).trim() === 'ok') {
        return true
      }
    } catch {
      // Network error or timeout: the server is probably still starting.
    } finally {
      window.clearTimeout(timeout)
      signal?.removeEventListener('abort', abort)
    }

    await sleep(PING_INTERVAL_MS, signal)
  }
  return false
}

// --- auth calls ---

async function readJson(response: Response): Promise<Record<string, unknown> | null> {
  try {
    const body: unknown = await response.json()
    return typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : null
  } catch {
    return null
  }
}

async function readAccessToken(response: Response): Promise<string | null> {
  const body = await readJson(response)
  return typeof body?.accessToken === 'string' ? body.accessToken : null
}

async function authenticate(path: string, email: string, password: string): Promise<void> {
  let response: Response
  try {
    response = await apiFetch(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
  } catch {
    throw new Error('Something went wrong')
  }

  if (!response.ok) {
    const body = await readJson(response)
    throw new Error(typeof body?.message === 'string' ? body.message : 'Something went wrong')
  }

  const token = await readAccessToken(response)
  if (!token) {
    throw new Error('Something went wrong')
  }
  accessToken = token
}

export function register(email: string, password: string): Promise<void> {
  return authenticate('/api/auth/register', email, password)
}

export function login(email: string, password: string): Promise<void> {
  return authenticate('/api/auth/login', email, password)
}

/** Ends the session on the server, then forgets the token whatever the response was. */
export async function logout(): Promise<void> {
  try {
    await apiFetch('/api/auth/logout', { method: 'POST' })
  } catch {
    // Ignored: the token is cleared below either way.
  } finally {
    accessToken = null
  }
}

export type Me = { id: string; email: string }

export async function getMe(): Promise<Me> {
  const response = await apiFetch('/api/me')
  const body = await readJson(response)
  if (!response.ok || typeof body?.id !== 'string' || typeof body.email !== 'string') {
    throw new Error('Something went wrong')
  }
  return { id: body.id, email: body.email }
}
