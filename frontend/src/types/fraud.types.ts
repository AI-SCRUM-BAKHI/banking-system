export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ReviewStatus = 'NEW' | 'REVIEWING' | 'CONFIRMED_FRAUD' | 'FALSE_POSITIVE'

export interface FraudAlertRow {
    ref: string
    from: string
    to: string
    amount: number
    time: string
    score: number
    risk: RiskLevel
    status: ReviewStatus
}
