import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, CheckCheck, Fingerprint, Network, ScanLine, ShieldAlert } from 'lucide-react'
import { errorMessage } from '../api/axios'
import { useDetail, useGraph } from '../lib/queries'
import { demoReview } from '../lib/demo'
import { reviewCase } from '../services/fraud.service'
import { nextReviewStatuses } from '../lib/investigation'
import { dateTime, money } from '../lib/format'
import { ErrorState, Loading, Modal, RiskBadge, StatusBadge, Pill } from './ui'
import { statusLabel } from '../lib/labels'
import { FlowGraph } from './NetworkScene'
import type { Session } from '../lib/console-context'
import type { ReviewStatus } from '../types/fraud.types'
export function CaseDrawer({
    id,
    session,
    onClose,
    notify,
}: {
    id: string
    session: Session
    onClose: () => void
    notify: (message: string) => void
}) {
    const [tab, setTab] = useState<'overview' | 'graph'>('overview')
    const detail = useDetail(session.mode, id)
    const graph = useGraph(session.mode, id)
    const client = useQueryClient()
    const mutation = useMutation({
        mutationFn: (status: ReviewStatus) =>
            session.mode === 'demo' ? Promise.resolve(demoReview(id, status)) : reviewCase(id, status),
        onSuccess: async () => {
            await Promise.all(
                ['cases', 'summary', 'detail'].map((key) =>
                    client.invalidateQueries({ queryKey: [key, session.mode] }),
                ),
            )
            notify(session.mode === 'demo' ? 'Đã cập nhật trạng thái trong demo.' : 'Đã lưu trạng thái điều tra.')
        },
    })
    return (
        <Modal title="Chi tiết hồ sơ" onClose={onClose} className="case-drawer">
            <div className="drawer-top">
                <div className="eyebrow">CASE INVESTIGATION</div>
                <h2>{detail.data?.transactionRef || 'Đang tải hồ sơ'}</h2>
                <p>Theo dõi bằng chứng. Đưa ra quyết định.</p>
            </div>
            {detail.isPending ? (
                <Loading />
            ) : detail.isError ? (
                <ErrorState message={errorMessage(detail.error)} retry={() => detail.refetch()} />
            ) : (
                detail.data && (
                    <>
                        <div className="case-badges">
                            <RiskBadge risk={detail.data.riskLevel} />
                            <StatusBadge status={detail.data.reviewStatus} />
                            {session.mode === 'demo' && <Pill tone="neutral">Hồ sơ tổng hợp</Pill>}
                        </div>
                        <div className="drawer-tabs">
                            <button className={tab === 'overview' ? 'active' : ''} onClick={() => setTab('overview')}>
                                <ScanLine size={16} />
                                Tổng quan & tín hiệu
                            </button>
                            <button className={tab === 'graph' ? 'active' : ''} onClick={() => setTab('graph')}>
                                <Network size={16} />
                                Dòng tiền
                            </button>
                        </div>
                        {tab === 'overview' ? (
                            <>
                                <div className="case-amount">
                                    <span>GIÁ TRỊ GIAO DỊCH</span>
                                    <strong>{money(detail.data.amount, detail.data.currency)}</strong>
                                    <small>{dateTime(detail.data.transactionTime)} · UTC+7</small>
                                </div>
                                <div className="account-transfer">
                                    <div>
                                        <span className="account-icon">
                                            <Fingerprint size={19} />
                                        </span>
                                        <small>TÀI KHOẢN GỬI</small>
                                        <b>{detail.data.fromAccount.masked}</b>
                                        <span>{detail.data.fromAccount.customerName || 'Không có tên khách hàng'}</span>
                                    </div>
                                    <ArrowRight size={20} />
                                    <div>
                                        <span className="account-icon recipient">
                                            <Fingerprint size={19} />
                                        </span>
                                        <small>TÀI KHOẢN NHẬN</small>
                                        <b>{detail.data.toAccount.masked}</b>
                                        <span>{detail.data.toAccount.customerName || 'Không có tên khách hàng'}</span>
                                    </div>
                                </div>
                                <div className="score-explainer">
                                    <div>
                                        <ShieldAlert size={20} />
                                        <h3>Điểm rủi ro</h3>
                                        <b>
                                            {detail.data.riskScore}
                                            <small>/100</small>
                                        </b>
                                    </div>
                                    <div className="score-meter">
                                        <i style={{ width: `${detail.data.riskScore}%` }} />
                                    </div>
                                    <p>{detail.data.fraudReason}</p>
                                    <small>
                                        Điểm rủi ro không phải xác suất gian lận. Trạng thái review là kết luận riêng.
                                    </small>
                                </div>
                                <div className="rule-hits">
                                    <h3>
                                        Tín hiệu kích hoạt <span>{detail.data.ruleHits.length}</span>
                                    </h3>
                                    {detail.data.ruleHits.length ? (
                                        detail.data.ruleHits.map((rule) => (
                                            <article key={rule.id}>
                                                <span className="rule-icon">
                                                    <ScanLine size={18} />
                                                </span>
                                                <div>
                                                    <b>{rule.ruleName}</b>
                                                    <code>{rule.ruleCode}</code>
                                                    <p>{rule.explanation}</p>
                                                </div>
                                                <span className="rule-score">+{rule.scoreContribution}</span>
                                            </article>
                                        ))
                                    ) : (
                                        <p className="muted">Chưa có rule hits trong hồ sơ này.</p>
                                    )}
                                </div>
                            </>
                        ) : (
                            <div className="drawer-graph">
                                {graph.isPending ? (
                                    <Loading />
                                ) : graph.isError ? (
                                    <ErrorState message={errorMessage(graph.error)} retry={() => graph.refetch()} />
                                ) : graph.data?.nodes.length ? (
                                    <>
                                        <FlowGraph graph={graph.data} />
                                        <div className="graph-key">
                                            <span>
                                                <i />
                                                Giao dịch liên quan
                                            </span>
                                            <span>
                                                <i className="orange" />
                                                Giao dịch gốc
                                            </span>
                                        </div>
                                        <div className="chain-list">
                                            {graph.data.edges.map((e) => (
                                                <div key={e.id}>
                                                    <span>
                                                        {e.transactionRef}
                                                        <small>
                                                            Hop {e.hopLevel} {e.root ? '· Gốc' : ''}
                                                        </small>
                                                    </span>
                                                    <b>{money(e.amount, e.currency)}</b>
                                                </div>
                                            ))}
                                        </div>
                                    </>
                                ) : (
                                    <p className="state-box">Không có liên kết dòng tiền.</p>
                                )}
                            </div>
                        )}
                        {mutation.isError && (
                            <p className="form-error" role="alert">
                                {errorMessage(mutation.error)}
                            </p>
                        )}
                        <div className="drawer-bottom">
                            <div>
                                <CheckCheck size={17} />
                                <span>
                                    {nextReviewStatuses(detail.data.reviewStatus).length
                                        ? 'Chọn bước tiếp theo trong quy trình điều tra.'
                                        : 'Hồ sơ đã có kết luận cuối cùng.'}
                                </span>
                            </div>
                            <div>
                                {nextReviewStatuses(detail.data.reviewStatus).map((status) => (
                                    <button
                                        disabled={mutation.isPending}
                                        key={status}
                                        className={'btn ' + (status === 'FALSE_POSITIVE' ? 'secondary' : 'primary')}
                                        onClick={() => mutation.mutate(status)}
                                    >
                                        {mutation.isPending ? 'Đang lưu…' : statusLabel[status]}
                                        <ArrowRight size={14} />
                                    </button>
                                ))}
                            </div>
                        </div>
                    </>
                )
            )}
        </Modal>
    )
}
