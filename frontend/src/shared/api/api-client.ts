import axios, { type InternalAxiosRequestConfig } from 'axios'

import { env } from '../../config/env'
import { tokenStorage } from '../../features/auth/utils/tokenStorage'

export const apiClient = axios.create({
    baseURL: env.VITE_API_BASE_URL,
    headers: { 'Content-Type': 'application/json' },
})

const publicAuthPaths = ['/auth/login', '/auth/refresh', '/auth/logout']

let isRefreshing = false
let failedQueue: Array<{
    resolve: (token: string) => void
    reject: (error: unknown) => void
}> = []

const processQueue = (error: unknown, token: string | null = null) => {
    failedQueue.forEach((promise) => {
        if (token) {
            promise.resolve(token)
        } else {
            promise.reject(error)
        }
    })
    failedQueue = []
}

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const token = tokenStorage.getAccessToken()
    const requestUrl = config.url || ''
    const isPublicAuthRequest = publicAuthPaths.some((path) => requestUrl.includes(path))
    if (token && config.headers && !isPublicAuthRequest) {
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
})

apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config
        if (!error.response || error.response.status !== 401 || originalRequest?._retry) {
            return Promise.reject(error)
        }

        const requestUrl = originalRequest.url || ''
        if (requestUrl.includes('/auth/login') || requestUrl.includes('/auth/refresh')) {
            return Promise.reject(error)
        }

        const refreshToken = tokenStorage.getRefreshToken()
        if (!refreshToken) {
            tokenStorage.clearTokens()
            window.dispatchEvent(new Event('auth:unauthorized'))
            return Promise.reject(error)
        }

        if (isRefreshing) {
            return new Promise((resolve, reject) => {
                failedQueue.push({ resolve, reject })
            })
                .then((newToken) => {
                    originalRequest.headers.Authorization = `Bearer ${newToken}`
                    return apiClient(originalRequest)
                })
                .catch((err) => Promise.reject(err))
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
            const { data } = await axios.post(`${env.VITE_API_BASE_URL}/auth/refresh`, {
                refreshToken,
            })
            const newTokens = data.data
            tokenStorage.setTokens(newTokens.accessToken, newTokens.refreshToken)
            processQueue(null, newTokens.accessToken)
            originalRequest.headers.Authorization = `Bearer ${newTokens.accessToken}`
            return apiClient(originalRequest)
        } catch (refreshError) {
            processQueue(refreshError, null)
            tokenStorage.clearTokens()
            window.dispatchEvent(new Event('auth:unauthorized'))
            return Promise.reject(refreshError)
        } finally {
            isRefreshing = false
        }
    }
)
