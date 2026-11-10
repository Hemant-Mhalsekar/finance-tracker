// The access token lives only in this module variable (never in any browser storage).
// It is lost on page reload; the HttpOnly refresh cookie is used to get a new one.
let accessToken: string | null = null

const AUTH_PATHS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout']

const PING_INTERVAL_MS = 3_000
const PING_TIMEOUT_MS = 10_000
// A Render free-tier cold start takes about 140 s, so give up only well after that.
const PING_MAX_TOTAL_MS = 180_000

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

// --- typed API calls ---
// Response shapes are trusted as the backend's documented contract (see CLAUDE.md, API section).

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await apiFetch(path, init)
  } catch {
    throw new ApiError(0, 'Could not reach the server')
  }
  const body: unknown = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    const message =
      typeof body === 'object' && body !== null && typeof (body as { message?: unknown }).message === 'string'
        ? (body as { message: string }).message
        : 'Something went wrong'
    throw new ApiError(response.status, message)
  }
  return body as T
}

function sendJson<T>(path: string, method: string, payload: unknown): Promise<T> {
  return requestJson<T>(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export type BudgetType = 'NEED' | 'WANT' | 'INVESTMENT'

export type Me = {
  id: string
  email: string
  payDay: number | null
  pendingPayDay: number | null
  pendingPayDayFrom: string | null
}

export function getMe(): Promise<Me> {
  return requestJson<Me>('/api/me')
}

export type Cycle = {
  periodStart: string
  periodEnd: string
  salaryPaise: number
  needsPaise: number
  wantsPaise: number
  savingsPaise: number
}

export function onboard(salaryPaise: number, payDay: number): Promise<{ payDay: number; cycle: Cycle }> {
  return sendJson('/api/onboarding', 'POST', { salaryPaise, payDay })
}

export type BudgetLine = {
  allocatedPaise: number
  spentPaise: number
  remainingPaise: number
  overspent: boolean
}

export type CategorySpend = {
  categoryId: string
  name: string
  archived: boolean
  type: BudgetType
  spentPaise: number
  expenseCount: number
}

export type CycleReport = {
  cycle: Cycle
  days: { total: number; elapsed: number; remaining: number }
  needs: BudgetLine
  wants: BudgetLine
  savings: { plannedPaise: number; savedPaise: number; investedPaise: number; deficit: boolean }
  totalLoggedPaise: number
  spendingExceedsSalary: boolean
  expenseCount: number
  categories: CategorySpend[]
  lastExpenseLoggedOn: string | null
  daysSinceLastExpenseLogged: number | null
}

export function getCurrentCycle(): Promise<Cycle> {
  return requestJson<Cycle>('/api/cycles/current')
}

export function changeCurrentSalary(salaryPaise: number): Promise<Cycle> {
  return sendJson<Cycle>('/api/cycles/current/salary', 'PUT', { salaryPaise })
}

export type PayDaySettings = { payDay: number; pendingPayDay: number | null; pendingPayDayFrom: string | null }

/** Takes effect after one full cycle. Sending the current pay day cancels a pending change. */
export function changePayDay(payDay: number): Promise<PayDaySettings> {
  return sendJson<PayDaySettings>('/api/settings/pay-day', 'PUT', { payDay })
}

export function getCurrentReport(): Promise<CycleReport> {
  return requestJson<CycleReport>('/api/cycles/current/report')
}

/** Report for the stored cycle containing the date. Throws ApiError 404 if there is none. */
export function getReportForDate(isoDate: string): Promise<CycleReport> {
  return requestJson<CycleReport>(`/api/cycles/report?date=${encodeURIComponent(isoDate)}`)
}

export type Category = { id: string; name: string; type: BudgetType | null; archived: boolean }

export function listCategories(includeArchived = false): Promise<Category[]> {
  return requestJson<Category[]>(`/api/categories${includeArchived ? '?includeArchived=true' : ''}`)
}

export function createCategory(name: string, type: BudgetType): Promise<Category> {
  return sendJson<Category>('/api/categories', 'POST', { name, type })
}

/** Only the fields given are changed. Other (type null) cannot be changed at all. */
export function updateCategory(
  id: string,
  changes: { name?: string; type?: BudgetType; archived?: boolean },
): Promise<Category> {
  return sendJson<Category>(`/api/categories/${encodeURIComponent(id)}`, 'PATCH', changes)
}

/** Fails with 409 when the category still has expenses; archiving is the alternative. */
export async function deleteCategory(id: string): Promise<void> {
  await requestJson<null>(`/api/categories/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export type Expense = {
  id: string
  categoryId: string
  type: BudgetType
  amountPaise: number
  expenseDate: string
  description: string
}

export type NewExpense = {
  amountPaise: number
  categoryId: string
  description: string
  expenseDate: string
  // Only for categories without a type (Other); the API rejects it otherwise.
  type?: BudgetType
}

export function createExpense(expense: NewExpense): Promise<Expense> {
  return sendJson<Expense>('/api/expenses', 'POST', expense)
}

export function updateExpense(id: string, expense: NewExpense): Promise<Expense> {
  return sendJson<Expense>(`/api/expenses/${encodeURIComponent(id)}`, 'PUT', expense)
}

export async function deleteExpense(id: string): Promise<void> {
  await requestJson<null>(`/api/expenses/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export type ExpensePage = {
  items: Expense[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type ExpenseQuery = { from?: string; to?: string; categoryId?: string; page?: number; size?: number }

/** Newest first. Pages start at 0; size is at most 100. */
export function listExpenses(query: ExpenseQuery): Promise<ExpensePage> {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  const search = params.toString()
  return requestJson<ExpensePage>(`/api/expenses${search ? `?${search}` : ''}`)
}
