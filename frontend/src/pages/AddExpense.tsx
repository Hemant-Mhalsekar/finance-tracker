import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { createExpense, listCategories, type Category } from '../api'
import ExpenseForm from '../components/ExpenseForm'
import { formatPaise } from '../money'

export default function AddExpense() {
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listCategories()
      .then((all) => {
        if (cancelled) return
        // New expenses cannot go into archived categories.
        setCategories(all.filter((c) => !c.archived).sort((a, b) => a.name.localeCompare(b.name)))
      })
      .catch((e: unknown) => {
        if (!cancelled) setLoadError(e instanceof Error ? e.message : 'Something went wrong')
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <section className="narrow-section">
      <h1>Add expense</h1>
      {loadError && (
        <p className="error" role="alert">
          {loadError}
        </p>
      )}
      {!loadError && !categories && <p className="muted">Loading categories...</p>}
      {categories && (
        <>
          <ExpenseForm
            categories={categories}
            submitLabel="Save expense"
            resetAfterSubmit
            autoFocus
            onSubmit={async (expense, category) => {
              const saved = await createExpense(expense)
              return `Saved ${formatPaise(saved.amountPaise)} in ${category.name}.`
            }}
          />
          <p>
            <Link to="/">See dashboard</Link> · <Link to="/expenses">All expenses</Link>
          </p>
        </>
      )}
    </section>
  )
}
