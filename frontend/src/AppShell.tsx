import { useEffect, useRef } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'

type Props = {
  email: string
  onLogout: () => void
  loggingOut: boolean
}

export default function AppShell({ email, onLogout, loggingOut }: Props) {
  const navRef = useRef<HTMLElement>(null)
  const { pathname } = useLocation()

  // On a phone the nav scrolls sideways; keep the current page's link visible.
  useEffect(() => {
    navRef.current?.querySelector('a.active')?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  }, [pathname])

  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">Finance Tracker</span>
        <nav aria-label="Main" ref={navRef}>
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/add">Add expense</NavLink>
          <NavLink to="/expenses">Expenses</NavLink>
          <NavLink to="/categories">Categories</NavLink>
          <NavLink to="/settings">Settings</NavLink>
        </nav>
        <div className="account">
          <span className="muted" title={email}>
            {email}
          </span>
          <button type="button" className="secondary" onClick={onLogout} disabled={loggingOut}>
            Log out
          </button>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
