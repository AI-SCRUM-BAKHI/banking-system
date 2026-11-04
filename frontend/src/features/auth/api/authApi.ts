import { apiClient } from '../../../shared/api/api-client'
import type { ApiResponse, CurrentUser, LoginRequest, TokenResponse } from '../types'

export const authApi = {
    async login(credentials: LoginRequest): Promise<TokenResponse> {
        const response = await apiClient.post<ApiResponse<TokenResponse>>('/auth/login', credentials)
        return response.data.data
    },

    async refresh(refreshToken: string): Promise<TokenResponse> {
        const response = await apiClient.post<ApiResponse<TokenResponse>>('/auth/refresh', { refreshToken })
        return response.data.data
    },

    async logout(refreshToken: string): Promise<void> {
        await apiClient.post('/auth/logout', { refreshToken })
    },

    async me(): Promise<CurrentUser> {
        const response = await apiClient.get<ApiResponse<CurrentUser>>('/auth/me')
        return response.data.data
    },
}

