import { useId, useRef, useState, type FormEvent } from 'react'
import type { BudgetType, Category, NewExpense } from '../api'
import { todayIst } from '../dates'
import { TYPE_LABELS } from '../labels'
import { paiseToInput, parseRupeesToPaise } from '../money'

const MAX_AMOUNT_PAISE = 10_000_000_000
const MIN_DATE = '2000-01-01'

export type ExpenseFormInitial = {
  amountPaise: number
  categoryId: string
  type: BudgetType
  description: string
  expenseDate: string
}

type Props = {
  categories: Category[]
  initial?: ExpenseFormInitial
  submitLabel: string
  // Add mode: clear amount and description after saving, keep category and date for the next entry.
  resetAfterSubmit?: boolean
  autoFocus?: boolean
  // Resolves to a success message to show, or throws to show its message as an error.
  onSubmit: (expense: NewExpense, category: Category) => Promise<string | null>
  onCancel?: () => void
}

export default function ExpenseForm({
  categories,
  initial,
  submitLabel,
  resetAfterSubmit = false,
  autoFocus = false,
  onSubmit,
  onCancel,
}: Props) {
  const id = useId()
  const initialCategory = categories.find((c) => c.id === initial?.categoryId)

  const [amountText, setAmountText] = useState(initial ? paiseToInput(initial.amountPaise) : '')
  const [categoryId, setCategoryId] = useState(initial?.categoryId ?? '')
  // Only meaningful for categories without a type (Other), where it was chosen per expense.
  const [type, setType] = useState<BudgetType | ''>(initialCategory?.type === null ? (initial?.type ?? '') : '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [date, setDate] = useState(initial?.expenseDate ?? todayIst())

  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const amountRef = useRef<HTMLInputElement>(null)

  const category = categories.find((c) => c.id === categoryId)
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
      setSuccess(null)
      return
    }

    setSubmitting(true)
    setError(null)
    try {
      const message = await onSubmit(
        {
          amountPaise: parseRupeesToPaise(amountText)!,
          categoryId,
          description: description.trim(),
          expenseDate: date,
          // The API rejects a type for categories that have their own.
          ...(needsType && type !== '' ? { type } : {}),
        },
        category!,
      )
      setSuccess(message)
      if (resetAfterSubmit) {
        setAmountText('')
        setDescription('')
        amountRef.current?.focus()
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
      setSuccess(null)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <label htmlFor={`${id}-amount`}>Amount (₹)</label>
      <input
        id={`${id}-amount`}
        ref={amountRef}
        className="amount-input"
        type="text"
        inputMode="decimal"
        autoComplete="off"
        autoFocus={autoFocus}
        placeholder="0.00"
        value={amountText}
        onChange={(e) => setAmountText(e.target.value)}
      />

      <label htmlFor={`${id}-category`}>Category</label>
      <select
        id={`${id}-category`}
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
            {c.archived ? ' – archived' : ''}
          </option>
        ))}
      </select>

      {needsType && (
        <fieldset className="type-picker">
          <legend>This expense is a</legend>
          {(Object.keys(TYPE_LABELS) as BudgetType[]).map((t) => (
            <label key={t}>
              <input
                type="radio"
                name={`${id}-type`}
                value={t}
                checked={type === t}
                onChange={() => setType(t)}
              />
              {TYPE_LABELS[t]}
            </label>
          ))}
        </fieldset>
      )}

      <label htmlFor={`${id}-description`}>Description</label>
      <input
        id={`${id}-description`}
        type="text"
        maxLength={200}
        autoComplete="off"
        placeholder="What was it for?"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />

      <label htmlFor={`${id}-date`}>Date</label>
      <input
        id={`${id}-date`}
        type="date"
        min={MIN_DATE}
        max={todayIst()}
        value={date}
        onChange={(e) => setDate(e.target.value)}
      />

      <div className="form-actions">
        <button type="submit" disabled={submitting}>
          {submitting ? 'Saving...' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="secondary" onClick={onCancel} disabled={submitting}>
            Cancel
          </button>
        )}
      </div>

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
    </form>
  )
}
