import { describe, expect, it } from 'vitest'
import { groupDemoIps, nextReviewStatuses } from './investigation'
import { demoTransactions } from './demo'

describe('review workflow matches FraudTransactionService', () => {
    it('allows only NEW → REVIEWING → final review decisions', () => {
        expect(nextReviewStatuses('NEW')).toEqual(['REVIEWING'])
        expect(nextReviewStatuses('REVIEWING')).toEqual(['CONFIRMED_FRAUD', 'FALSE_POSITIVE'])
        expect(nextReviewStatuses('CONFIRMED_FRAUD')).toEqual([])
        expect(nextReviewStatuses('FALSE_POSITIVE')).toEqual([])
    })
})
it('groups only explicit demo IPs and keeps confirmed fraud distinct from high risk', () => {
    const row = demoTransactions[0]
    const groups = groupDemoIps([
        { ...row, demoIp: '203.0.113.42', reviewStatus: 'NEW' },
        { ...row, id: 'another', demoIp: '203.0.113.42', reviewStatus: 'CONFIRMED_FRAUD' },
        { ...row, id: 'live-row', demoIp: undefined },
    ])
    expect(groups).toHaveLength(1)
    expect(groups[0]).toMatchObject({
        ip: '203.0.113.42',
        count: 2,
        confirmed: 1,
        highRisk: 2,
        accounts: 2,
        amount: row.amount * 2,
    })
})
