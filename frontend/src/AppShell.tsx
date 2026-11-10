import { NavLink, Outlet } from 'react-router'

type Props = {
  email: string
  onLogout: () => void
  loggingOut: boolean
}

export default function AppShell({ email, onLogout, loggingOut }: Props) {
  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">Finance Tracker</span>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/add">Add expense</NavLink>
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
