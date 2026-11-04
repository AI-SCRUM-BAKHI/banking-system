import { useState, type FormEvent } from 'react'
import axios from 'axios'
import { Link, useNavigate } from 'react-router-dom'

import { login } from '../services/auth.service'

export function LoginPage() {
    const navigate = useNavigate()
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [isSubmitting, setIsSubmitting] = useState(false)

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        setError('')
        setIsSubmitting(true)

        try {
            const tokens = await login({ username, password })
            sessionStorage.setItem('bankshield.accessToken', tokens.accessToken)
            sessionStorage.setItem('bankshield.refreshToken', tokens.refreshToken)
            navigate('/dashboard', { replace: true })
        } catch (requestError) {
            const status = axios.isAxiosError(requestError) ? requestError.response?.status : undefined
            setError(
                status === 401
                    ? 'Tên đăng nhập hoặc mật khẩu chưa chính xác.'
                    : 'Chưa thể đăng nhập. Vui lòng thử lại sau.',
            )
        } finally {
            setIsSubmitting(false)
        }
    }

    return (
        <main className="auth-shell grid min-h-dvh bg-canvas font-sans text-copy lg:grid-cols-[1fr_0.9fr]">
            <section className="relative hidden overflow-hidden border-r border-line lg:flex lg:flex-col lg:justify-between lg:p-12 xl:p-16">
                <div
                    className="absolute -left-32 top-1/4 size-[480px] rounded-full bg-accent/[0.06] blur-[100px]"
                    aria-hidden="true"
                />
                <Link to="/dashboard" className="relative flex items-center gap-3 text-copy no-underline">
                    <span className="grid size-10 place-items-center rounded-md border border-accent/40 bg-accent/10 text-accent-bright">
                        <svg className="size-6" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                            <path
                                d="M12 2.8 20 6v5.3c0 5.1-3.4 8.2-8 10-4.6-1.8-8-4.9-8-10V6l8-3.2Z"
                                stroke="currentColor"
                                strokeWidth="1.6"
                            />
                            <path d="M8 12h8M12 8v8" stroke="currentColor" strokeWidth="1.6" />
                        </svg>
                    </span>
                    <span>
                        <span className="block text-sm font-bold tracking-[0.12em]">BANKSHIELD</span>
                        <span className="text-[10px] tracking-[0.08em] text-dim">FRAUD INTELLIGENCE</span>
                    </span>
                </Link>
                <div className="relative max-w-xl">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-accent">
                        Không gian vận hành
                    </p>
                    <h1 className="mt-6 text-4xl font-bold leading-tight tracking-[-0.04em] xl:text-5xl">
                        Điều tra giao dịch với đầy đủ ngữ cảnh.
                    </h1>
                    <p className="mt-5 text-sm leading-7 text-muted">
                        Đăng nhập để xem hàng đợi cảnh báo, thông tin giải thích và liên kết giao dịch.
                    </p>
                    <div className="mt-10 flex items-center gap-3 rounded-lg border border-line bg-panel/80 p-4">
                        <span className="size-2 rounded-full bg-signal" />
                        <span className="text-xs text-muted">Tài khoản được cấp bởi quản trị viên.</span>
                    </div>
                </div>
                <p className="relative text-[11px] text-dim">BankShield · Fraud Intelligence</p>
            </section>
            <section className="flex min-h-dvh items-center justify-center px-5 py-12 sm:px-8">
                <div className="w-full max-w-[420px]">
                    <Link
                        to="/dashboard"
                        className="mb-10 inline-flex min-h-11 items-center gap-2 text-xs text-muted no-underline hover:text-copy lg:hidden"
                    >
                        <span aria-hidden="true">←</span> Về trang tổng quan
                    </Link>
                    <div className="mb-9">
                        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-accent">
                            Chào mừng trở lại
                        </p>
                        <h2 className="mt-3 text-3xl font-bold tracking-[-0.04em]">Đăng nhập</h2>
                        <p className="mt-3 text-sm leading-6 text-muted">
                            Sử dụng thông tin tài khoản do quản trị viên cung cấp.
                        </p>
                    </div>
                    <form className="space-y-5" onSubmit={handleSubmit}>
                        <div>
                            <label className="mb-2 block text-xs font-medium text-muted" htmlFor="username">
                                Tên đăng nhập
                            </label>
                            <input
                                autoComplete="username"
                                className="min-h-12 w-full rounded-md border border-line-strong bg-panel px-4 text-base text-copy placeholder:text-dim focus:border-accent"
                                id="username"
                                name="username"
                                onChange={(event) => setUsername(event.target.value)}
                                required
                                value={username}
                            />
                        </div>
                        <div>
                            <label className="mb-2 block text-xs font-medium text-muted" htmlFor="password">
                                Mật khẩu
                            </label>
                            <input
                                autoComplete="current-password"
                                className="min-h-12 w-full rounded-md border border-line-strong bg-panel px-4 text-base text-copy placeholder:text-dim focus:border-accent"
                                id="password"
                                name="password"
                                onChange={(event) => setPassword(event.target.value)}
                                required
                                type="password"
                                value={password}
                            />
                        </div>
                        {error && (
                            <p
                                className="rounded-md border border-risk-critical/30 bg-risk-critical/10 px-4 py-3 text-xs leading-5 text-risk-critical"
                                role="alert"
                            >
                                {error}
                            </p>
                        )}
                        <button
                            className="inline-flex min-h-12 w-full items-center justify-center rounded-md bg-accent px-5 text-sm font-bold text-[#14200a] transition-colors hover:bg-accent-bright disabled:cursor-not-allowed disabled:opacity-60"
                            disabled={isSubmitting}
                            type="submit"
                        >
                            {isSubmitting ? 'Đang xác thực…' : 'Đăng nhập'}
                        </button>
                    </form>
                    <p className="mt-7 text-center text-[11px] leading-6 text-dim">
                        Chưa có tài khoản? Liên hệ quản trị viên của tổ chức để được cấp quyền.
                    </p>
                    <Link
                        to="/dashboard"
                        className="mt-8 hidden min-h-11 items-center justify-center text-xs text-muted no-underline hover:text-copy lg:flex"
                    >
                        ← Về trang tổng quan
                    </Link>
                </div>
            </section>
        </main>
    )
}
