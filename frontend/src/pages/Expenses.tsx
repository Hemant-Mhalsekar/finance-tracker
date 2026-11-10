import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import {
  deleteExpense,
  getCurrentCycle,
  listCategories,
  listExpenses,
  updateExpense,
  type Category,
  type Expense,
  type ExpensePage,
} from '../api'
import ExpenseForm from '../components/ExpenseForm'
import { formatDate, todayIst } from '../dates'
import { TYPE_LABELS } from '../labels'
import { formatPaise } from '../money'

const PAGE_SIZE = 20

type Query = { from: string; to: string; categoryId: string; page: number }

export default function Expenses() {
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [cycleRange, setCycleRange] = useState<{ from: string; to: string } | null>(null)
  const [query, setQuery] = useState<Query | null>(null)
  // Bumped after an edit or delete to reload the same page.
  const [reloadCount, setReloadCount] = useState(0)
  const [result, setResult] = useState<{ key: string; page: ExpensePage } | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  // Defaults: the current cycle, all categories.
  useEffect(() => {
    let cancelled = false
    Promise.all([listCategories(true), getCurrentCycle()])
      .then(([all, cycle]) => {
        if (cancelled) return
        setCategories(all)
        const range = { from: cycle.periodStart, to: todayIst() }
        setCycleRange(range)
        setQuery({ ...range, categoryId: '', page: 0 })
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Something went wrong')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const requestKey = query ? `${JSON.stringify(query)}#${reloadCount}` : null

  useEffect(() => {
    if (!query || !requestKey) return
    let cancelled = false
    listExpenses({ ...query, size: PAGE_SIZE })
      .then((page) => {
        if (cancelled) return
        setResult({ key: requestKey, page })
        setError(null)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Something went wrong')
      })
    return () => {
      cancelled = true
    }
  }, [query, requestKey])

  const loading = requestKey !== null && result?.key !== requestKey
  const page = result?.page ?? null
  const categoryById = new Map((categories ?? []).map((c) => [c.id, c]))

  // Any filter change goes back to the first page.
  function updateFilter(changes: Partial<Omit<Query, 'page'>>) {
    setQuery((q) => (q ? { ...q, ...changes, page: 0 } : q))
    setEditingId(null)
    setConfirmDeleteId(null)
  }

  function goToPage(next: number) {
    setQuery((q) => (q ? { ...q, page: next } : q))
    setEditingId(null)
    setConfirmDeleteId(null)
  }

  async function handleDelete(expense: Expense) {
    setDeletingId(expense.id)
    setError(null)
    try {
      await deleteExpense(expense.id)
      setConfirmDeleteId(null)
      // Deleting the last row of a later page would leave an empty page.
      if (page && page.items.length === 1 && page.page > 0) {
        goToPage(page.page - 1)
      } else {
        setReloadCount((n) => n + 1)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setDeletingId(null)
    }
  }

  // Editing may keep an archived category in place, but never move into one.
  function editableCategories(expense: Expense): Category[] {
    return (categories ?? [])
      .filter((c) => !c.archived || c.id === expense.categoryId)
      .sort((a, b) => a.name.localeCompare(b.name))
  }

  if (!query || !categories || !cycleRange) {
    return (
      <section>
        <h1>Expenses</h1>
        {error ? (
          <p className="error" role="alert">
            {error}
          </p>
        ) : (
          <p className="muted">Loading...</p>
        )}
      </section>
    )
  }

  const showingCurrentCycle =
    query.from === cycleRange.from && query.to === cycleRange.to && query.categoryId === ''

  return (
    <section className="expenses" aria-busy={loading}>
      <div className="page-header">
        <h1>Expenses</h1>
        <Link to="/add" className="button-link">
          Add expense
        </Link>
      </div>

      <div className="filters">
        <div>
          <label htmlFor="filter-from">From</label>
          <input
            id="filter-from"
            type="date"
            min="2000-01-01"
            max={query.to}
            value={query.from}
            onChange={(e) => e.target.value && updateFilter({ from: e.target.value })}
          />
        </div>
        <div>
          <label htmlFor="filter-to">To</label>
          <input
            id="filter-to"
            type="date"
            min={query.from}
            max={todayIst()}
            value={query.to}
            onChange={(e) => e.target.value && updateFilter({ to: e.target.value })}
          />
        </div>
        <div>
          <label htmlFor="filter-category">Category</label>
          <select
            id="filter-category"
            value={query.categoryId}
            onChange={(e) => updateFilter({ categoryId: e.target.value })}
          >
            <option value="">All categories</option>
            {[...categories]
              .sort((a, b) => a.name.localeCompare(b.name))
              .map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                  {c.archived ? ' (archived)' : ''}
                </option>
              ))}
          </select>
        </div>
        <button
          type="button"
          className="secondary"
          disabled={showingCurrentCycle}
          onClick={() => updateFilter({ ...cycleRange, categoryId: '' })}
        >
          This cycle
        </button>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {page && (
        <>
          <p className="muted">
            {page.totalItems} {page.totalItems === 1 ? 'expense' : 'expenses'}
            {loading ? ' · updating...' : ''}
          </p>

          {page.items.length === 0 ? (
            <p className="muted">No expenses match these filters.</p>
          ) : (
            <ul className="expense-list">
              {page.items.map((expense) => {
                const category = categoryById.get(expense.categoryId)
                if (editingId === expense.id) {
                  return (
                    <li key={expense.id} className="card">
                      <h2>Edit expense</h2>
                      <ExpenseForm
                        categories={editableCategories(expense)}
                        initial={expense}
                        submitLabel="Save changes"
                        autoFocus
                        onSubmit={async (changes) => {
                          await updateExpense(expense.id, changes)
                          setEditingId(null)
                          setReloadCount((n) => n + 1)
                          return null
                        }}
                        onCancel={() => setEditingId(null)}
                      />
                    </li>
                  )
                }
                return (
                  <li key={expense.id} className="expense-row">
                    <div className="expense-main">
                      <span className="expense-description">{expense.description}</span>
                      <span className="muted">
                        {formatDate(expense.expenseDate)} · {category?.name ?? 'Unknown category'} ·{' '}
                        {TYPE_LABELS[expense.type]}
                      </span>
                    </div>
                    <span className="expense-amount">{formatPaise(expense.amountPaise)}</span>
                    <div className="row-actions">
                      {confirmDeleteId === expense.id ? (
                        <>
                          <button
                            type="button"
                            className="danger"
                            disabled={deletingId === expense.id}
                            onClick={() => handleDelete(expense)}
                          >
                            {deletingId === expense.id ? 'Deleting...' : 'Confirm delete'}
                          </button>
                          <button
                            type="button"
                            className="secondary"
                            disabled={deletingId === expense.id}
                            onClick={() => setConfirmDeleteId(null)}
                          >
                            Keep
                          </button>
                        </>
                      ) : (
                        <>
                          <button
                            type="button"
                            className="secondary"
                            onClick={() => {
                              setEditingId(expense.id)
                              setConfirmDeleteId(null)
                            }}
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            className="secondary"
                            onClick={() => {
                              setConfirmDeleteId(expense.id)
                              setEditingId(null)
                            }}
                          >
                            Delete
                          </button>
                        </>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
          )}

          {page.totalPages > 1 && (
            <nav className="pager" aria-label="Pages">
              <button
                type="button"
                className="secondary"
                disabled={loading || page.page === 0}
                onClick={() => goToPage(page.page - 1)}
              >
                ← Newer
              </button>
              <span className="muted">
                Page {page.page + 1} of {page.totalPages}
              </span>
              <button
                type="button"
                className="secondary"
                disabled={loading || page.page >= page.totalPages - 1}
                onClick={() => goToPage(page.page + 1)}
              >
                Older →
              </button>
            </nav>
          )}
        </>
      )}
    </section>
  )
}
