import { NavLink, Outlet } from 'react-router-dom'

const links = [
    ['Dashboard', '/dashboard'],
    ['Transactions', '/transactions'],
    ['Fraud alerts', '/fraud-alerts'],
    ['Investigations', '/investigations'],
]

export function AppLayout() {
    return (
        <div className="min-h-screen bg-slate-50 text-slate-950">
            <header className="border-b border-slate-200 bg-white">
                <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
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
            </header>
            <main className="mx-auto max-w-7xl px-6 py-8">
                <Outlet />
            </main>
        </div>
    )
}
