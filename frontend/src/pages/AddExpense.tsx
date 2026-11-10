import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { createExpense, listCategories, type BudgetType, type Category } from '../api'
import { todayIst } from '../dates'
import { TYPE_LABELS } from '../labels'
import { formatPaise, parseRupeesToPaise } from '../money'

const MAX_AMOUNT_PAISE = 10_000_000_000
const MIN_DATE = '2000-01-01'

export default function AddExpense() {
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [amountText, setAmountText] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [type, setType] = useState<BudgetType | ''>('')
  const [description, setDescription] = useState('')
  const [date, setDate] = useState(todayIst)

  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const amountRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let cancelled = false
    listCategories()
      .then((all) => {
        if (cancelled) return
        const active = all.filter((c) => !c.archived).sort((a, b) => a.name.localeCompare(b.name))
        setCategories(active)
      })
      .catch((e: unknown) => {
        if (!cancelled) setLoadError(e instanceof Error ? e.message : 'Something went wrong')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const category = categories?.find((c) => c.id === categoryId)
  // Categories without a type (Other) need the type picked for each expense.
  const needsType = category !== undefined && category.type === null

  function validate(): string | null {
    const amountPaise = parseRupeesToPaise(amountText)
    if (amountPaise === null || amountPaise < 1 || amountPaise > MAX_AMOUNT_PAISE) {
      return 'Enter an amount between ₹0.01 and ₹10,00,00,000, with at most 2 decimals'
    }
    if (!category) return 'Choose a category'
    if (needsType && type === '') return 'Choose whether this is a need, want or investment'
    if (description.trim() === '') return 'Add a short description'
    if (date === '' || date < MIN_DATE || date > todayIst()) return 'Choose a date between 2000 and today'
    return null
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    const problem = validate()
    if (problem) {
      setError(problem)
      setSaved(null)
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      const expense = await createExpense({
        amountPaise: parseRupeesToPaise(amountText)!,
        categoryId,
        description: description.trim(),
        expenseDate: date,
        ...(needsType && type !== '' ? { type } : {}),
      })
      setSaved(`Saved ${formatPaise(expense.amountPaise)} in ${category!.name}`)
      // Keep category and date: several expenses in a row usually share them.
      setAmountText('')
      setDescription('')
      amountRef.current?.focus()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      setSaved(null)
    } finally {
      setSubmitting(false)
    }
  }

  if (loadError) {
    return (
      <section className="narrow-section">
        <h1>Add expense</h1>
        <p className="error" role="alert">
          {loadError}
        </p>
      </section>
    )
  }

  if (!categories) {
    return (
      <section className="narrow-section">
        <h1>Add expense</h1>
        <p className="muted">Loading categories...</p>
      </section>
    )
  }

  return (
    <section className="narrow-section">
      <h1>Add expense</h1>
      <form onSubmit={handleSubmit} noValidate>
        <label htmlFor="amount">Amount (₹)</label>
        <input
          id="amount"
          ref={amountRef}
          className="amount-input"
          type="text"
          inputMode="decimal"
          autoComplete="off"
          autoFocus
          placeholder="0.00"
          value={amountText}
          onChange={(e) => setAmountText(e.target.value)}
        />

        <label htmlFor="category">Category</label>
        <select
          id="category"
          value={categoryId}
          onChange={(e) => {
            setCategoryId(e.target.value)
            setType('')
          }}
        >
          <option value="" disabled>
            Choose a category
          </option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
              {c.type ? ` (${TYPE_LABELS[c.type]})` : ''}
            </option>
          ))}
        </select>

        {needsType && (
          <fieldset className="type-picker">
            <legend>This expense is a</legend>
            {(Object.keys(TYPE_LABELS) as BudgetType[]).map((t) => (
              <label key={t}>
                <input type="radio" name="type" value={t} checked={type === t} onChange={() => setType(t)} />
                {TYPE_LABELS[t]}
              </label>
            ))}
          </fieldset>
        )}

        <label htmlFor="description">Description</label>
        <input
          id="description"
          type="text"
          maxLength={200}
          autoComplete="off"
          placeholder="What was it for?"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <label htmlFor="date">Date</label>
        <input
          id="date"
          type="date"
          min={MIN_DATE}
          max={todayIst()}
          value={date}
          onChange={(e) => setDate(e.target.value)}
        />

        <button type="submit" disabled={submitting}>
          {submitting ? 'Saving...' : 'Save expense'}
        </button>

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        {saved && (
          <p className="success" role="status">
            {saved}. <Link to="/">See dashboard</Link>
          </p>
        )}
      </form>
    </section>
  )
}
