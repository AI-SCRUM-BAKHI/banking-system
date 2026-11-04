import type { TokenResponse } from '../types/auth.types'
const key = 'aegis.tokens'
export function getTokens(): TokenResponse | null {
    try {
        const value = JSON.parse(sessionStorage.getItem(key) || 'null')
        return value && typeof value.accessToken === 'string' && typeof value.refreshToken === 'string' ? value : null
    } catch {
        return null
    }
}
export function setTokens(tokens: TokenResponse | null) {
    if (tokens) sessionStorage.setItem(key, JSON.stringify(tokens))
    else sessionStorage.removeItem(key)
}
