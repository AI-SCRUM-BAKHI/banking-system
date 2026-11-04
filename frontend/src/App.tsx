import { StrictMode } from 'react'

import { AppProviders } from './components/providers/AppProviders'
import { AppRouter } from './routes/AppRouter'

export function App() {
    return (
        <StrictMode>
            <AppProviders>
                <AppRouter />
            </AppProviders>
        </StrictMode>
    )
}
