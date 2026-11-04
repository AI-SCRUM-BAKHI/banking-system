import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, it, vi } from 'vitest'
import { EvaluationPlayground } from './EvaluationPlayground'
import { evaluateTransaction } from '../services/evaluation.service'
vi.mock('../lib/console-context', () => ({ useConsole: () => ({ session: { mode: 'live' }, notify: vi.fn() }) }))
vi.mock('../services/evaluation.service', () => ({ evaluateTransaction: vi.fn() }))
afterEach(() => {
    cleanup()
    vi.clearAllMocks()
})
it('submits transaction input and displays a nullable fraud prediction as insufficient data', async () => {
    vi.mocked(evaluateTransaction).mockResolvedValue({
        success: true,
        data: {
            transactionRef: 'TX-TEST',
            isFraud: null,
            decision: 'INSUFFICIENT_DATA',
            riskScore: 0,
            riskLevel: 'LOW',
            evaluationStatus: 'PARTIAL',
            fraudThreshold: 60,
            ruleVersion: 'rule-score-v1',
            triggeredRules: [],
            unevaluatedRules: [{ ruleCode: 'AMOUNT_SPIKE', reason: 'Missing baseline coverage' }],
        },
        message: 'ok',
        requestId: 'req-123',
        timestamp: '2026-11-04T00:00:00Z',
    })
    const user = userEvent.setup()
    render(<EvaluationPlayground />)
    await user.click(screen.getByRole('button', { name: 'Detect fraud' }))
    await screen.findByRole('heading', { name: 'Chưa đủ dữ liệu để kết luận' })
    expect(screen.getByText('null', { exact: true })).toBeInTheDocument()
    expect(screen.getByText('Missing baseline coverage')).toBeInTheDocument()
    expect(evaluateTransaction).toHaveBeenCalledWith(expect.objectContaining({ currency: 'VND', amountVnd: 85000000 }))
})
