export interface LoginRequest {
    username: string
    password: string
}

export interface RegisterRequest {
    username: string
    password: string
    fullName: string
    email: string
}

export interface TokenResponse {
    accessToken: string
    refreshToken: string
    tokenType: string
    expiresIn: number
}

export interface CurrentUser {
    id: string
    username: string
    fullName: string
    email: string
    role: 'ADMIN' | 'BANK_USER'
    status: 'ACTIVE' | 'DISABLED'
}
