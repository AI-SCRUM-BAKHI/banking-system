import axios from 'axios'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Sparkles } from 'lucide-react'
import { errorMessage } from '../api/axios'
import { createAiAnalysis, getAiAnalysis } from '../services/fraud.service'
import type { AiFraudAnalysis, FraudDetail, Mode } from '../types/fraud.types'
import { Loading, Pill } from './ui'

function errorCode(error: unknown): string | undefined {
    return axios.isAxiosError(error) ? error.response?.data?.error?.code : undefined
}

function analysisError(error: unknown): string {
    const code = errorCode(error)
    if (code === 'RESOURCE_NOT_FOUND')
        return 'Chưa tìm thấy kết quả đánh giá cho mã giao dịch này. Hãy gửi giao dịch qua API đánh giá trước; hồ sơ điều tra và kết quả đánh giá được lưu riêng.'
    if (code === 'AI_ANALYSIS_NOT_ELIGIBLE') return 'Chỉ giao dịch có kết quả đánh giá FRAUD mới được phân tích AI.'
    if (code === 'AI_PROVIDER_UNAVAILABLE')
        return 'OpenRouter chưa được cấu hình. Hãy kiểm tra API key và model trong cấu hình backend.'
    if (code === 'AI_PROVIDER_TIMEOUT' || (axios.isAxiosError(error) && error.code === 'ECONNABORTED'))
        return 'AI phản hồi quá thời gian. Bạn có thể thử lại hoặc tải kết quả đã lưu.'
    return errorMessage(error)
}

function syntheticAnalysis(detail: FraudDetail): AiFraudAnalysis {
    return {
        fraudExplanation: {
            summary: 'Ví dụ tổng hợp minh họa cách trình bày tín hiệu và hướng điều tra; chưa gọi OpenRouter.',
            reasons: detail.ruleHits.map((rule) => ({
                ruleCode: rule.ruleCode,
                description: rule.explanation,
                evidenceTransactionRefs: [detail.transactionRef],
            })),
        },
        investigationRecommendations: [
            {
                priority: 1,
                action: 'Xác minh mục đích giao dịch và quan hệ giữa hai tài khoản.',
                rationale:
                    'Đối chiếu tín hiệu trong hồ sơ với chứng từ để phân biệt giao dịch hợp lệ và hoạt động đáng ngờ.',
                evidenceTransactionRefs: [detail.transactionRef],
                dataToCollect: ['Chứng từ giao dịch', 'Thông tin xác minh quan hệ bên gửi và bên nhận'],
            },
        ],
    }
}

export function AiAnalysisPanel({
    transactionRef,
    mode,
    demoDetail,
}: {
    transactionRef: string
    mode: Mode
    demoDetail?: FraudDetail
}) {
    const client = useQueryClient()
    const queryKey = ['ai-analysis', mode, transactionRef]
    const query = useQuery({
        queryKey,
        queryFn: () => getAiAnalysis(transactionRef),
        enabled: mode === 'live',
        retry: false,
        staleTime: Infinity,
    })
    const mutation = useMutation({
        mutationFn: () => {
            if (mode === 'demo') {
                if (!demoDetail) throw new Error('Hãy chọn một hồ sơ demo để xem ví dụ tổng hợp.')
                return Promise.resolve(syntheticAnalysis(demoDetail))
            }
            return createAiAnalysis(transactionRef)
        },
        onSuccess: (data) => client.setQueryData(queryKey, data),
    })
    const analysis = query.data
    const missing = errorCode(query.error) === 'AI_ANALYSIS_NOT_FOUND'
    const blocked = ['RESOURCE_NOT_FOUND', 'AI_ANALYSIS_NOT_ELIGIBLE'].includes(errorCode(mutation.error) || '')
    const canGenerate = !blocked && (mode === 'demo' || missing)
    const busy = mutation.isPending || (mode === 'live' && query.isPending)

    return (
        <section className="ai-analysis" aria-label="Phân tích AI" aria-busy={busy}>
            <div className="ai-analysis-heading">
                <Sparkles size={20} aria-hidden="true" />
                <div>
                    <h3>Phân tích AI</h3>
                    <p className="ai-ref">{transactionRef}</p>
                </div>
                <Pill tone="neutral">{mode === 'demo' ? 'Ví dụ tổng hợp' : 'OpenRouter'}</Pill>
            </div>
            <p className="ai-analysis-note">
                {mode === 'demo'
                    ? 'Dữ liệu và nội dung minh họa được tạo cục bộ, không gọi model AI.'
                    : 'AI giải thích bằng chứng đã lưu và đề xuất bước kiểm tra. Chuyên viên xác nhận kết luận điều tra.'}
            </p>
            {busy && (
                <div role="status" aria-live="polite">
                    <Loading />
                    <p>
                        {mutation.isPending ? 'Đang phân tích bằng chứng, vui lòng chờ…' : 'Đang tải phân tích đã lưu…'}
                    </p>
                </div>
            )}
            {!analysis && !busy && (mode === 'demo' || missing) && (
                <div className="ai-analysis-empty">
                    <h4>Chưa có bản phân tích</h4>
                    <p>Tạo một bản phân tích gồm lý do bị gắn cờ fraud và các bước điều tra ưu tiên.</p>
                </div>
            )}
            {!analysis && query.isError && !missing && (
                <p className="form-error" role="alert">
                    {analysisError(query.error)}
                </p>
            )}
            {mutation.isError && (
                <p className="form-error" role="alert">
                    {analysisError(mutation.error)}
                </p>
            )}
            {analysis && (
                <div className="ai-analysis-result">
                    <section aria-labelledby="fraud-explanation-title">
                        <h4 id="fraud-explanation-title">01 · Vì sao bị gắn cờ fraud?</h4>
                        <p className="ai-summary">{analysis.fraudExplanation.summary}</p>
                        {analysis.fraudExplanation.reasons.map((reason, index) => (
                            <article key={`${reason.ruleCode}-${index}`}>
                                <code>{reason.ruleCode}</code>
                                <p>{reason.description}</p>
                                <Evidence refs={reason.evidenceTransactionRefs} />
                            </article>
                        ))}
                    </section>
                    <section aria-labelledby="investigation-title">
                        <h4 id="investigation-title">02 · Hướng điều tra</h4>
                        {analysis.investigationRecommendations.map((step, index) => (
                            <article key={`${step.priority}-${index}`}>
                                <span className="ai-priority">Ưu tiên {step.priority}</span>
                                <h5>{step.action}</h5>
                                <p>{step.rationale}</p>
                                <Evidence refs={step.evidenceTransactionRefs} />
                                {step.dataToCollect.length > 0 && (
                                    <div className="ai-collect">
                                        <strong>Cần thu thập</strong>
                                        <ul>
                                            {step.dataToCollect.map((item, i) => (
                                                <li key={i}>{item}</li>
                                            ))}
                                        </ul>
                                    </div>
                                )}
                            </article>
                        ))}
                    </section>
                </div>
            )}
            {!analysis && !busy && (
                <div className="ai-analysis-actions">
                    {canGenerate && (
                        <button className="btn primary" onClick={() => mutation.mutate()}>
                            <Sparkles size={15} aria-hidden="true" />
                            {mode === 'demo'
                                ? 'Xem ví dụ tổng hợp'
                                : mutation.isError
                                  ? 'Thử phân tích lại'
                                  : 'Phân tích bằng AI'}
                        </button>
                    )}
                    {mode === 'live' && (
                        <button
                            className="btn secondary"
                            onClick={() => {
                                mutation.reset()
                                void query.refetch()
                            }}
                        >
                            Tải lại kết quả
                        </button>
                    )}
                </div>
            )}
        </section>
    )
}

function Evidence({ refs }: { refs: string[] }) {
    if (!refs.length) return null
    return (
        <div className="ai-evidence" aria-label="Giao dịch bằng chứng">
            <span>Bằng chứng</span>
            {refs.map((ref) => (
                <code key={ref}>{ref}</code>
            ))}
        </div>
    )
}
