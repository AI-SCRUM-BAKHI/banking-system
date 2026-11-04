import { lazy, Suspense } from 'react'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'

import { AppLayout } from '../components/layout/AppLayout'

const LoginPage = lazy(() => import('../pages/Login').then((module) => ({ default: module.LoginPage })))
const DashboardPage = lazy(() => import('../pages/Dashboard').then((module) => ({ default: module.DashboardPage })))
const PlaceholderPage = lazy(() =>
    import('../pages/PlaceholderPage').then((module) => ({ default: module.PlaceholderPage })),
)

function PageFallback() {
    return (
        <div className="grid min-h-dvh place-items-center bg-canvas text-sm text-muted" role="status">
            Đang tải giao diện…
        </div>
    )
}

function lazyPage(element: React.ReactNode) {
    return <Suspense fallback={<PageFallback />}>{element}</Suspense>
}

const router = createBrowserRouter([
    { path: '/', element: <Navigate to="/dashboard" replace /> },
    { path: '/login', element: lazyPage(<LoginPage />) },
    {
        element: <AppLayout />,
        children: [
            { path: '/dashboard', element: lazyPage(<DashboardPage />) },
            { path: '/transactions', element: lazyPage(<PlaceholderPage title="Giao dịch" />) },
            { path: '/fraud-alerts', element: lazyPage(<PlaceholderPage title="Cảnh báo fraud" />) },
            { path: '/investigations', element: lazyPage(<PlaceholderPage title="Điều tra" />) },
        ],
    },
])

export function AppRouter() {
    return <RouterProvider router={router} />
}
