import type { FraudSummary, ReviewStatus } from '../types/fraud.types'
export function nextReviewStatuses(current: ReviewStatus): ReviewStatus[] {
    if (current === 'NEW') return ['REVIEWING']
    if (current === 'REVIEWING') return ['CONFIRMED_FRAUD', 'FALSE_POSITIVE']
    return []
}
export interface IpGroup {
    ip: string
    count: number
    confirmed: number
    highRisk: number
    accounts: number
    amount: number
    records: FraudSummary[]
}
export function groupDemoIps(rows: FraudSummary[]): IpGroup[] {
    const groups = new Map<string, FraudSummary[]>()
    for (const row of rows) {
        if (!row.demoIp) continue
        const group = groups.get(row.demoIp) || []
        group.push(row)
        groups.set(row.demoIp, group)
    }
    return [...groups]
        .map(([ip, records]) => ({
            ip,
            records,
            count: records.length,
            confirmed: records.filter((r) => r.reviewStatus === 'CONFIRMED_FRAUD').length,
            highRisk: records.filter((r) => r.riskLevel === 'HIGH' || r.riskLevel === 'CRITICAL').length,
            accounts: new Set(records.flatMap((r) => [r.fromAccountMasked, r.toAccountMasked])).size,
            amount: records.reduce((sum, r) => sum + r.amount, 0),
        }))
        .sort((a, b) => b.confirmed - a.confirmed || b.highRisk - a.highRisk || b.count - a.count)
}
