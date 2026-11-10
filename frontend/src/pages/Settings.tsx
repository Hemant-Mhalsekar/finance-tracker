import { useEffect, useState, type FormEvent } from 'react'
import { changeCurrentSalary, changePayDay, getCurrentCycle, type Cycle, type Me } from '../api'
import { formatDate } from '../dates'
import { formatPaise, paiseToInput, parseRupeesToPaise, splitBudget } from '../money'

const MIN_SALARY_PAISE = 100
const MAX_SALARY_PAISE = 1_000_000_000
const DAYS = Array.from({ length: 31 }, (_, i) => i + 1)

type Props = {
  me: Me
  // Reloads the user after a pay day change so the pending change shows everywhere.
  onUserChanged: () => Promise<void>
}

export default function Settings({ me, onUserChanged }: Props) {
  return (
    <section className="settings narrow-section">
      <h1>Settings</h1>
      <SalarySettings />
      <PayDaySettings me={me} onUserChanged={onUserChanged} />
      <article className="card">
        <h2>Account</h2>
        <p>{me.email}</p>
      </article>
    </section>
  )
}

function SalarySettings() {
  const [cycle, setCycle] = useState<Cycle | null>(null)
  const [salaryText, setSalaryText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    getCurrentCycle()
      .then((current) => {
        if (cancelled) return
        setCycle(current)
        setSalaryText(paiseToInput(current.salaryPaise))
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Something went wrong')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const salaryPaise = parseRupeesToPaise(salaryText)
  const valid = salaryPaise !== null && salaryPaise >= MIN_SALARY_PAISE && salaryPaise <= MAX_SALARY_PAISE
  const unchanged = cycle !== null && salaryPaise === cycle.salaryPaise
  const split = valid ? splitBudget(salaryPaise) : null

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting || !cycle) return
    if (!valid) {
      setError('Enter a salary between ₹1 and ₹1,00,00,000, with at most 2 decimals')
      setSuccess(null)
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const updated = await changeCurrentSalary(salaryPaise)
      setCycle(updated)
      setSalaryText(paiseToInput(updated.salaryPaise))
      setSuccess(`Salary is now ${formatPaise(updated.salaryPaise)} for this cycle and the ones after it.`)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      setSuccess(null)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <article className="card">
      <h2>Salary</h2>
      {!cycle && !error && <p className="muted">Loading...</p>}
      {cycle && (
        <form onSubmit={handleSubmit} noValidate>
          <p className="muted">
            Applies to the current cycle ({formatDate(cycle.periodStart)} – {formatDate(cycle.periodEnd)}) and later
            cycles. Past cycles keep their salary.
          </p>
          <label htmlFor="settings-salary">Monthly take-home salary (₹)</label>
          <input
            id="settings-salary"
            type="text"
            inputMode="decimal"
            autoComplete="off"
            value={salaryText}
            onChange={(e) => {
              setSalaryText(e.target.value)
              setSuccess(null)
            }}
          />
          {split && !unchanged && (
            <p className="hint">
              Needs {formatPaise(split.needs)} · Wants {formatPaise(split.wants)} · Savings{' '}
              {formatPaise(split.savings)}
            </p>
          )}
          <div className="form-actions">
            <button type="submit" disabled={submitting || unchanged}>
              {submitting ? 'Saving...' : 'Update salary'}
            </button>
          </div>
        </form>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {success && (
        <p className="success" role="status">
          {success}
        </p>
      )}
    </article>
  )
}

function PayDaySettings({ me, onUserChanged }: Props) {
  const currentPayDay = me.payDay ?? 1
  const [selected, setSelected] = useState(String(me.pendingPayDay ?? currentPayDay))
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(payDay: number) {
    setSubmitting(true)
    setError(null)
    try {
      await changePayDay(payDay)
      await onUserChanged()
      setSelected(String(payDay))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  const selectedDay = Number(selected)
  const nothingToSave = selectedDay === (me.pendingPayDay ?? currentPayDay)

  return (
    <article className="card">
      <h2>Pay day</h2>
      <p>
        Your salary arrives on day <strong>{currentPayDay}</strong> of the month.
      </p>
      {me.pendingPayDay !== null && me.pendingPayDayFrom !== null && (
        <div className="notice">
          <p>
            Changing to day <strong>{me.pendingPayDay}</strong>. The first cycle on the new pay day starts on{' '}
            <strong>{formatDate(me.pendingPayDayFrom)}</strong>.
          </p>
          <button type="button" className="secondary" disabled={submitting} onClick={() => submit(currentPayDay)}>
            Cancel this change
          </button>
        </div>
      )}
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (!submitting && !nothingToSave) submit(selectedDay)
        }}
        noValidate
      >
        <p className="muted">
          A change waits one full cycle: your current cycle and the next one keep the current pay day, so no cycle is
          cut short without warning.
        </p>
        <label htmlFor="settings-pay-day">New pay day</label>
        <select id="settings-pay-day" value={selected} onChange={(e) => setSelected(e.target.value)}>
          {DAYS.map((day) => (
            <option key={day} value={day}>
              {day}
            </option>
          ))}
        </select>
        {selectedDay >= 29 && <p className="hint">In shorter months, the last day of the month is used.</p>}
        <div className="form-actions">
          <button type="submit" disabled={submitting || nothingToSave}>
            {submitting ? 'Saving...' : 'Schedule change'}
          </button>
        </div>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </article>
  )
}
