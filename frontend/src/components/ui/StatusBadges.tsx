import { reviewLabels, riskLabels } from '../../constants/fraud'
import type { ReviewStatus, RiskLevel } from '../../types/fraud.types'

export function RiskBadge({ level }: { level: RiskLevel }) {
    const colorClasses: Record<RiskLevel, string> = {
        LOW: 'border-accent-bright/25 bg-accent-bright/10 text-accent-bright',
        MEDIUM: 'border-signal/25 bg-signal/10 text-signal',
        HIGH: 'border-risk-medium/25 bg-risk-medium/10 text-risk-medium',
        CRITICAL: 'border-risk-critical/25 bg-risk-critical/10 text-risk-critical',
    }

    return (
        <span
            className={`inline-flex min-h-[23px] items-center gap-1.5 rounded-full border px-2 py-0.5 text-[9px] font-semibold tracking-[0.04em] before:size-[5px] before:rounded-full before:bg-current before:content-[''] ${colorClasses[level]}`}
        >
            {riskLabels[level]}
        </span>
    )
}

export function ReviewBadge({ status }: { status: ReviewStatus }) {
    const colorClasses: Record<ReviewStatus, string> = {
        NEW: 'border-line-strong bg-white/[0.04] text-muted',
        REVIEWING: 'border-signal/25 bg-signal/10 text-signal',
        CONFIRMED_FRAUD: 'border-accent-bright/25 bg-accent-bright/10 text-accent-bright',
        FALSE_POSITIVE: 'border-line-strong bg-white/[0.04] text-muted',
    }

    return (
        <span
            className={`inline-flex min-h-[23px] items-center rounded-full border px-2 py-0.5 text-[9px] font-semibold tracking-[0.04em] ${colorClasses[status]}`}
        >
            {reviewLabels[status]}
        </span>
    )
}
