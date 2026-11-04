import { lazy, Suspense, useCallback, useEffect, useRef, useState } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, ShieldCheck } from 'lucide-react'
import { AppProviders } from './components/providers/AppProviders'
import { ConsoleLayout } from './components/ConsoleLayout'
import { LandingPage } from './pages/LandingPage'
import { LoginPage } from './pages/LoginPage'
import { Loading } from './components/ui'
import { demoUser } from './lib/demo'
import { logout } from './services/auth.service'
import { getTokens, setTokens } from './lib/session'
import type { CurrentUser } from './types/auth.types'
import type { Session } from './lib/console-context'
const DashboardPage = lazy(() => import('./pages/DashboardPage').then((module) => ({ default: module.DashboardPage })))
const TransactionsPage = lazy(() =>
    import('./pages/TransactionsPage').then((module) => ({ default: module.TransactionsPage })),
)
const NetworkPage = lazy(() => import('./pages/NetworkPage').then((module) => ({ default: module.NetworkPage })))
const IpPage = lazy(() => import('./pages/IpPage').then((module) => ({ default: module.IpPage })))
const ApiPage = lazy(() => import('./pages/ApiPage').then((module) => ({ default: module.ApiPage })))
const UsersPage = lazy(() => import('./pages/UsersPage').then((module) => ({ default: module.UsersPage })))
function savedSession(): Session | null {
    try {
        const session = JSON.parse(sessionStorage.getItem('aegis.session') || 'null')
        return session?.user?.id && (session.mode === 'demo' || (session.mode === 'live' && getTokens()))
            ? session
            : null
    } catch {
        return null
    }
}
function Application() {
    const [session, setSession] = useState<Session | null>(savedSession)
    const [toast, setToast] = useState('')
    const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
    const client = useQueryClient()
    const navigate = useNavigate()
    const notify = useCallback((message: string) => {
        setToast(message)
        if (timer.current) clearTimeout(timer.current)
        timer.current = setTimeout(() => setToast(''), 4000)
    }, [])
    useEffect(
        () => () => {
            if (timer.current) clearTimeout(timer.current)
        },
        [],
    )
    const clearSession = useCallback(() => {
        setTokens(null)
        sessionStorage.removeItem('aegis.session')
        setSession(null)
        client.clear()
    }, [client])
    useEffect(() => {
        const expired = () => {
            clearSession()
            navigate('/login')
            notify('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
        }
        window.addEventListener('aegis:session-expired', expired)
        return () => window.removeEventListener('aegis:session-expired', expired)
    }, [clearSession, navigate, notify])
    function start(mode: Session['mode'], user: CurrentUser) {
        if (mode === 'demo') setTokens(null)
        client.clear()
        const next = { mode, user }
        setSession(next)
        sessionStorage.setItem('aegis.session', JSON.stringify(next))
        navigate('/console')
        window.scrollTo(0, 0)
    }
    async function end() {
        if (session?.mode === 'live') {
            try {
                await logout()
            } catch {
                notify('Đã xóa phiên trên trình duyệt; chưa xác nhận thu hồi token trên máy chủ.')
            }
        }
        clearSession()
        navigate('/login')
    }
    return (
        <>
            <a href="#main-content" className="skip-link">
                Đến nội dung chính
            </a>
            <Routes>
                <Route path="/" element={<LandingPage onDemo={() => start('demo', demoUser)} />} />
                <Route
                    path="/login"
                    element={
                        session ? (
                            <Navigate to="/console" replace />
                        ) : (
                            <LoginPage onDemo={() => start('demo', demoUser)} onLogin={(user) => start('live', user)} />
                        )
                    }
                />
                <Route
                    path="/console"
                    element={
                        session ? (
                            <ConsoleLayout session={session} onLogout={end} notify={notify} />
                        ) : (
                            <Navigate to="/login" replace />
                        )
                    }
                >
                    <Route index element={<DashboardPage />} />
                    <Route path="transactions" element={<TransactionsPage />} />
                    <Route path="network" element={<NetworkPage />} />
                    <Route path="ip" element={<IpPage />} />
                    <Route path="api" element={<ApiPage />} />
                    <Route path="users" element={<UsersPage />} />
                </Route>
                <Route
                    path="*"
                    element={
                        <main className="not-found" id="main-content">
                            <ShieldCheck size={43} />
                            <span className="eyebrow">404 / SIGNAL NOT FOUND</span>
                            <h1>Đường dẫn này chưa có kết nối.</h1>
                            <p>Quay về trang chủ để tiếp tục khám phá.</p>
                            <Link className="btn primary" to="/">
                                Về trang chủ
                            </Link>
                        </main>
                    }
                />
            </Routes>
            {toast && (
                <div className="toast" role="status">
                    <CheckCircle2 size={17} />
                    {toast}
                </div>
            )}
        </>
    )
}
export function App() {
    return (
        <AppProviders>
            <BrowserRouter>
                <Suspense fallback={<Loading text="Đang mở workspace…" />}>
                    <Application />
                </Suspense>
            </BrowserRouter>
        </AppProviders>
    )
}
