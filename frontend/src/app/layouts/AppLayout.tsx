import { NavLink, Outlet } from 'react-router-dom'

import { useAuth } from '../../features/auth/context/auth-context'

const links = [
    ['Dashboard', '/dashboard'],
    ['Transactions', '/transactions'],
    ['Fraud alerts', '/fraud-alerts'],
    ['Investigations', '/investigations'],
]

export function AppLayout() {
    const { user, logout } = useAuth()

    return (
        <div className="min-h-screen bg-slate-50 text-slate-950">
            <header className="border-b border-slate-200 bg-white">
                <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
                    <div className="flex items-center gap-8">
                        <NavLink to="/" className="text-lg font-bold tracking-tight text-indigo-700">
                            BankShield
                        </NavLink>
                        <nav className="flex gap-1" aria-label="Primary navigation">
                            {links.map(([label, href]) => (
                                <NavLink
                                    key={href}
                                    to={href}
                                    className={({ isActive }) =>
                                        `rounded-lg px-3 py-2 text-sm font-medium transition ${isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-100'}`
                                    }
                                >
                                    {label}
                                </NavLink>
                            ))}
                        </nav>
                    </div>

                    <div className="flex items-center gap-4">
                        {user && (
                            <div className="flex items-center gap-3">
                                <div className="text-right">
                                    <p className="text-sm font-medium text-slate-900">{user.fullName || user.username}</p>
                                    <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                                        {user.role}
                                    </span>
                                </div>
                                <button
                                    type="button"
                                    onClick={() => logout()}
                                    className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 shadow-sm hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                                >
                                    Sign out
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </header>
            <main className="mx-auto max-w-7xl px-6 py-8">
                <Outlet />
            </main>
        </div>
    )
}
