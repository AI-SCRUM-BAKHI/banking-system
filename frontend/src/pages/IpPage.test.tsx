import { render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { IpPage } from './IpPage'
vi.mock('../lib/console-context', () => ({ useConsole: () => ({ session: { mode: 'live' }, openCase: vi.fn() }) }))
vi.mock('../lib/queries', () => ({
    useCases: () => ({ data: { content: [] } }),
    useIpIntelligence: () => ({
        data: {
            totalTransactions: 3,
            transactionsWithIp: 2,
            sourceFraudTransactions: 2,
            groups: [
                {
                    ip: '192.0.2.54',
                    count: 2,
                    sourceFraud: 2,
                    engineFraud: 1,
                    confirmed: 0,
                    highRisk: 0,
                    accounts: 3,
                    accountLabels: ['**** 0001', '**** 0002'],
                    amount: 1000,
                    records: [],
                },
            ],
        },
        isPending: false,
        isError: false,
    }),
}))
it('shows stored IPs in live mode with CSV fraud labels separate from confirmed reviews', () => {
    render(<IpPage />)
    expect(screen.getAllByText('192.0.2.54').length).toBeGreaterThan(0)
    expect(screen.getByText('2 nhãn fraud CSV')).toBeInTheDocument()
    expect(screen.getByText('0 xác nhận fraud')).toBeInTheDocument()
    expect(screen.queryByText('Backend chưa cung cấp địa chỉ IP')).not.toBeInTheDocument()
})
