const ACCESS_TOKEN_KEY = 'bankshield_access_token'
const REFRESH_TOKEN_KEY = 'bankshield_refresh_token'

export const tokenStorage = {
    getAccessToken(): string | null {
        return localStorage.getItem(ACCESS_TOKEN_KEY)
    },
    setAccessToken(token: string): void {
        localStorage.setItem(ACCESS_TOKEN_KEY, token)
    },
    getRefreshToken(): string | null {
        return localStorage.getItem(REFRESH_TOKEN_KEY)
    },
    setRefreshToken(token: string): void {
        localStorage.setItem(REFRESH_TOKEN_KEY, token)
    },
    setTokens(accessToken: string, refreshToken: string): void {
        localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
        localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    },
    clearTokens(): void {
        localStorage.removeItem(ACCESS_TOKEN_KEY)
        localStorage.removeItem(REFRESH_TOKEN_KEY)
    },
}

