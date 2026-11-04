import { apiClient } from '../api/axios'
import type { ApiResponse } from '../types/api.types'
import type { LoginRequest, TokenResponse } from '../types/auth.types'

export async function login(credentials: LoginRequest): Promise<TokenResponse> {
    const response = await apiClient.post<ApiResponse<TokenResponse>>('/auth/login', credentials)
    return response.data.data
}
