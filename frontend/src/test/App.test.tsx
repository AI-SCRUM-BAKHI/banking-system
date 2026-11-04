import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { DashboardPage } from '../pages/Dashboard'

describe('DashboardPage', () => {
    it('renders the operations overview', () => {
        render(
            <MemoryRouter>
                <DashboardPage />
            </MemoryRouter>,
        )

        expect(screen.getByRole('heading', { name: /fraud detection dashboard/i })).toBeInTheDocument()
    })
})
