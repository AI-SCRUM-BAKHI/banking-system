import axios, { AxiosError, AxiosHeaders } from 'axios'
import type { AxiosAdapter } from 'axios'
import { afterEach, expect, it, vi } from 'vitest'
import { apiClient } from './axios'
import { getTokens, setTokens } from '../lib/session'

const originalAdapter = apiClient.defaults.adapter
afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setTokens(null)
    vi.restoreAllMocks()
})
it('refreshes concurrent expired requests including /auth/me once and retries with the rotated bearer', async () => {
    setTokens({ accessToken: 'expired', refreshToken: 'old-refresh', tokenType: 'Bearer', expiresIn: 1 })
    const refresh = vi.spyOn(axios, 'post').mockResolvedValue({
        data: {
            success: true,
            data: { accessToken: 'fresh', refreshToken: 'rotated-refresh', tokenType: 'Bearer', expiresIn: 900 },
        },
    })
    const seen: string[] = []
    const adapter: AxiosAdapter = async (config) => {
        const auth = String(config.headers.Authorization)
        seen.push(auth)
        if (auth === 'Bearer expired')
            throw new AxiosError('Expired', 'ERR_BAD_REQUEST', config, undefined, {
                status: 401,
                statusText: 'Unauthorized',
                headers: new AxiosHeaders(),
                config,
                data: { success: false },
            })
        return {
            status: 200,
            statusText: 'OK',
            headers: new AxiosHeaders(),
            config,
            data: { success: true, data: { id: 'operator' } },
        }
    }
    apiClient.defaults.adapter = adapter
    await Promise.all([apiClient.get('/auth/me'), apiClient.get('/fraud-transactions')])
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(getTokens()?.refreshToken).toBe('rotated-refresh')
    expect(seen.filter((h) => h === 'Bearer fresh')).toHaveLength(2)
})
