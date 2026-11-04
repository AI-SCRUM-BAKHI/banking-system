import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { App } from './App'
import { getTokens, setTokens } from './lib/session'
import { login } from './services/auth.service'

const { admin, tokens } = vi.hoisted(() => ({
    admin: {
        id: 'admin-1',
        username: 'admin',
        fullName: 'Bank Admin',
        email: 'admin@example.com',
        role: 'ADMIN',
        status: 'ACTIVE',
    },
    tokens: { accessToken: 'live-access', refreshToken: 'live-refresh', tokenType: 'Bearer', expiresIn: 900 },
}))

vi.mock('./services/auth.service', () => ({
    login: vi.fn().mockResolvedValue(tokens),
    register: vi.fn(),
    currentUser: vi.fn().mockResolvedValue(admin),
    logout: vi.fn(),
}))
vi.mock('./pages/DashboardPage', () => ({ DashboardPage: () => <h1>Workspace dashboard</h1> }))

afterEach(() => {
    cleanup()
    sessionStorage.clear()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
})

it('opens real login after an admin explores demo and returns to the admin workspace after signing in', async () => {
    vi.stubGlobal(
        'IntersectionObserver',
        class {
            observe() {}
            disconnect() {}
        },
    )
    vi.stubGlobal('scrollTo', vi.fn())
    sessionStorage.setItem('aegis.session', JSON.stringify({ mode: 'live', user: admin }))
    setTokens(tokens)
    window.history.replaceState({}, '', '/')
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: 'Khám phá demo' }))
    await screen.findByRole('heading', { name: 'Workspace dashboard' })
    expect(JSON.parse(sessionStorage.getItem('aegis.session')!).mode).toBe('demo')
    expect(getTokens()).toBeNull()

    await user.click(screen.getByRole('link', { name: /AEGIS.*FRAUD INTELLIGENCE/ }))
    await user.click(screen.getByRole('link', { name: 'Đăng nhập' }))
    await screen.findByRole('button', { name: 'Đăng nhập workspace' })
    expect(window.location.pathname).toBe('/login')

    await user.type(screen.getByLabelText('Tên đăng nhập'), 'admin')
    await user.type(screen.getByLabelText('Mật khẩu', { exact: true }), 'example-password')
    await user.click(screen.getByRole('button', { name: 'Đăng nhập workspace' }))
    await screen.findByRole('heading', { name: 'Workspace dashboard' })
    expect(login).toHaveBeenCalledWith({ username: 'admin', password: 'example-password' })
    expect(JSON.parse(sessionStorage.getItem('aegis.session')!)).toEqual({ mode: 'live', user: admin })
    expect(getTokens()?.accessToken).toBe('live-access')
})
