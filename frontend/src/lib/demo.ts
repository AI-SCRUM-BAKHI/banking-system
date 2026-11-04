import type { CurrentUser } from '../types/auth.types'
import type {
    DashboardSummary,
    FraudDetail,
    FraudFilters,
    FraudSummary,
    Page,
    ReviewStatus,
    RiskLevel,
    TransactionGraph,
} from '../types/fraud.types'
import { nextReviewStatuses } from './investigation'

export const demoUser: CurrentUser = {
    id: 'demo-operator',
    username: 'minhanh',
    fullName: 'Nguyễn Minh Anh',
    email: 'demo@example.com',
    role: 'ADMIN',
    status: 'ACTIVE',
}
const risks: RiskLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'HIGH', 'CRITICAL', 'MEDIUM']
const statuses: ReviewStatus[] = ['NEW', 'NEW', 'REVIEWING', 'FALSE_POSITIVE', 'CONFIRMED_FRAUD', 'REVIEWING', 'NEW']
export const demoTransactions: FraudSummary[] = Array.from({ length: 168 }, (_, i) => ({
    id: `00000000-0000-4000-8000-${String(i + 1).padStart(12, '0')}`,
    transactionRef: `TX-2026-${String(10481 + i).padStart(6, '0')}`,
    fromAccountMasked: `**** ${1000 + ((i * 17) % 78)}`,
    toAccountMasked: `**** ${3000 + ((i * 31) % 52)}`,
    amount: [185000000, 78000000, 24500000, 4500000, 93000000, 260000000, 18200000][i % 7] + i * 12500,
    currency: 'VND',
    transactionTime: new Date(Date.UTC(2026, 10, 4 - Math.floor(i / 24), (i * 7) % 17, (i * 13) % 60)).toISOString(),
    riskScore: [95, 72, 48, 22, 68, 88, 55][i % 7],
    riskLevel: risks[i % 7],
    reviewStatus: statuses[i % 7],
    createdAt: new Date(Date.UTC(2026, 10, 4 - Math.floor(i / 24), (i * 7) % 17, (i * 13) % 60, 2)).toISOString(),
    demoIp:
        i % 7 < 2 || i % 7 === 4
            ? ['203.0.113.42', '198.51.100.18', '192.0.2.86'][i % 3]
            : `192.0.2.${100 + (i % 100)}`,
}))
export function demoList(filters: FraudFilters = {}): Page<FraudSummary> {
    const { page = 0, size = 20, riskLevel, reviewStatus, keyword = '', from, to, sort = 'createdAt,desc' } = filters
    const filtered = demoTransactions.filter(
        (t) =>
            (!riskLevel || t.riskLevel === riskLevel) &&
            (!reviewStatus || t.reviewStatus === reviewStatus) &&
            (!from || t.transactionTime >= from) &&
            (!to || t.transactionTime <= to) &&
            [t.transactionRef, t.fromAccountMasked, t.toAccountMasked]
                .join(' ')
                .toLowerCase()
                .includes(keyword.toLowerCase()),
    )
    const [field, direction] = sort.split(',')
    filtered.sort((a, b) => {
        const key = field as keyof FraudSummary
        const x = a[key] ?? ''
        const y = b[key] ?? ''
        return (
            (typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y))) *
            (direction === 'asc' ? 1 : -1)
        )
    })
    return {
        content: filtered.slice(page * size, (page + 1) * size),
        page,
        size,
        totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / size),
    }
}
export function demoSummary(period: Pick<FraudFilters, 'from' | 'to'> = {}): DashboardSummary {
    const rows = demoList({ ...period, size: 1000 }).content
    return {
        total: rows.length,
        newCases: rows.filter((t) => t.reviewStatus === 'NEW').length,
        reviewingCases: rows.filter((t) => t.reviewStatus === 'REVIEWING').length,
        confirmedFraud: rows.filter((t) => t.reviewStatus === 'CONFIRMED_FRAUD').length,
        falsePositives: rows.filter((t) => t.reviewStatus === 'FALSE_POSITIVE').length,
        highRisk: rows.filter((t) => t.riskLevel === 'HIGH').length,
        criticalRisk: rows.filter((t) => t.riskLevel === 'CRITICAL').length,
    }
}
export function demoDetail(id: string): FraudDetail {
    const row = demoTransactions.find((t) => t.id === id)
    if (!row) throw new Error('Không tìm thấy hồ sơ demo.')
    const ruleHits = [
        {
            id: `${id}-rule-1`,
            ruleCode: 'MULTI_ACCOUNT_FLOW',
            ruleName: 'Luồng tiền qua nhiều tài khoản',
            scoreContribution: row.riskScore,
            explanation:
                'Kịch bản tổng hợp: nhiều tài khoản chuyển tiền qua một tài khoản trung gian trong thời gian ngắn.',
        },
    ]
    return {
        ...row,
        fromAccount: {
            ref: 'ACC-' + row.fromAccountMasked.slice(-4),
            masked: row.fromAccountMasked,
            customerName: 'Khách hàng mô phỏng A',
        },
        toAccount: {
            ref: 'ACC-' + row.toAccountMasked.slice(-4),
            masked: row.toAccountMasked,
            customerName: 'Khách hàng mô phỏng B',
        },
        fraudReason:
            'Hồ sơ mô phỏng để trình diễn quy trình điều tra. Điểm rủi ro là dữ liệu mẫu, không được chấm bởi backend.',
        ruleHits,
    }
}
export function demoGraph(id: string): TransactionGraph {
    const detail = demoDetail(id)
    const nodes = [
        { id: detail.fromAccount.ref, masked: detail.fromAccount.masked, customerName: 'Tài khoản gửi' },
        { id: detail.toAccount.ref, masked: detail.toAccount.masked, customerName: 'Trung gian' },
        ...Array.from({ length: 4 }, (_, i) => ({
            id: `linked-${i}`,
            masked: `**** ${6318 + i * 132}`,
            customerName: `Tài khoản liên quan ${i + 1}`,
        })),
    ]
    return {
        nodes,
        edges: nodes
            .slice(1)
            .map((n, i) => ({
                id: `edge-${i}`,
                transactionRef: i === 0 ? detail.transactionRef : `CHAIN-${i}`,
                fromAccountRef: i === 0 ? nodes[0].id : nodes[1].id,
                toAccountRef: n.id,
                amount: Math.round(detail.amount / (i + 1)),
                currency: 'VND',
                transactionTime: detail.transactionTime,
                hopLevel: i === 0 ? 0 : 1,
                root: i === 0,
            }))
            .filter((e) => e.fromAccountRef !== e.toAccountRef),
    }
}
export function demoReview(id: string, status: ReviewStatus) {
    const row = demoTransactions.find((t) => t.id === id)
    if (!row || !nextReviewStatuses(row.reviewStatus).includes(status))
        throw new Error('Chuyển trạng thái không hợp lệ.')
    row.reviewStatus = status
    return { id, reviewStatus: status }
}
