export interface LoginRequest {
    username: string
    password: string
}

export interface TokenResponse {
    accessToken: string
    refreshToken: string
    tokenType: string
    expiresIn: number
}
