export type UserRole = 'ADMIN' | 'BANK_USER'
export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface CurrentUser {
    id: string
    username: string
    fullName: string
    email: string
    role: UserRole
    status: UserStatus
}

export interface TokenResponse {
    accessToken: string
    refreshToken: string
    tokenType: string
    expiresIn: number
}

export interface LoginRequest {
    username: string
    password: string
}

export interface RefreshRequest {
    refreshToken: string
}

export interface ApiResponse<T> {
    success: boolean
    data: T
    message?: string
    timestamp: string
    requestId?: string
}

export interface ApiErrorResponse {
    success: boolean
    error: {
        code: string
        message: string
        details?: Record<string, unknown>
    }
    statusCode: number
    timestamp: string
    path: string
    requestId?: string
}

