import type { ReviewStatus, RiskLevel } from '../types/fraud.types'

export const riskLabels: Record<RiskLevel, string> = {
    LOW: 'Thấp',
    MEDIUM: 'Trung bình',
    HIGH: 'Cao',
    CRITICAL: 'Nghiêm trọng',
}

export const reviewLabels: Record<ReviewStatus, string> = {
    NEW: 'Mới',
    REVIEWING: 'Đang xem xét',
    CONFIRMED_FRAUD: 'Đã xác nhận',
    FALSE_POSITIVE: 'Không phải fraud',
}
