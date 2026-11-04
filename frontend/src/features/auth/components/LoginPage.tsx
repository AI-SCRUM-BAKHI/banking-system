import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import axios from 'axios'

import { useAuth } from '../context/auth-context'

export function LoginPage() {
    const { login, isAuthenticated, isLoading } = useAuth()
    const navigate = useNavigate()
    const location = useLocation()

    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [showPassword, setShowPassword] = useState(false)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)
    const [isSubmitting, setIsSubmitting] = useState(false)

    const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/dashboard'

    if (!isLoading && isAuthenticated) {
        return <Navigate to={from} replace />
    }

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault()
        setErrorMessage(null)

        if (!username.trim() || !password) {
            setErrorMessage('Please enter both username and password.')
            return
        }

        setIsSubmitting(true)
        try {
            await login({ username: username.trim(), password })
            navigate(from, { replace: true })
        } catch (error: unknown) {
            if (axios.isAxiosError(error)) {
                const apiError = error.response?.data?.error
                if (apiError?.message) {
                    setErrorMessage(apiError.message)
                } else if (error.response?.status === 401) {
                    setErrorMessage('Invalid username or password.')
                } else if (!error.response) {
                    setErrorMessage('Unable to connect to authentication server. Please check your network.')
                } else {
                    setErrorMessage('An unexpected error occurred during login. Please try again.')
                }
            } else {
                setErrorMessage('An unexpected error occurred. Please try again.')
            }
        } finally {
            setIsSubmitting(false)
        }
    }

    return (
        <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-12 sm:px-6 lg:px-8">
            <div className="w-full max-w-md space-y-8 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
                <div>
                    <div className="inline-block rounded-xl bg-indigo-50 px-3 py-1.5 text-xs font-semibold uppercase tracking-wider text-indigo-700">
                        BankShield Security
                    </div>
                    <h2 className="mt-3 text-2xl font-bold tracking-tight text-slate-900">
                        Sign in to Fraud Operations
                    </h2>
                    <p className="mt-2 text-sm text-slate-500">
                        Enter your credentials to access the transaction monitoring platform.
                    </p>
                </div>

                {errorMessage && (
                    <div
                        className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700"
                        role="alert"
                    >
                        <div className="flex items-center gap-2">
                            <svg className="h-5 w-5 shrink-0 text-red-500" viewBox="0 0 20 20" fill="currentColor">
                                <path
                                    fillRule="evenodd"
                                    d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z"
                                    clipRule="evenodd"
                                />
                            </svg>
                            <span>{errorMessage}</span>
                        </div>
                    </div>
                )}

                <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
                    <div>
                        <label htmlFor="username" className="block text-sm font-medium text-slate-700">
                            Username
                        </label>
                        <div className="mt-1">
                            <input
                                id="username"
                                name="username"
                                type="text"
                                autoComplete="username"
                                required
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                placeholder="e.g. admin or bank_operator"
                                className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 placeholder-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 sm:text-sm"
                            />
                        </div>
                    </div>

                    <div>
                        <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                            Password
                        </label>
                        <div className="relative mt-1">
                            <input
                                id="password"
                                name="password"
                                type={showPassword ? 'text' : 'password'}
                                autoComplete="current-password"
                                required
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                placeholder="••••••••••••"
                                className="block w-full rounded-lg border border-slate-300 px-3 py-2 pr-10 text-slate-900 placeholder-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 sm:text-sm"
                            />
                            <button
                                type="button"
                                onClick={() => setShowPassword(!showPassword)}
                                className="absolute inset-y-0 right-0 flex items-center pr-3 text-xs text-slate-500 hover:text-slate-700"
                            >
                                {showPassword ? 'Hide' : 'Show'}
                            </button>
                        </div>
                    </div>

                    <div>
                        <button
                            type="submit"
                            disabled={isSubmitting}
                            className="flex w-full justify-center rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {isSubmitting ? (
                                <span className="flex items-center gap-2">
                                    <svg
                                        className="h-4 w-4 animate-spin text-white"
                                        xmlns="http://www.w3.org/2000/svg"
                                        fill="none"
                                        viewBox="0 0 24 24"
                                    >
                                        <circle
                                            className="opacity-25"
                                            cx="12"
                                            cy="12"
                                            r="10"
                                            stroke="currentColor"
                                            strokeWidth="4"
                                        />
                                        <path
                                            className="opacity-75"
                                            fill="currentColor"
                                            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                                        />
                                    </svg>
                                    Authenticating...
                                </span>
                            ) : (
                                'Sign in'
                            )}
                        </button>
                    </div>
                </form>

                <div className="border-t border-slate-100 pt-4 text-center text-xs text-slate-400">
                    Protected by BankShield Multi-factor Risk Engine
                </div>
            </div>
        </div>
    )
}

