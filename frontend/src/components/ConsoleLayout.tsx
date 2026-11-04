import { useCallback, useState } from 'react'
import { NavLink, Link, Outlet, useLocation } from 'react-router-dom'
import {
    Activity,
    ArrowUpRight,
    BookOpen,
    Building2,
    ChevronDown,
    ChevronsUpDown,
    Code2,
    Globe,
    LayoutDashboard,
    LogOut,
    Menu,
    Network,
    Search,
    ShieldCheck,
    Users,
    X,
} from 'lucide-react'
import { Brand, Pill } from './ui'
import type { Session } from '../lib/console-context'
import { CaseDrawer } from './CaseDrawer'
const items = [
    { path: '/console', label: 'Tổng quan', Icon: LayoutDashboard, end: true },
    { path: '/console/transactions', label: 'Hồ sơ cảnh báo', Icon: Activity },
    { path: '/console/network', label: 'Liên kết & dòng tiền', Icon: Network },
    { path: '/console/ip', label: 'IP Intelligence', Icon: Globe },
    { path: '/console/api', label: 'API & tích hợp', Icon: Code2 },
]
export function ConsoleLayout({
    session,
    onLogout,
    notify,
}: {
    session: Session
    onLogout: () => void
    notify: (message: string) => void
}) {
    const [menu, setMenu] = useState(false)
    const [caseId, setCaseId] = useState('')
    const location = useLocation()
    const title =
        items.find((i) => (i.end ? location.pathname === i.path : location.pathname.startsWith(i.path)))?.label ||
        (location.pathname.endsWith('users') ? 'Thành viên' : 'Workspace')
    const close = useCallback(() => setCaseId(''), [])
    return (
        <div className="console-shell">
            <button
                className={'sidebar-scrim ' + (menu ? 'visible' : '')}
                aria-label="Đóng menu"
                onClick={() => setMenu(false)}
            />
            <aside className={'sidebar ' + (menu ? 'open' : '')}>
                <div className="sidebar-brand">
                    <Link to="/">
                        <Brand light />
                    </Link>
                    <button className="icon-button mobile-close" aria-label="Đóng menu" onClick={() => setMenu(false)}>
                        <X size={18} />
                    </button>
                </div>
                <div className="workspace-selector">
                    <span>
                        <Building2 size={20} />
                    </span>
                    <div>
                        <b>{session.mode === 'demo' ? 'Horizon Bank' : 'Banking workspace'}</b>
                        <small>{session.mode === 'demo' ? 'Demo organization' : 'Connected organization'}</small>
                    </div>
                    <ChevronsUpDown size={13} />
                </div>
                <div className="sidebar-label">WORKSPACE</div>
                <nav aria-label="Workspace">
                    {items.map(({ path, label, Icon, end }) => (
                        <NavLink key={path} to={path} end={end} onClick={() => setMenu(false)}>
                            <Icon size={18} />
                            <span>{label}</span>
                            {path.endsWith('/ip') && <span className="tiny-label">DEMO</span>}
                        </NavLink>
                    ))}
                    {session.user.role === 'ADMIN' && (
                        <>
                            <div className="sidebar-label admin-label">QUẢN TRỊ</div>
                            <NavLink to="/console/users" onClick={() => setMenu(false)}>
                                <Users size={18} />
                                <span>Thành viên</span>
                            </NavLink>
                        </>
                    )}
                </nav>
                <div className="sidebar-lower">
                    <div className="sidebar-help">
                        <div className="help-icon">
                            <BookOpen size={19} />
                        </div>
                        <b>Mọi kết nối đều có ý nghĩa.</b>
                        <p>Tài liệu API và hướng dẫn tích hợp dành cho đội kỹ thuật.</p>
                        <Link to="/console/api" onClick={() => setMenu(false)}>
                            Khám phá tài liệu <ArrowUpRight size={14} />
                        </Link>
                    </div>
                    <div className="environment">
                        <span className={'env-dot ' + session.mode} />
                        <span>{session.mode === 'demo' ? 'DEMO ENVIRONMENT' : 'LIVE BACKEND'}</span>
                        <ShieldCheck size={13} />
                    </div>
                    <div className="sidebar-user">
                        <span className="avatar">
                            {session.user.fullName
                                .split(' ')
                                .slice(-2)
                                .map((w) => w[0])
                                .join('')}
                        </span>
                        <div>
                            <b>{session.user.fullName}</b>
                            <small>{session.user.role === 'ADMIN' ? 'Administrator' : 'Bank operator'}</small>
                        </div>
                        <button className="icon-button" aria-label="Đăng xuất" onClick={onLogout}>
                            <LogOut size={16} />
                        </button>
                    </div>
                </div>
            </aside>
            <div className="console-main">
                <header className="console-header">
                    <button className="icon-button mobile-menu" aria-label="Mở menu" onClick={() => setMenu(!menu)}>
                        <Menu size={20} />
                    </button>
                    <div className="breadcrumb">
                        Workspace <span>/</span> <b>{title}</b>
                    </div>
                    <div className="header-right">
                        <Link to="/console/transactions" className="header-search" aria-label="Tìm hồ sơ">
                            <Search size={17} />
                            <span>Tìm hồ sơ…</span>
                            <kbd>⌕</kbd>
                        </Link>
                        <span className="header-divider" />
                        <Pill tone={session.mode === 'demo' ? 'amber' : 'green'}>
                            <i />
                            {session.mode === 'demo' ? 'Dữ liệu mô phỏng' : 'Backend connected'}
                        </Pill>
                        <span className="avatar">
                            {session.user.fullName.split(' ').slice(-1)[0].slice(0, 2).toUpperCase()}
                        </span>
                        <ChevronDown size={13} />
                    </div>
                </header>
                <main id="main-content" className="console-content">
                    <div key={location.pathname} className="route-enter">
                        <Outlet context={{ session, notify, openCase: setCaseId }} />
                    </div>
                    <footer className="console-footer">
                        <span>
                            <ShieldCheck size={13} /> AEGIS Intelligence Platform
                        </span>
                        <span>
                            {session.mode === 'demo'
                                ? 'Dữ liệu tổng hợp · Snapshot 29/10–04/11/2026'
                                : 'Dữ liệu từ API backend đã cấu hình'}
                            <span className="footer-dot" />
                            UTC+7
                        </span>
                    </footer>
                </main>
            </div>
            {caseId && <CaseDrawer id={caseId} session={session} onClose={close} notify={notify} />}
        </div>
    )
}
