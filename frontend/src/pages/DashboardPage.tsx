import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    Activity,
    ArrowRight,
    ArrowUpRight,
    CheckCheck,
    Clock3,
    Download,
    Network,
    RefreshCw,
    ShieldAlert,
} from 'lucide-react'
import { AnimatedNumber, ArrowLink, Empty, ErrorState, Loading, Panel, Pill } from '../components/ui'
import { VolumeChart, ReviewDonut, RiskDistribution } from '../components/Charts'
import { CaseTable } from '../components/CaseTable'
import { useConsole } from '../lib/console-context'
import { useCases, useSummary } from '../lib/queries'
import { errorMessage } from '../api/axios'
import { exportCsv, shortMoney } from '../lib/format'
export function DashboardPage() {
    const { session, openCase, notify } = useConsole()
    const navigate = useNavigate()
    const [period, setPeriod] = useState('all')
    const until = session.mode === 'demo' ? new Date('2026-11-04T23:59:59+07:00') : new Date()
    const from = period === 'all' ? undefined : new Date(until.getTime() - Number(period) * 86400000).toISOString()
    const range = { from, to: period === 'all' ? undefined : until.toISOString() }
    const summary = useSummary(session.mode, range)
    const cases = useCases(session.mode, { ...range, size: 100, sort: 'transactionTime,desc' })
    const rows = cases.data?.content || []
    const stats = summary.data
    return (
        <>
            <div className="page-title">
                <div>
                    <div className="eyebrow">RISK OPERATIONS / OVERVIEW</div>
                    <h1>
                        Toàn cảnh. Trong tầm tay<span>.</span>
                    </h1>
                    <p>Mỗi tín hiệu là một cơ hội để nhìn rõ hơn.</p>
                </div>
                <div className="page-actions">
                    <button
                        className="icon-button refresh-button"
                        aria-label="Làm mới dashboard"
                        onClick={() => {
                            summary.refetch()
                            cases.refetch()
                        }}
                    >
                        <RefreshCw size={16} className={summary.isFetching ? 'spin' : ''} />
                    </button>
                    <select aria-label="Thời gian dashboard" value={period} onChange={(e) => setPeriod(e.target.value)}>
                        <option value="all">Toàn bộ thời gian</option>
                        <option value="7">7 ngày gần nhất</option>
                        <option value="1">24 giờ gần nhất</option>
                    </select>
                    <button
                        className="btn secondary small"
                        disabled={!rows.length}
                        onClick={() => {
                            exportCsv(
                                rows.map((r) => ({
                                    transactionRef: r.transactionRef,
                                    amount: r.amount,
                                    riskScore: r.riskScore,
                                    riskLevel: r.riskLevel,
                                    reviewStatus: r.reviewStatus,
                                    transactionTime: r.transactionTime,
                                })),
                                'aegis-recent-cases.csv',
                            )
                            notify('Đã xuất tối đa 100 hồ sơ đang hiển thị.')
                        }}
                    >
                        <Download size={15} />
                        Xuất mẫu CSV
                    </button>
                </div>
            </div>
            {summary.isPending ? (
                <Loading />
            ) : summary.isError ? (
                <Panel>
                    <ErrorState message={errorMessage(summary.error)} retry={() => summary.refetch()} />
                </Panel>
            ) : (
                stats && (
                    <>
                        <div className="stats-grid">
                            {[
                                {
                                    label: 'Hồ sơ cảnh báo',
                                    value: stats.total,
                                    Icon: Activity,
                                    note: 'Tổng hồ sơ trong khoảng đã chọn',
                                    tone: 'blue',
                                    bars: [4, 6, 5, 8, 7, 10, 8, 12],
                                },
                                {
                                    label: 'Cần ưu tiên',
                                    value: stats.highRisk + stats.criticalRisk,
                                    Icon: ShieldAlert,
                                    note: `${stats.criticalRisk} hồ sơ mức nghiêm trọng`,
                                    tone: 'orange',
                                    bars: [3, 4, 7, 6, 8, 10, 9, 12],
                                },
                                {
                                    label: 'Đang điều tra',
                                    value: stats.reviewingCases,
                                    Icon: Clock3,
                                    note: `${stats.newCases} hồ sơ mới chờ tiếp nhận`,
                                    tone: 'blue',
                                    bars: [8, 6, 7, 5, 8, 10, 9, 11],
                                },
                                {
                                    label: 'Đã xác nhận fraud',
                                    value: stats.confirmedFraud,
                                    Icon: CheckCheck,
                                    note: 'Kết luận bởi người điều tra',
                                    tone: 'green',
                                    bars: [2, 4, 5, 4, 7, 6, 8, 10],
                                },
                            ].map(({ label, value, Icon, note, tone, bars }, i) => (
                                <article
                                    className={'stat-card stat-' + tone}
                                    key={label}
                                    style={{ animationDelay: `${i * 70}ms` }}
                                >
                                    <div className="stat-label">
                                        {label}
                                        <span>
                                            <Icon size={17} />
                                        </span>
                                    </div>
                                    <div className="stat-value">
                                        <strong>
                                            <AnimatedNumber value={value} />
                                        </strong>
                                        <div className="mini-bars" aria-hidden="true">
                                            {bars.map((h, j) => (
                                                <i key={j} style={{ height: h * 2.4, animationDelay: `${j * 70}ms` }} />
                                            ))}
                                        </div>
                                    </div>
                                    <div className="stat-note">
                                        <i />
                                        {note}
                                    </div>
                                </article>
                            ))}
                        </div>
                        <div className="dashboard-main-grid">
                            <Panel
                                title="Dòng cảnh báo"
                                subtitle={`Phân bố theo ngày · ${rows.length} hồ sơ gần nhất trong khoảng chọn`}
                                action={
                                    <div className="chart-legend">
                                        <span>
                                            <i />
                                            Hồ sơ
                                        </span>
                                        <span>
                                            <i />
                                            Rủi ro cao
                                        </span>
                                    </div>
                                }
                                className="activity-panel"
                            >
                                {cases.isPending ? (
                                    <Loading />
                                ) : cases.isError ? (
                                    <ErrorState message={errorMessage(cases.error)} retry={() => cases.refetch()} />
                                ) : rows.length ? (
                                    <VolumeChart rows={rows} />
                                ) : (
                                    <Empty />
                                )}
                                <div className="chart-foot">
                                    <span>
                                        <i className="live-dot" />
                                        {session.mode === 'demo' ? 'Snapshot demo' : 'Mẫu truy vấn từ backend'}
                                    </span>
                                    <span>Hover để xem chi tiết</span>
                                </div>
                            </Panel>
                            <Panel
                                title="Trạng thái điều tra"
                                subtitle="Phân bố toàn bộ hồ sơ theo bộ lọc thời gian"
                                action={<span className="panel-ellipsis">···</span>}
                            >
                                <ReviewDonut summary={stats} />
                                <div className="review-progress">
                                    <div>
                                        <span>Hồ sơ đã có kết luận</span>
                                        <b>
                                            {stats.total
                                                ? Math.round(
                                                      ((stats.confirmedFraud + stats.falsePositives) / stats.total) *
                                                          100,
                                                  )
                                                : 0}
                                            %
                                        </b>
                                    </div>
                                    <div>
                                        <i
                                            style={{
                                                width: `${stats.total ? ((stats.confirmedFraud + stats.falsePositives) / stats.total) * 100 : 0}%`,
                                            }}
                                        />
                                    </div>
                                </div>
                            </Panel>
                        </div>
                        <div className="dashboard-secondary-grid">
                            <Panel
                                title="Phân bố mức rủi ro"
                                subtitle={`Tính trên mẫu ${rows.length} hồ sơ đang hiển thị`}
                            >
                                <RiskDistribution rows={rows} />
                            </Panel>
                            <section className="network-callout">
                                <div className="callout-content">
                                    <Pill tone="glass">
                                        <Network size={12} /> CONNECTED INTELLIGENCE
                                    </Pill>
                                    <h2>
                                        Đằng sau một giao dịch,
                                        <br />
                                        có thể là cả mạng lưới.
                                    </h2>
                                    <p>
                                        Theo dấu tài khoản trung gian và những
                                        <br />
                                        luồng tiền cần được nhìn kỹ hơn.
                                    </p>
                                    <button onClick={() => navigate('/console/network')}>
                                        Khám phá dòng tiền <ArrowUpRight size={16} />
                                    </button>
                                </div>
                                <div className="callout-network" aria-hidden="true">
                                    <svg viewBox="0 0 220 200">
                                        <path d="m45 50 80 60 65-70 M125 110l55 55 M125 110 35 160" />
                                        {[
                                            [45, 50],
                                            [125, 110],
                                            [190, 40],
                                            [180, 165],
                                            [35, 160],
                                        ].map(([x, y], i) => (
                                            <g key={i}>
                                                <circle
                                                    className={i === 1 ? 'center-node' : ''}
                                                    cx={x}
                                                    cy={y}
                                                    r={i === 1 ? 22 : 12}
                                                />
                                                <circle cx={x} cy={y} r="3" />
                                            </g>
                                        ))}
                                    </svg>
                                </div>
                            </section>
                        </div>
                        <Panel
                            title="Hồ sơ cần chú ý"
                            subtitle="Ưu tiên rủi ro cao trong mẫu 100 hồ sơ gần nhất"
                            action={
                                <ArrowLink onClick={() => navigate('/console/transactions')}>Tất cả hồ sơ</ArrowLink>
                            }
                        >
                            {cases.isPending ? (
                                <Loading />
                            ) : cases.isError ? (
                                <ErrorState message={errorMessage(cases.error)} retry={() => cases.refetch()} />
                            ) : (
                                <CaseTable
                                    rows={[...rows].sort((a, b) => b.riskScore - a.riskScore).slice(0, 5)}
                                    onSelect={openCase}
                                />
                            )}
                        </Panel>
                        <div className="data-scope-note">
                            <span>
                                <Activity size={14} /> Các biểu đồ dùng mẫu tối đa 100 hồ sơ; chỉ số tổng hợp lấy từ
                                endpoint summary.
                            </span>
                            <span>
                                Giá trị mẫu: <b>{shortMoney(rows.reduce((s, r) => s + r.amount, 0))} ₫</b>
                                <ArrowRight size={12} />
                            </span>
                        </div>
                    </>
                )
            )}
        </>
    )
}
