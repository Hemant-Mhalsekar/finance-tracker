import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { ApiError, getCurrentReport, getReportForDate, type BudgetLine, type CycleReport } from '../api'
import { addDays, formatDate, todayIst } from '../dates'
import { TYPE_LABELS } from '../labels'
import { formatPaise } from '../money'

// Nudge only after a few quiet days; daily loggers should not see it.
const NUDGE_AFTER_DAYS = 3

export default function Dashboard() {
  // null = the current cycle; otherwise any date inside the cycle being viewed.
  const [viewDate, setViewDate] = useState<string | null>(null)
  const [report, setReport] = useState<CycleReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(viewDate === null ? getCurrentReport() : getReportForDate(viewDate))
      .then((result) => {
        if (cancelled) return
        setReport(result)
        setError(null)
      })
      .catch((e: unknown) => {
        if (cancelled) return
        // Asking for the cycle before the first one: keep showing the report already on screen.
        if (e instanceof ApiError && e.status === 404) {
          setNotice('There is no earlier cycle.')
          return
        }
        setError(e instanceof Error ? e.message : 'Something went wrong')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [viewDate])

  // Loading state is set here, in the event, rather than in the effect that fetches.
  function goTo(date: string) {
    // The same date would not re-run the effect, leaving the page stuck on loading.
    if (date === viewDate) return
    setLoading(true)
    setNotice(null)
    setViewDate(date)
  }

  if (error && !report) {
    return (
      <section>
        <h1>Dashboard</h1>
        <p className="error" role="alert">
          {error}
        </p>
      </section>
    )
  }

  if (!report) {
    return (
      <section>
        <h1>Dashboard</h1>
        <p className="muted">Loading...</p>
      </section>
    )
  }

  const { cycle, days, needs, wants, savings } = report
  const isCurrent = cycle.periodEnd >= todayIst()

  return (
    <section className="dashboard" aria-busy={loading}>
      <div className="cycle-header">
        <div>
          <h1>
            {formatDate(cycle.periodStart)} – {formatDate(cycle.periodEnd)}
          </h1>
          <p className="muted">
            Salary {formatPaise(cycle.salaryPaise)} ·{' '}
            {isCurrent ? `${days.remaining} ${days.remaining === 1 ? 'day' : 'days'} left` : 'Past cycle'}
          </p>
        </div>
        <div className="cycle-nav">
          <button
            type="button"
            className="secondary"
            disabled={loading || notice !== null}
            onClick={() => goTo(addDays(cycle.periodStart, -1))}
          >
            ← Previous
          </button>
          <button
            type="button"
            className="secondary"
            disabled={loading || isCurrent}
            onClick={() => goTo(addDays(cycle.periodEnd, 1))}
          >
            Next →
          </button>
        </div>
      </div>

      {notice && <p className="muted">{notice}</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      <Warnings report={report} isCurrent={isCurrent} />

      <div className="cards">
        <BudgetCard title="Needs" line={needs} />
        <BudgetCard title="Wants" line={wants} />
        <article className="card">
          <h2>Savings</h2>
          <p className={`big ${savings.deficit ? 'negative' : ''}`}>{formatPaise(savings.savedPaise)}</p>
          <p className="muted">
            {isCurrent ? 'Saved so far' : 'Saved'} · plan {formatPaise(savings.plannedPaise)}
          </p>
          <p>Invested {formatPaise(savings.investedPaise)}</p>
        </article>
      </div>

      <article className="card">
        <h2>By category</h2>
        {report.categories.length === 0 ? (
          <p className="muted">
            No expenses in this cycle. {isCurrent && <Link to="/add">Add one</Link>}
          </p>
        ) : (
          <table className="category-table">
            <thead>
              <tr>
                <th scope="col">Category</th>
                <th scope="col">Type</th>
                <th scope="col" className="num">
                  Spent
                </th>
              </tr>
            </thead>
            <tbody>
              {report.categories.map((row) => (
                <tr key={`${row.categoryId}-${row.type}`}>
                  <td>
                    {row.name}
                    {row.archived && <span className="muted"> (archived)</span>}
                    <span className="muted count">
                      {' '}
                      · {row.expenseCount} {row.expenseCount === 1 ? 'expense' : 'expenses'}
                    </span>
                  </td>
                  <td>{TYPE_LABELS[row.type]}</td>
                  <td className="num">{formatPaise(row.spentPaise)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </article>
    </section>
  )
}

function Warnings({ report, isCurrent }: { report: CycleReport; isCurrent: boolean }) {
  const messages: string[] = []
  if (report.spendingExceedsSalary) {
    messages.push(
      `You logged ${formatPaise(report.totalLoggedPaise)} this cycle, more than your salary of ${formatPaise(report.cycle.salaryPaise)}.`,
    )
  }
  if (report.savings.deficit) {
    messages.push(`Needs and wants are ${formatPaise(-report.savings.savedPaise)} over your salary.`)
  }
  if (isCurrent) {
    if (report.daysSinceLastExpenseLogged === null) {
      messages.push('No expenses logged yet.')
    } else if (report.daysSinceLastExpenseLogged >= NUDGE_AFTER_DAYS) {
      messages.push(`Your last expense was logged ${report.daysSinceLastExpenseLogged} days ago.`)
    }
  }
  if (messages.length === 0) return null

  return (
    <ul className="warnings">
      {messages.map((m) => (
        <li key={m}>{m}</li>
      ))}
    </ul>
  )
}

function BudgetCard({ title, line }: { title: string; line: BudgetLine }) {
  const used = line.allocatedPaise > 0 ? Math.min(100, (line.spentPaise / line.allocatedPaise) * 100) : 100
  return (
    <article className={`card ${line.overspent ? 'over' : ''}`}>
      <h2>{title}</h2>
      <p className="big">{formatPaise(line.spentPaise)}</p>
      <p className="muted">of {formatPaise(line.allocatedPaise)}</p>
      <div
        className="bar"
        role="progressbar"
        aria-label={`${title} used`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(used)}
      >
        <div className="bar-fill" style={{ width: `${used}%` }} />
      </div>
      <p className={line.overspent ? 'negative' : ''}>
        {line.overspent
          ? `Over by ${formatPaise(-line.remainingPaise)}`
          : `${formatPaise(line.remainingPaise)} left`}
      </p>
    </article>
  )
}
