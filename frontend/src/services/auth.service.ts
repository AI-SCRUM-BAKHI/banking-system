import { apiClient } from '../api/axios'
import type { ApiResponse } from '../types/api.types'
import type { CurrentUser, LoginRequest, RegisterRequest, TokenResponse } from '../types/auth.types'
import { getTokens, setTokens } from '../lib/session'

export async function register(data: RegisterRequest): Promise<TokenResponse> {
    const response = await apiClient.post<ApiResponse<TokenResponse>>('/auth/register', data)
    return response.data.data
}

export async function login(credentials: LoginRequest): Promise<TokenResponse> {
    const response = await apiClient.post<ApiResponse<TokenResponse>>('/auth/login', credentials)
    return response.data.data
}

export async function currentUser(): Promise<CurrentUser> {
    return (await apiClient.get<ApiResponse<CurrentUser>>('/auth/me')).data.data
}

export async function logout(): Promise<void> {
    const tokens = getTokens()
    try {
        if (tokens) await apiClient.post('/auth/logout', { refreshToken: tokens.refreshToken })
    } finally {
        setTokens(null)
    }
}
