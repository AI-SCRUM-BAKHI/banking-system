import { createBrowserRouter } from 'react-router-dom'

import { AppLayout } from './layouts/AppLayout'
import { ProtectedRoute } from './components/ProtectedRoute'
import { LoginPage } from '../features/auth/components/LoginPage'
import { DashboardPage } from '../features/dashboard/components/DashboardPage'
import { PlaceholderPage } from '../shared/components/PlaceholderPage'

export const router = createBrowserRouter([
    {
        path: '/login',
        element: <LoginPage />,
    },
    {
        path: '/',
        element: (
            <ProtectedRoute>
                <AppLayout />
            </ProtectedRoute>
        ),
        children: [
            { index: true, element: <DashboardPage /> },
            { path: 'dashboard', element: <DashboardPage /> },
            { path: 'transactions', element: <PlaceholderPage title="Transactions" /> },
            { path: 'fraud-alerts', element: <PlaceholderPage title="Fraud alerts" /> },
            { path: 'investigations', element: <PlaceholderPage title="Investigations" /> },
        ],
    },
])
