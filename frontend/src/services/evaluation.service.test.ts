import { afterEach, expect, it, vi } from 'vitest'
import { apiClient } from '../api/axios'
import { evaluateTransaction } from './evaluation.service'
import { newEvaluationSample } from '../lib/evaluation'
afterEach(() => vi.restoreAllMocks())
it('sends the raw transaction through the authenticated frontend gateway and preserves a null fraud label', async () => {
    const input = newEvaluationSample()
    const envelope = {
        success: true,
        data: {
            transactionRef: input.transactionRef,
            isFraud: null,
            decision: 'INSUFFICIENT_DATA',
            riskScore: 0,
            riskLevel: 'LOW',
            evaluationStatus: 'PARTIAL',
            fraudThreshold: 60,
            ruleVersion: 'rule-score-v1',
            triggeredRules: [],
            unevaluatedRules: [{ ruleCode: 'AMOUNT_SPIKE', reason: 'Missing baseline' }],
        },
        requestId: 'request-1',
    }
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: envelope })
    expect(await evaluateTransaction(input)).toEqual(envelope)
    expect(post).toHaveBeenCalledWith('/transactions/evaluate', input, { baseURL: '/api/v1' })
})
