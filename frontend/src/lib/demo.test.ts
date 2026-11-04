import { expect, it } from 'vitest'
import { demoTransactions } from './demo'
it('keeps the synthetic snapshot within the advertised Vietnam local date range', () => {
    const start = Date.parse('2026-10-29T00:00:00+07:00')
    const end = Date.parse('2026-11-04T23:59:59.999+07:00')
    expect(
        demoTransactions.every((r) => Date.parse(r.transactionTime) >= start && Date.parse(r.transactionTime) <= end),
    ).toBe(true)
})
