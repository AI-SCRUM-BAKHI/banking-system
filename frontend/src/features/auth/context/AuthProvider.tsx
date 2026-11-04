import { useCallback, useEffect, useState, type PropsWithChildren } from 'react'

import { authApi } from '../api/authApi'
import type { CurrentUser, LoginRequest } from '../types'
import { tokenStorage } from '../utils/tokenStorage'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }: PropsWithChildren) {
    const [user, setUser] = useState<CurrentUser | null>(null)
    const [isLoading, setIsLoading] = useState<boolean>(true)

    const logout = useCallback(async () => {
        const refreshToken = tokenStorage.getRefreshToken()
        if (refreshToken) {
            try {
                await authApi.logout(refreshToken)
            } catch {
                // Ignore failure on logout to always clean local session
            }
        }
        tokenStorage.clearTokens()
        setUser(null)
    }, [])

    const login = useCallback(async (credentials: LoginRequest) => {
        const tokenResponse = await authApi.login(credentials)
        tokenStorage.setTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
        try {
            const profile = await authApi.me()
            setUser(profile)
        } catch (error) {
            tokenStorage.clearTokens()
            throw error
        }
    }, [])

    useEffect(() => {
        let isMounted = true

        const initializeAuth = async () => {
            const accessToken = tokenStorage.getAccessToken()
            const refreshToken = tokenStorage.getRefreshToken()

            if (!accessToken && !refreshToken) {
                if (isMounted) setIsLoading(false)
                return
            }

            try {
                const profile = await authApi.me()
                if (isMounted) setUser(profile)
            } catch {
                if (isMounted) {
                    tokenStorage.clearTokens()
                    setUser(null)
                }
            } finally {
                if (isMounted) setIsLoading(false)
            }
        }

        initializeAuth()

        const handleUnauthorized = () => {
            logout()
        }

        window.addEventListener('auth:unauthorized', handleUnauthorized)
        return () => {
            isMounted = false
            window.removeEventListener('auth:unauthorized', handleUnauthorized)
        }
    }, [logout])

    return (
        <AuthContext.Provider
            value={{
                user,
                isAuthenticated: !!user,
                isLoading,
                login,
                logout,
            }}
        >
            {children}
        </AuthContext.Provider>
    )
}
