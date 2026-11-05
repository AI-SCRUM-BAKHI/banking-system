export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ReviewStatus = 'NEW' | 'REVIEWING' | 'CONFIRMED_FRAUD' | 'FALSE_POSITIVE'
export type Mode = 'demo' | 'live'

export interface AiFraudAnalysis {
    fraudExplanation: {
        summary: string
        reasons: {
            ruleCode: string
            description: string
            evidenceTransactionRefs: string[]
        }[]
    }
    investigationRecommendations: {
        priority: number
        action: string
        rationale: string
        evidenceTransactionRefs: string[]
        dataToCollect: string[]
    }[]
}

export interface FraudSummary {
    id: string
    transactionRef: string
    fromAccountMasked: string
    toAccountMasked: string
    amount: number
    currency: string
    transactionTime: string
    riskScore: number
    riskLevel: RiskLevel
    reviewStatus: ReviewStatus
    createdAt: string
    /** Available only in the explicitly synthetic demo. Backend does not provide IP. */
    demoIp?: string
}
export interface Account {
    ref: string
    masked: string
    customerName: string | null
}
export interface RuleHit {
    id: string
    ruleCode: string
    ruleName: string
    scoreContribution: number
    explanation: string
}
export interface FraudDetail extends Omit<FraudSummary, 'fromAccountMasked' | 'toAccountMasked'> {
    fromAccount: Account
    toAccount: Account
    fraudReason: string
    ruleHits: RuleHit[]
}
export interface Page<T> {
    content: T[]
    page: number
    size: number
    totalElements: number
    totalPages: number
}
export interface DashboardSummary {
    total: number
    newCases: number
    reviewingCases: number
    confirmedFraud: number
    falsePositives: number
    highRisk: number
    criticalRisk: number
}
export interface GraphNode {
    id: string
    masked: string
    customerName: string | null
}
export interface GraphEdge {
    id: string
    transactionRef: string
    fromAccountRef: string
    toAccountRef: string
    amount: number
    currency: string
    transactionTime: string
    hopLevel: number
    root: boolean
}
export interface TransactionGraph {
    nodes: GraphNode[]
    edges: GraphEdge[]
}
export interface FraudFilters {
    page?: number
    size?: number
    riskLevel?: RiskLevel
    reviewStatus?: ReviewStatus
    keyword?: string
    from?: string
    to?: string
    sort?: string
}
