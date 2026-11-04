import { useOutletContext } from 'react-router-dom'
import type { CurrentUser } from '../types/auth.types'
import type { Mode } from '../types/fraud.types'
export interface Session {
    mode: Mode
    user: CurrentUser
}
export interface ConsoleContext {
    session: Session
    notify: (message: string) => void
    openCase: (id: string) => void
}
export function useConsole() {
    return useOutletContext<ConsoleContext>()
}
