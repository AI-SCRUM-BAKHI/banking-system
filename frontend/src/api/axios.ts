import axios from 'axios'

import { env } from '../config/env'
import { getTokens, setTokens } from '../lib/session'
import type { ApiResponse } from '../types/api.types'
import type { TokenResponse } from '../types/auth.types'

// Local requests go through Vite so any dev port can use the backend's existing CORS policy.
export const apiBaseUrl = import.meta.env.DEV ? '/api/v1' : env.VITE_API_BASE_URL
export const apiClient = axios.create({
    baseURL: apiBaseUrl,
    headers: { 'Content-Type': 'application/json' },
    timeout: 15_000,
})

apiClient.interceptors.request.use((config) => {
    const tokens = getTokens()
    if (tokens && !['/auth/login', '/auth/refresh', '/auth/register'].includes(config.url || ''))
        config.headers.Authorization = `Bearer ${tokens.accessToken}`
    return config
})

let refreshing: Promise<TokenResponse> | null = null
apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
        const config = error.config
        const tokens = getTokens()
        if (
            error.response?.status !== 401 ||
            !config ||
            config._retried ||
            !tokens ||
            ['/auth/login', '/auth/refresh', '/auth/logout', '/auth/register'].includes(config.url || '')
        )
            return Promise.reject(error)
        config._retried = true
        if (!refreshing) {
            refreshing = axios
                .post<ApiResponse<TokenResponse>>(
                    `${apiBaseUrl}/auth/refresh`,
                    { refreshToken: tokens.refreshToken },
                    { timeout: 15_000 },
                )
                .then((response) => {
                    setTokens(response.data.data)
                    return response.data.data
                })
                .finally(() => {
                    refreshing = null
                })
        }
        try {
            const next = await refreshing
            config.headers.Authorization = `Bearer ${next.accessToken}`
            return apiClient(config)
        } catch (refreshError) {
            setTokens(null)
            window.dispatchEvent(new Event('aegis:session-expired'))
            return Promise.reject(refreshError)
        }
    },
)

export function errorMessage(error: unknown): string {
    if (axios.isAxiosError(error))
        return (
            error.response?.data?.error?.message ||
            (error.response
                ? `Yêu cầu không thành công (${error.response.status}).`
                : 'Không kết nối được backend. Kiểm tra API URL và trạng thái máy chủ.')
        )
    return error instanceof Error ? error.message : 'Đã xảy ra lỗi. Vui lòng thử lại.'
}
