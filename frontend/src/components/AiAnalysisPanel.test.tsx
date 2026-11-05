import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AxiosError } from 'axios'
import { afterEach, expect, it, vi } from 'vitest'
import { AiAnalysisPanel } from './AiAnalysisPanel'
import { createAiAnalysis, getAiAnalysis } from '../services/fraud.service'
import { demoDetail, demoTransactions } from '../lib/demo'
import type { AiFraudAnalysis } from '../types/fraud.types'

vi.mock('../services/fraud.service', () => ({ getAiAnalysis: vi.fn(), createAiAnalysis: vi.fn() }))
afterEach(() => {
    cleanup()
    vi.resetAllMocks()
})

const analysis: AiFraudAnalysis = {
    fraudExplanation: {
        summary: 'Chuyển tiền bất thường.',
        reasons: [
            { ruleCode: 'AMOUNT_SPIKE', description: 'Số tiền vượt baseline.', evidenceTransactionRefs: ['TX-001'] },
        ],
    },
    investigationRecommendations: [
        {
            priority: 1,
            action: 'Xác minh chứng từ.',
            rationale: 'Cần đối chiếu.',
            evidenceTransactionRefs: ['TX-001'],
            dataToCollect: ['Chứng từ gốc'],
        },
    ],
}
function failure(code: string, status = 404) {
    const error = new AxiosError(code)
    error.response = { status, data: { error: { code, message: code } } } as AxiosError['response']
    return error
}
function mount(mode: 'live' | 'demo' = 'live') {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
    render(
        <QueryClientProvider client={client}>
            <AiAnalysisPanel
                transactionRef="TX-001"
                mode={mode}
                demoDetail={mode === 'demo' ? demoDetail(demoTransactions[0].id) : undefined}
            />
        </QueryClientProvider>,
    )
    return client
}
it('loads a cached analysis without generating or calling OpenRouter again', async () => {
    vi.mocked(getAiAnalysis).mockResolvedValue(analysis)
    mount()
    expect(await screen.findByText('Chuyển tiền bất thường.')).toBeInTheDocument()
    expect(screen.getByText('Xác minh chứng từ.')).toBeInTheDocument()
    expect(screen.getByText('Chứng từ gốc')).toBeInTheDocument()
    expect(createAiAnalysis).not.toHaveBeenCalled()
})
it('generates only when explicitly requested and caches the two returned sections', async () => {
    vi.mocked(getAiAnalysis).mockRejectedValue(failure('AI_ANALYSIS_NOT_FOUND'))
    vi.mocked(createAiAnalysis).mockResolvedValue(analysis)
    const client = mount()
    fireEvent.click(await screen.findByRole('button', { name: 'Phân tích bằng AI' }))
    expect(await screen.findByText('Chuyển tiền bất thường.')).toBeInTheDocument()
    expect(createAiAnalysis).toHaveBeenCalledWith('TX-001')
    expect(client.getQueryData(['ai-analysis', 'live', 'TX-001'])).toEqual(analysis)
})
it('does not offer generation for a legacy case with no evaluation', async () => {
    vi.mocked(getAiAnalysis).mockRejectedValue(failure('RESOURCE_NOT_FOUND'))
    mount()
    expect(await screen.findByRole('alert')).toHaveTextContent('Chưa tìm thấy kết quả đánh giá')
    expect(screen.queryByRole('button', { name: 'Phân tích bằng AI' })).not.toBeInTheDocument()
    expect(createAiAnalysis).not.toHaveBeenCalled()
})
it('shows provider error and permits a deliberate retry', async () => {
    vi.mocked(getAiAnalysis).mockRejectedValue(failure('AI_ANALYSIS_NOT_FOUND'))
    vi.mocked(createAiAnalysis)
        .mockRejectedValueOnce(failure('AI_PROVIDER_TIMEOUT', 504))
        .mockResolvedValueOnce(analysis)
    mount()
    fireEvent.click(await screen.findByRole('button', { name: 'Phân tích bằng AI' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('AI phản hồi quá thời gian')
    fireEvent.click(screen.getByRole('button', { name: 'Thử phân tích lại' }))
    expect(await screen.findByText('Chuyển tiền bất thường.')).toBeInTheDocument()
})
it('blocks regeneration when evaluation is not FRAUD', async () => {
    vi.mocked(getAiAnalysis).mockRejectedValue(failure('AI_ANALYSIS_NOT_FOUND'))
    vi.mocked(createAiAnalysis).mockRejectedValue(failure('AI_ANALYSIS_NOT_ELIGIBLE', 409))
    mount()
    fireEvent.click(await screen.findByRole('button', { name: 'Phân tích bằng AI' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Chỉ giao dịch có kết quả đánh giá FRAUD')
    expect(screen.queryByRole('button', { name: 'Thử phân tích lại' })).not.toBeInTheDocument()
})
it('renders a clearly labeled synthetic example without network calls in demo mode', async () => {
    mount('demo')
    fireEvent.click(screen.getByRole('button', { name: 'Xem ví dụ tổng hợp' }))
    await waitFor(() => expect(screen.getByText(/Ví dụ tổng hợp minh họa/)).toBeInTheDocument())
    expect(getAiAnalysis).not.toHaveBeenCalled()
    expect(createAiAnalysis).not.toHaveBeenCalled()
})
