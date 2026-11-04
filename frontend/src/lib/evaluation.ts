import { z } from 'zod'
import type { RiskLevel } from '../types/fraud.types'

const timestamp = z
    .string()
    .refine(
        (value) => /(Z|[+-]\d{2}:\d{2})$/.test(value) && Number.isFinite(Date.parse(value)),
        'Thời gian phải là ISO 8601 kèm UTC offset, ví dụ +07:00 hoặc Z.',
    )
export const evaluationRequestSchema = z
    .object({
        transactionRef: z.string().trim().min(1, 'Nhập mã giao dịch.').max(80),
        senderAccountId: z.string().trim().min(1, 'Nhập tài khoản gửi.').max(80),
        receiverAccountId: z.string().trim().min(1, 'Nhập tài khoản nhận.').max(80),
        amountVnd: z
            .number()
            .int()
            .positive('Số tiền phải là số nguyên dương.')
            .max(Number.MAX_SAFE_INTEGER, 'Số tiền vượt giới hạn số nguyên an toàn của trình duyệt.'),
        currency: z.literal('VND'),
        transactionTime: timestamp,
        postedAt: timestamp.optional(),
    })
    .refine((request) => request.senderAccountId !== request.receiverAccountId, {
        message: 'Tài khoản gửi và nhận phải khác nhau.',
        path: ['receiverAccountId'],
    })
    .refine((request) => !request.postedAt || Date.parse(request.postedAt) >= Date.parse(request.transactionTime), {
        message: 'Thời điểm ghi có phải từ thời điểm giao dịch trở đi.',
        path: ['postedAt'],
    })
export type EvaluationRequest = z.infer<typeof evaluationRequestSchema>
export interface EvaluationResult {
    transactionRef: string
    isFraud: boolean | null
    decision: 'FRAUD' | 'NOT_FRAUD' | 'INSUFFICIENT_DATA'
    riskScore: number
    riskLevel: RiskLevel
    evaluationStatus: 'COMPLETE' | 'PARTIAL'
    fraudThreshold: number
    ruleVersion: string
    triggeredRules: { ruleCode: string; scoreContribution: number; suppressedBy: string | null }[]
    unevaluatedRules: { ruleCode: string; reason: string }[]
}
export const evaluationRules = [
    {
        code: 'AMOUNT_SPIKE',
        name: 'Số tiền tăng đột biến',
        points: 35,
        description: 'Số tiền lớn hơn 8 lần trung vị giao dịch trong 30 ngày.',
    },
    {
        code: 'NEW_BENEFICIARY_LARGE',
        name: 'Người nhận mới, giá trị lớn',
        points: 50,
        description: 'Người nhận chưa xuất hiện trong 30 ngày, số tiền vượt 8 lần trung vị.',
    },
    {
        code: 'UNUSUAL_HOUR',
        name: 'Khung giờ bất thường',
        points: 25,
        description: 'Giao dịch trong khung 02:00–03:59, giờ Việt Nam.',
    },
    {
        code: 'FREQUENCY_SPIKE',
        name: 'Tần suất tăng đột biến',
        points: 45,
        description: 'Ít nhất 12 giao dịch gửi trong 10 phút, gồm giao dịch hiện tại.',
    },
    {
        code: 'STRUCTURED_SPLIT',
        name: 'Chia nhỏ giao dịch',
        points: 55,
        description: '9 khoản bằng nhau tới cùng người nhận, cách nhau 5–7 phút.',
    },
    {
        code: 'RAPID_PASS_THROUGH',
        name: 'Tiền đi qua nhanh',
        points: 65,
        description: 'Chuyển tiếp 95–100% khoản vừa ghi có, sau 60–120 giây.',
    },
    {
        code: 'MULTI_ACCOUNT_FLOW',
        name: 'Dòng tiền nhiều tài khoản',
        points: 80,
        description: 'Ít nhất 5 nguồn tiền, 2 đích và tỷ lệ chuyển tiếp 95–100%.',
    },
]
export function newEvaluationSample(): EvaluationRequest {
    return {
        transactionRef: `TX-UI-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        senderAccountId: 'SIM000000005',
        receiverAccountId: 'SIM000000028',
        amountVnd: 85000000,
        currency: 'VND',
        transactionTime: new Date(Date.now() - 2000).toISOString(),
    }
}
