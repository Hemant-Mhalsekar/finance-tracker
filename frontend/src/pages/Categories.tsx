import { useEffect, useState, type FormEvent } from 'react'
import {
  createCategory,
  deleteCategory,
  listCategories,
  updateCategory,
  type BudgetType,
  type Category,
} from '../api'
import { TYPE_LABELS } from '../labels'

const MAX_NAME_LENGTH = 50
const TYPES = Object.keys(TYPE_LABELS) as BudgetType[]
const GROUP_TITLES: Record<BudgetType, string> = {
  NEED: 'Needs',
  WANT: 'Wants',
  INVESTMENT: 'Investments',
}

const byName = (a: Category, b: Category) => a.name.localeCompare(b.name)

export default function Categories() {
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listCategories(true)
      .then((all) => {
        if (!cancelled) setCategories(all)
      })
      .catch((e: unknown) => {
        if (!cancelled) setLoadError(e instanceof Error ? e.message : 'Something went wrong')
      })
    return () => {
      cancelled = true
    }
  }, [])

  function replace(updated: Category) {
    setCategories((all) => all?.map((c) => (c.id === updated.id ? updated : c)) ?? null)
  }

  function remove(id: string) {
    setCategories((all) => all?.filter((c) => c.id !== id) ?? null)
  }

  if (!categories) {
    return (
      <section className="narrow-section">
        <h1>Categories</h1>
        {loadError ? (
          <p className="error" role="alert">
            {loadError}
          </p>
        ) : (
          <p className="muted">Loading...</p>
        )}
      </section>
    )
  }

  const active = categories.filter((c) => !c.archived)
  const archived = categories.filter((c) => c.archived).sort(byName)
  const protectedOnes = active.filter((c) => c.type === null).sort(byName)

  return (
    <section className="categories narrow-section">
      <h1>Categories</h1>
      <p className="muted">
        Changing a category&apos;s type affects new expenses only. Expenses already logged keep the type they had.
      </p>

      <AddCategoryForm onAdded={(c) => setCategories((all) => [...(all ?? []), c])} />

      {TYPES.map((type) => {
        const group = active.filter((c) => c.type === type).sort(byName)
        return (
          <article key={type} className="card">
            <h2>{GROUP_TITLES[type]}</h2>
            {group.length === 0 ? (
              <p className="muted">None</p>
            ) : (
              <ul className="category-list">
                {group.map((c) => (
                  <CategoryRow key={c.id} category={c} onChanged={replace} onDeleted={remove} />
                ))}
              </ul>
            )}
          </article>
        )
      })}

      {protectedOnes.length > 0 && (
        <article className="card">
          <h2>Pick the type each time</h2>
          <ul className="category-list">
            {protectedOnes.map((c) => (
              <li key={c.id} className="category-row">
                <span className="category-name">{c.name}</span>
                <span className="muted">Built in. You choose need, want or investment for each expense.</span>
              </li>
            ))}
          </ul>
        </article>
      )}

      {archived.length > 0 && (
        <article className="card">
          <h2>Archived</h2>
          <p className="muted">Hidden when adding expenses. Past expenses still count in reports.</p>
          <ul className="category-list">
            {archived.map((c) => (
              <CategoryRow key={c.id} category={c} onChanged={replace} onDeleted={remove} />
            ))}
          </ul>
        </article>
      )}
    </section>
  )
}

function AddCategoryForm({ onAdded }: { onAdded: (category: Category) => void }) {
  const [name, setName] = useState('')
  const [type, setType] = useState<BudgetType>('WANT')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return
    const trimmed = name.trim()
    if (trimmed === '') {
      setError('Enter a name')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      onAdded(await createCategory(trimmed, type))
      setName('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="card inline-form" onSubmit={handleSubmit} noValidate>
      <h2>New category</h2>
      <div className="inline-fields">
        <input
          aria-label="Category name"
          type="text"
          placeholder="Name"
          maxLength={MAX_NAME_LENGTH}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <select aria-label="Category type" value={type} onChange={(e) => setType(e.target.value as BudgetType)}>
          {TYPES.map((t) => (
            <option key={t} value={t}>
              {TYPE_LABELS[t]}
            </option>
          ))}
        </select>
        <button type="submit" disabled={submitting}>
          Add
        </button>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}

type RowProps = {
  category: Category
  onChanged: (category: Category) => void
  onDeleted: (id: string) => void
}

function CategoryRow({ category, onChanged, onDeleted }: RowProps) {
  const [renaming, setRenaming] = useState(false)
  const [name, setName] = useState(category.name)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  function saveName(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (trimmed === '') {
      setError('Enter a name')
      return
    }
    if (trimmed === category.name) {
      setRenaming(false)
      return
    }
    run(async () => {
      onChanged(await updateCategory(category.id, { name: trimmed }))
      setRenaming(false)
    })
  }

  return (
    <li className="category-row">
      {renaming ? (
        <form className="inline-fields" onSubmit={saveName} noValidate>
          <input
            aria-label={`New name for ${category.name}`}
            type="text"
            maxLength={MAX_NAME_LENGTH}
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <button type="submit" disabled={busy}>
            Save
          </button>
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => {
              setRenaming(false)
              setName(category.name)
              setError(null)
            }}
          >
            Cancel
          </button>
        </form>
      ) : (
        <span className="category-name">{category.name}</span>
      )}

      {!renaming && (
        <div className="row-actions">
          <select
            aria-label={`Type of ${category.name}`}
            value={category.type ?? ''}
            disabled={busy}
            onChange={(e) =>
              run(async () => onChanged(await updateCategory(category.id, { type: e.target.value as BudgetType })))
            }
          >
            {TYPES.map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </select>
          {confirmDelete ? (
            <>
              <button
                type="button"
                className="danger"
                disabled={busy}
                onClick={() =>
                  // On failure (usually: it still has expenses) the confirm stays open with the archive hint.
                  run(async () => {
                    await deleteCategory(category.id)
                    onDeleted(category.id)
                  })
                }
              >
                Confirm delete
              </button>
              <button type="button" className="secondary" disabled={busy} onClick={() => setConfirmDelete(false)}>
                Keep
              </button>
            </>
          ) : (
            <>
              <button type="button" className="secondary" disabled={busy} onClick={() => setRenaming(true)}>
                Rename
              </button>
              <button
                type="button"
                className="secondary"
                disabled={busy}
                onClick={() =>
                  run(async () => onChanged(await updateCategory(category.id, { archived: !category.archived })))
                }
              >
                {category.archived ? 'Restore' : 'Archive'}
              </button>
              <button type="button" className="secondary" disabled={busy} onClick={() => setConfirmDelete(true)}>
                Delete
              </button>
            </>
          )}
        </div>
      )}

      {error && (
        <p className="error row-error" role="alert">
          {error}
          {confirmDelete && ' Archive it instead to hide it from new expenses.'}
        </p>
      )}
    </li>
  )
}
