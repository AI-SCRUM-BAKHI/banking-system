import { createBrowserRouter } from 'react-router-dom'

import { AppLayout } from './layouts/AppLayout'
import { DashboardPage } from '../features/dashboard/components/DashboardPage'
import { PlaceholderPage } from '../shared/components/PlaceholderPage'

export const router = createBrowserRouter([
    {
        path: '/',
        element: <AppLayout />,
        children: [
            { index: true, element: <DashboardPage /> },
            { path: 'dashboard', element: <DashboardPage /> },
            { path: 'transactions', element: <PlaceholderPage title="Transactions" /> },
            { path: 'fraud-alerts', element: <PlaceholderPage title="Fraud alerts" /> },
            { path: 'investigations', element: <PlaceholderPage title="Investigations" /> },
        ],
    },
])
