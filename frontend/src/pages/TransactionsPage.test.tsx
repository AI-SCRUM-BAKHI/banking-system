import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { TransactionsPage } from './TransactionsPage'
import { useConsole } from '../lib/console-context'
import { getAiAnalysis } from '../services/fraud.service'

vi.mock('../lib/console-context', () => ({ useConsole: vi.fn() }))
vi.mock('../lib/queries', () => ({ useCases: () => ({ data: { content: [], totalElements: 0, totalPages: 0 } }) }))
vi.mock('../services/fraud.service', () => ({ getAiAnalysis: vi.fn(), createAiAnalysis: vi.fn() }))
afterEach(() => {
    cleanup()
    vi.resetAllMocks()
})

it('opens AI lookup for evaluated refs without requiring a fraud case, trimming the ref', async () => {
    vi.mocked(useConsole).mockReturnValue({
        session: { mode: 'live' },
        openCase: vi.fn(),
        notify: vi.fn(),
    } as unknown as ReturnType<typeof useConsole>)
    const error = new AxiosError('No analysis')
    error.response = { status: 404, data: { error: { code: 'AI_ANALYSIS_NOT_FOUND' } } } as AxiosError['response']
    vi.mocked(getAiAnalysis).mockRejectedValue(error)
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
        <QueryClientProvider client={client}>
            <TransactionsPage />
        </QueryClientProvider>,
    )
    const input = screen.getByLabelText('Phân tích theo mã giao dịch')
    expect(input).toHaveAttribute('maxlength', '80')
    const button = screen.getByRole('button', { name: 'Xem phân tích AI' })
    expect(button).toBeDisabled()
    fireEvent.change(input, { target: { value: '  TX-LOOKUP-001  ' } })
    fireEvent.click(button)
    expect(await screen.findByRole('button', { name: 'Phân tích bằng AI' })).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(getAiAnalysis).toHaveBeenCalledWith('TX-LOOKUP-001')
})
it('keeps synthetic examples inside demo cases instead of exposing live lookup', () => {
    vi.mocked(useConsole).mockReturnValue({
        session: { mode: 'demo' },
        openCase: vi.fn(),
        notify: vi.fn(),
    } as unknown as ReturnType<typeof useConsole>)
    render(<TransactionsPage />)
    expect(screen.queryByLabelText('Phân tích theo mã giao dịch')).not.toBeInTheDocument()
})
