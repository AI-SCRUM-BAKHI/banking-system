import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

import { ReviewBadge, RiskBadge } from '../components/ui/StatusBadges'
import { demoAlerts } from '../data/demo-alerts'
import type { RiskLevel } from '../types/fraud.types'

const currency = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })
const currentDate = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: 'long', year: 'numeric' }).format(
    new Date(),
)

function RiskTrendChart() {
    return (
        <div className="p-3">
            <svg
                className="block w-full"
                viewBox="0 0 660 230"
                role="img"
                aria-labelledby="risk-chart-title risk-chart-desc"
            >
                <title id="risk-chart-title">Xu hướng cảnh báo fraud trong 24 giờ</title>
                <desc id="risk-chart-desc">
                    Biểu đồ minh họa số cảnh báo và cảnh báo nghiêm trọng theo giờ. Dữ liệu hiện tại là dữ liệu mẫu.
                </desc>
                <defs>
                    <linearGradient id="risk-area" x1="0" x2="0" y1="0" y2="1">
                        <stop offset="0%" stopColor="#76b900" stopOpacity="0.24" />
                        <stop offset="100%" stopColor="#76b900" stopOpacity="0" />
                    </linearGradient>
                </defs>
                {[28, 70, 112, 154, 196].map((y) => (
                    <line key={y} className="chart-grid-line" x1="42" x2="642" y1={y} y2={y} />
                ))}
                <text className="chart-axis-label" x="10" y="31">
                    40
                </text>
                <text className="chart-axis-label" x="10" y="73">
                    30
                </text>
                <text className="chart-axis-label" x="10" y="115">
                    20
                </text>
                <text className="chart-axis-label" x="10" y="157">
                    10
                </text>
                <text className="chart-axis-label" x="17" y="199">
                    0
                </text>
                <path
                    className="chart-area"
                    d="M42 162 C86 150 92 130 128 138 S185 148 213 118 S270 128 299 105 S357 119 385 85 S444 93 471 72 S530 91 556 52 S612 63 642 34 V196 H42Z"
                />
                <path
                    className="chart-line-secondary"
                    d="M42 183 C91 177 100 166 128 173 S184 174 213 157 S271 165 299 146 S357 157 385 132 S445 142 471 125 S530 134 556 108 S610 113 642 95"
                />
                <path
                    className="chart-line-primary"
                    d="M42 162 C86 150 92 130 128 138 S185 148 213 118 S270 128 299 105 S357 119 385 85 S444 93 471 72 S530 91 556 52 S612 63 642 34"
                />
                <circle className="chart-point" cx="556" cy="52" r="4" />
                <circle className="chart-point" cx="642" cy="34" r="4" />
                {['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', 'Now'].map((label, index) => (
                    <text
                        className="chart-axis-label"
                        key={label}
                        x={42 + index * 100}
                        y="218"
                        textAnchor={index === 6 ? 'end' : 'middle'}
                    >
                        {label}
                    </text>
                ))}
            </svg>
        </div>
    )
}

function MoneyFlowGraph() {
    return (
        <>
            <div className="mx-3 my-3 rounded-md border border-line bg-canvas/60 p-2">
                <svg className="block w-full" viewBox="0 0 560 240" role="img" aria-labelledby="flow-title flow-desc">
                    <title id="flow-title">Đồ thị luồng tiền của vụ FR-2026-04821</title>
                    <desc id="flow-desc">
                        Tài khoản nguồn chuyển tiền qua hai tài khoản trung gian tới một tài khoản đích. Nút cuối được
                        đánh dấu rủi ro.
                    </desc>
                    <defs>
                        <marker id="edge-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                            <path d="M0,0 L0,6 L7,3 z" fill="#76b900" />
                        </marker>
                    </defs>
                    <path className="graph-edge" d="M104 115 C149 115 157 75 204 75" />
                    <path className="graph-edge secondary" d="M238 75 C287 75 298 145 344 145" />
                    <path className="graph-edge" d="M378 145 C423 145 430 105 476 105" />
                    <text className="graph-edge-label" x="147" y="88">
                        72M VND · 1 hop
                    </text>
                    <text className="graph-edge-label" x="279" y="112">
                        68M VND · 2 hop
                    </text>
                    <text className="graph-edge-label" x="413" y="118">
                        65M VND · 3 hop
                    </text>
                    <g>
                        <circle className="graph-node-halo" cx="86" cy="115" r="27" />
                        <circle className="graph-node-dot" cx="86" cy="115" r="5" />
                        <text className="graph-node-label" x="86" y="158">
                            •••• 0281
                        </text>
                        <text className="graph-node-label" x="86" y="173" fill="#858d82">
                            Nguồn
                        </text>
                    </g>
                    <g>
                        <circle className="graph-node-halo" cx="221" cy="75" r="25" />
                        <circle className="graph-node-dot" cx="221" cy="75" r="5" />
                        <text className="graph-node-label" x="221" y="36">
                            •••• 9204
                        </text>
                        <text className="graph-node-label" x="221" y="21" fill="#858d82">
                            Trung gian
                        </text>
                    </g>
                    <g>
                        <circle className="graph-node-halo" cx="361" cy="145" r="25" />
                        <circle className="graph-node-dot" cx="361" cy="145" r="5" />
                        <text className="graph-node-label" x="361" y="190">
                            •••• 4472
                        </text>
                        <text className="graph-node-label" x="361" y="205" fill="#858d82">
                            Trung gian
                        </text>
                    </g>
                    <g>
                        <circle className="graph-node-halo alert" cx="494" cy="105" r="29" />
                        <circle className="graph-node-dot alert" cx="494" cy="105" r="5" />
                        <text className="graph-node-label" x="494" y="151">
                            •••• 6610
                        </text>
                        <text className="graph-node-label" x="494" y="166" fill="#fa7278">
                            Đích rủi ro
                        </text>
                    </g>
                </svg>
            </div>
            <div className="flex justify-between gap-2.5 px-4 pb-3.5 text-[9px] text-dim">
                <span>Giao dịch gốc · FR-2026-04821</span>
                <span>3 hops · 4 tài khoản</span>
            </div>
        </>
    )
}

export function DashboardPage() {
    const [riskFilter, setRiskFilter] = useState<'ALL' | RiskLevel>('ALL')
    const visibleAlerts = useMemo(
        () => (riskFilter === 'ALL' ? demoAlerts : demoAlerts.filter((alert) => alert.risk === riskFilter)),
        [riskFilter],
    )

    return (
        <div className="space-y-3.5">
            <div className="mb-6 flex items-end justify-between gap-6 max-md:mb-5 max-md:items-start max-md:flex-col max-md:gap-4">
                <div>
                    <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-dim">
                        Tổng quan vận hành · {currentDate}
                    </div>
                    <h1 className="mt-2 text-[clamp(22px,2vw,30px)] font-semibold leading-tight tracking-[-0.06em] text-balance">
                        Fraud detection dashboard
                    </h1>
                    <p className="mt-2.5 max-w-[68ch] text-xs text-muted">
                        Theo dõi cảnh báo, đánh giá rủi ro và lần theo luồng tiền đáng ngờ.
                    </p>
                </div>
                <div className="flex items-center gap-2 max-md:w-full">
                    <Link
                        className="inline-flex min-h-10 items-center justify-center gap-2 rounded-md border border-accent bg-accent px-3.5 text-[11px] font-bold text-[#17220b] no-underline transition duration-200 hover:-translate-y-px hover:border-[#8bd20a] hover:bg-[#8bd20a] active:translate-y-0 max-md:w-full"
                        to="/fraud-alerts"
                    >
                        <svg
                            className="size-[15px]"
                            viewBox="0 0 20 20"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.6"
                            aria-hidden="true"
                        >
                            <path d="M10 4v12M4 10h12" />
                        </svg>
                        Mở danh sách cảnh báo
                    </Link>
                </div>
            </div>

            <div
                className="mb-5 flex items-center gap-2.5 rounded-md border border-signal/20 bg-signal/5 px-3 py-2.5 text-[10px] text-muted"
                role="note"
            >
                <strong className="text-[9px] uppercase tracking-[0.12em] text-signal">Preview data</strong>
                <span>Số liệu và giao dịch bên dưới là dữ liệu mẫu; frontend chưa kết nối API backend.</span>
            </div>

            <section
                className="mb-3.5 grid grid-cols-4 gap-3 max-[1100px]:grid-cols-2 max-md:gap-2 max-[420px]:grid-cols-2"
                aria-label="Chỉ số cảnh báo"
            >
                {[
                    {
                        label: 'Cảnh báo đang mở',
                        value: '1,284',
                        delta: '+12.8%',
                        note: 'so với 24 giờ trước',
                        code: 'ALERTS / 24H',
                    },
                    {
                        label: 'Mức nghiêm trọng',
                        value: '37',
                        delta: '+4 vụ',
                        note: 'cần ưu tiên xem xét',
                        code: 'CRITICAL / NOW',
                        alert: true,
                    },
                    {
                        label: 'Đang điều tra',
                        value: '216',
                        delta: '−8.2%',
                        note: 'so với tuần trước',
                        code: 'IN REVIEW',
                    },
                    {
                        label: 'Đã xác nhận fraud',
                        value: '89.4%',
                        delta: '+2.4%',
                        note: 'độ chính xác phát hiện',
                        code: 'CONFIRMED',
                    },
                ].map((metric) => (
                    <article
                        className="relative min-w-0 overflow-hidden rounded-md border border-line bg-gradient-to-br from-panel-raised/80 to-panel p-4 shadow-[0_10px_30px_rgba(0,0,0,0.16)] max-md:p-3"
                        key={metric.code}
                    >
                        <div className="absolute left-3.5 right-3.5 top-0 h-px bg-gradient-to-r from-transparent via-accent/45 to-transparent" />
                        <div className="flex items-center justify-between gap-2">
                            <span className="text-[10px] text-muted max-[420px]:text-[9px]">{metric.label}</span>
                            <span className="text-[9px] tracking-[0.08em] text-dim max-md:hidden">{metric.code}</span>
                        </div>
                        <div className="mt-3.5 flex items-baseline gap-2.5">
                            <span className="text-[clamp(23px,2.4vw,31px)] font-semibold tabular-nums leading-none tracking-[-0.08em] text-copy">
                                {metric.value}
                            </span>
                            <span className={`text-[9px] ${metric.alert ? 'text-risk-medium' : 'text-accent-bright'}`}>
                                {metric.delta}
                            </span>
                        </div>
                        <div className="mt-2 text-[9px] text-dim">{metric.note}</div>
                    </article>
                ))}
            </section>

            <div className="mb-3.5 grid grid-cols-[minmax(0,1.12fr)_minmax(0,0.88fr)] gap-3.5 max-[1100px]:grid-cols-1">
                <section
                    className="min-w-0 rounded-md border border-line bg-gradient-to-br from-panel-raised/80 to-panel shadow-[0_10px_30px_rgba(0,0,0,0.16)]"
                    aria-labelledby="trend-heading"
                >
                    <div className="flex min-h-[57px] items-center justify-between gap-4 border-b border-line px-4 py-3 max-md:items-start max-md:flex-col max-md:gap-2.5">
                        <div>
                            <h2 className="m-0 text-xs font-medium text-copy" id="trend-heading">
                                Tần suất cảnh báo
                            </h2>
                            <p className="mt-1 text-[9px] text-dim">Diễn biến phát hiện trong 24 giờ gần nhất</p>
                        </div>
                        <div className="flex items-center gap-3.5 text-[9px] text-muted" aria-label="Chú giải biểu đồ">
                            <span className="inline-flex items-center gap-1.5">
                                <span className="size-[7px] rounded-full bg-accent" />
                                Tất cả cảnh báo
                            </span>
                            <span className="inline-flex items-center gap-1.5">
                                <span className="size-[7px] rounded-full bg-signal" />
                                Nghiêm trọng
                            </span>
                        </div>
                    </div>
                    <RiskTrendChart />
                </section>

                <section
                    className="min-w-0 rounded-md border border-line bg-gradient-to-br from-panel-raised/80 to-panel shadow-[0_10px_30px_rgba(0,0,0,0.16)]"
                    aria-labelledby="graph-heading"
                >
                    <div className="flex min-h-[57px] items-center justify-between gap-4 border-b border-line px-4 py-3 max-md:items-start max-md:flex-col max-md:gap-2.5">
                        <div>
                            <h2 className="m-0 text-xs font-medium text-copy" id="graph-heading">
                                Luồng tiền liên quan
                            </h2>
                            <p className="mt-1 text-[9px] text-dim">Mạng lưới tài khoản quanh cảnh báo rủi ro cao</p>
                        </div>
                        <span className="text-[9px] uppercase tracking-[0.08em] text-dim">Graph preview</span>
                    </div>
                    <MoneyFlowGraph />
                </section>
            </div>

            <section
                className="overflow-hidden rounded-md border border-line bg-gradient-to-br from-panel-raised/80 to-panel shadow-[0_10px_30px_rgba(0,0,0,0.16)]"
                aria-labelledby="alerts-heading"
            >
                <div className="flex min-h-[57px] items-center justify-between gap-4 border-b border-line px-4 py-3 max-md:items-start max-md:flex-col max-md:gap-2.5">
                    <div>
                        <h2 className="m-0 text-xs font-medium text-copy" id="alerts-heading">
                            Cảnh báo mới nhất
                        </h2>
                        <p className="mt-1 text-[9px] text-dim">Các giao dịch đang chờ hoặc cần tiếp tục điều tra</p>
                    </div>
                    <div className="flex items-center gap-2 max-md:w-full">
                        <label
                            className="text-[10px] font-semibold uppercase tracking-[0.14em] text-dim"
                            htmlFor="risk-filter"
                        >
                            Mức rủi ro
                        </label>
                        <select
                            className="min-h-[34px] rounded-sm border border-line bg-panel-raised px-2.5 text-[10px] text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-bright max-md:flex-1"
                            id="risk-filter"
                            value={riskFilter}
                            onChange={(event) => setRiskFilter(event.target.value as 'ALL' | RiskLevel)}
                        >
                            <option value="ALL">Tất cả mức</option>
                            <option value="CRITICAL">Nghiêm trọng</option>
                            <option value="HIGH">Cao</option>
                            <option value="MEDIUM">Trung bình</option>
                            <option value="LOW">Thấp</option>
                        </select>
                    </div>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full min-w-[900px] border-collapse text-left">
                        <thead>
                            <tr>
                                <th
                                    className="border-b border-line bg-canvas/45 px-4 py-3 text-[9px] font-medium uppercase tracking-[0.08em] text-dim"
                                    scope="col"
                                >
                                    Giao dịch
                                </th>
                                <th
                                    className="border-b border-line bg-canvas/45 px-4 py-3 text-[9px] font-medium uppercase tracking-[0.08em] text-dim"
                                    scope="col"
                                >
                                    Luồng tài khoản
                                </th>
                                <th
                                    className="border-b border-line bg-canvas/45 px-4 py-3 text-[9px] font-medium uppercase tracking-[0.08em] text-dim"
                                    scope="col"
                                >
                                    Số tiền
                                </th>
                                <th
                                    className="border-b border-line bg-canvas/45 px-4 py-3 text-[9px] font-medium uppercase tracking-[0.08em] text-dim"
                                    scope="col"
                                >
                                    Risk score
                                </th>
                                <th
                                    className="border-b border-line bg-canvas/45 px-4 py-3 text-[9px] font-medium uppercase tracking-[0.08em] text-dim"
                                    scope="col"
                                >
                                    Mức rủi ro
                                </th>
                                <th
                                    className="border-b border-line bg-canvas/45 px-4 py-3 text-[9px] font-medium uppercase tracking-[0.08em] text-dim"
                                    scope="col"
                                >
                                    Trạng thái
                                </th>
                                <th
                                    className="border-b border-line bg-canvas/45 px-4 py-3 text-[9px] font-medium uppercase tracking-[0.08em] text-dim"
                                    scope="col"
                                >
                                    <span className="sr-only">Thao tác</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {visibleAlerts.map((alert) => (
                                <tr className="transition-colors hover:bg-accent/5" key={alert.ref}>
                                    <td className="border-b border-line/60 px-4 py-3 text-[10px] text-muted">
                                        <span className="text-[10px] font-medium text-copy">{alert.ref}</span>
                                        <br />
                                        <span className="text-[9px] uppercase tracking-[0.08em] text-dim">
                                            Hôm nay · {alert.time}
                                        </span>
                                    </td>
                                    <td className="border-b border-line/60 px-4 py-3 text-[10px] text-muted">
                                        <span className="flex items-center gap-[7px]">
                                            {alert.from}
                                            <span className="text-dim" aria-hidden="true">
                                                →
                                            </span>
                                            {alert.to}
                                        </span>
                                    </td>
                                    <td className="border-b border-line/60 px-4 py-3 text-[10px] tabular-nums text-copy">
                                        ₫{currency.format(alert.amount)}
                                    </td>
                                    <td className="border-b border-line/60 px-4 py-3 text-[10px] text-muted">
                                        <span className="flex items-center gap-2 tabular-nums text-copy">
                                            {alert.score}
                                            <span
                                                className="h-[3px] w-10 overflow-hidden rounded-full bg-line-strong"
                                                aria-hidden="true"
                                            >
                                                <span
                                                    className={`block h-full rounded-full ${alert.risk === 'CRITICAL' ? 'bg-risk-critical' : 'bg-risk-medium'}`}
                                                    style={{ width: `${alert.score}%` }}
                                                />
                                            </span>
                                        </span>
                                    </td>
                                    <td className="border-b border-line/60 px-4 py-3 text-[10px] text-muted">
                                        <RiskBadge level={alert.risk} />
                                    </td>
                                    <td className="border-b border-line/60 px-4 py-3 text-[10px] text-muted">
                                        <ReviewBadge status={alert.status} />
                                    </td>
                                    <td className="border-b border-line/60 px-4 py-3 text-[10px] text-muted">
                                        <Link
                                            className="grid size-8 place-items-center rounded-sm text-dim transition-colors hover:bg-panel-raised hover:text-accent-bright"
                                            to="/investigations"
                                            aria-label={`Mở điều tra ${alert.ref}`}
                                            title="Mở hồ sơ"
                                        >
                                            <svg
                                                viewBox="0 0 20 20"
                                                className="size-4"
                                                fill="none"
                                                stroke="currentColor"
                                                strokeWidth="1.5"
                                                aria-hidden="true"
                                            >
                                                <path d="M3.5 10s2.3-5 6.5-5 6.5 5 6.5 5-2.3 5-6.5 5-6.5-5-6.5-5Z" />
                                                <circle cx="10" cy="10" r="1.8" />
                                            </svg>
                                        </Link>
                                    </td>
                                </tr>
                            ))}
                            {visibleAlerts.length === 0 && (
                                <tr>
                                    <td
                                        className="border-b border-line/60 px-4 py-3 text-[10px] text-muted"
                                        colSpan={7}
                                    >
                                        Không có cảnh báo ở mức rủi ro này.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
                <div className="flex items-center justify-between gap-3 border-t border-line px-4 py-3 text-[9px] text-dim max-md:items-start max-md:flex-col">
                    <span>Đang hiển thị {visibleAlerts.length} cảnh báo mẫu</span>
                    <div className="flex items-center gap-[5px]" aria-label="Phân trang xem trước">
                        <span>Trang</span>
                        <button
                            className="min-h-[30px] min-w-[30px] rounded-sm border border-accent/50 bg-accent/10 text-[9px] text-accent-bright"
                            type="button"
                            aria-current="page"
                            aria-label="Trang 1"
                        >
                            1
                        </button>
                        <button
                            className="min-h-[30px] min-w-[30px] rounded-sm border border-line bg-panel-raised text-[9px] text-muted disabled:cursor-not-allowed disabled:opacity-40"
                            type="button"
                            disabled
                            aria-label="Trang kế tiếp"
                        >
                            →
                        </button>
                    </div>
                </div>
            </section>
        </div>
    )
}
