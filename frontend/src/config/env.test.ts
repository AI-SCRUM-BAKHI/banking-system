import { afterEach, expect, it, vi } from 'vitest'

afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
})

it('accepts a same-origin API base for the deployed frontend', async () => {
    vi.stubEnv('VITE_API_BASE_URL', '/api/v1')
    vi.resetModules()
    const { env } = await import('./env')
    expect(env.VITE_API_BASE_URL).toBe('/api/v1')
})
