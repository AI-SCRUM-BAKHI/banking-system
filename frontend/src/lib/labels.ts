import type { ReviewStatus, RiskLevel } from '../types/fraud.types'
export const riskLabel: Record<RiskLevel, string> = {
    LOW: 'Thấp',
    MEDIUM: 'Trung bình',
    HIGH: 'Cao',
    CRITICAL: 'Nghiêm trọng',
}
export const statusLabel: Record<ReviewStatus, string> = {
    NEW: 'Mới phát hiện',
    REVIEWING: 'Đang điều tra',
    CONFIRMED_FRAUD: 'Xác nhận fraud',
    FALSE_POSITIVE: 'Cảnh báo nhầm',
}
