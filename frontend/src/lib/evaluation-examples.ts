import type { EvaluationRequest } from './evaluation'

// Inputs of imported historical records. Repeated requests return the engine's stored result.
export const evaluationExamples: { key: string; name: string; request: EvaluationRequest }[] = [
    {
        key: 'FRAUD',
        name: 'Lịch sử có dự đoán fraud',
        request: {
            transactionRef: 'TX00000006240',
            senderAccountId: 'SIM000000115',
            receiverAccountId: 'SIM000000109',
            amountVnd: 583000,
            currency: 'VND',
            transactionTime: '2026-10-03T15:06:02+07:00',
            postedAt: '2026-10-03T15:06:36+07:00',
        },
    },
    {
        key: 'NOT_FRAUD',
        name: 'Lịch sử chưa phát hiện fraud',
        request: {
            transactionRef: 'TX00000005115',
            senderAccountId: 'SIM000000100',
            receiverAccountId: 'SIM000000108',
            amountVnd: 2096000,
            currency: 'VND',
            transactionTime: '2026-09-27T07:48:25+07:00',
            postedAt: '2026-09-27T07:48:40+07:00',
        },
    },
    {
        key: 'INSUFFICIENT_DATA',
        name: 'Lịch sử thiếu ngữ cảnh',
        request: {
            transactionRef: 'TX00000000001',
            senderAccountId: 'SIM000000005',
            receiverAccountId: 'SIM000000028',
            amountVnd: 434000,
            currency: 'VND',
            transactionTime: '2026-08-28T05:48:42+07:00',
            postedAt: '2026-08-28T05:49:32+07:00',
        },
    },
]
