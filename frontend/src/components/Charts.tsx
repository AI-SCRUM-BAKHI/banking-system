import { useId, useState } from 'react'
import type { DashboardSummary, FraudSummary } from '../types/fraud.types'
import { number } from '../lib/format'
import { statusLabel } from '../lib/labels'
export function VolumeChart({ rows }: { rows: FraudSummary[] }) {
    const id = useId().replaceAll(':', '')
    const [hover, setHover] = useState(-1)
    const data = Object.values(
        rows.reduce<Record<string, { day: string; total: number; high: number }>>((acc, row) => {
            const day = new Date(new Date(row.transactionTime).getTime() + 7 * 3600000).toISOString().slice(0, 10)
            const entry = (acc[day] ??= { day, total: 0, high: 0 })
            entry.total++
            entry.high += row.riskLevel === 'CRITICAL' || row.riskLevel === 'HIGH' ? 1 : 0
            return acc
        }, {}),
    ).sort((a, b) => a.day.localeCompare(b.day))
    const max = Math.max(...data.map((d) => d.total), 4)
    const x = (i: number) => 45 + (i * 570) / Math.max(data.length - 1, 1)
    const y = (v: number) => 188 - (v / max) * 145
    const path = (key: 'total' | 'high') => data.map((d, i) => `${i ? 'L' : 'M'}${x(i)},${y(d[key])}`).join(' ')
    return (
        <div className="volume-chart">
            <svg viewBox="0 0 650 230" role="img" aria-label="Biểu đồ số hồ sơ và hồ sơ rủi ro cao theo ngày">
                <defs>
                    <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
                        <stop stopColor="#3579ec" stopOpacity=".18" />
                        <stop offset="1" stopColor="#3579ec" stopOpacity="0" />
                    </linearGradient>
                </defs>
                {[0, 0.25, 0.5, 0.75, 1].map((r) => (
                    <g key={r}>
                        <line x1="45" x2="625" y1={y(max * r)} y2={y(max * r)} stroke="#e8edf5" strokeDasharray="3 5" />
                        <text x="29" y={y(max * r) + 4} textAnchor="end" fill="#9aa5b6" fontSize="12">
                            {Math.round(max * r)}
                        </text>
                    </g>
                ))}
                {data.length > 0 && (
                    <>
                        <path
                            className="chart-fill"
                            d={`${path('total')} L${x(data.length - 1)},188 L45,188 Z`}
                            fill={`url(#${id})`}
                        />
                        <path
                            className="chart-draw"
                            d={path('total')}
                            fill="none"
                            stroke="#3478e5"
                            strokeWidth="2.5"
                            strokeLinejoin="round"
                        />
                        <path
                            className="chart-draw delayed"
                            d={path('high')}
                            fill="none"
                            stroke="#ea8b63"
                            strokeWidth="2"
                            strokeDasharray="4 4"
                            strokeLinejoin="round"
                        />
                    </>
                )}
                {data.map((d, i) => (
                    <g key={d.day}>
                        <text x={x(i)} y="215" textAnchor="middle" fontSize="12" fill="#8b96a8">
                            {d.day.slice(8)} Th{Number(d.day.slice(5, 7))}
                        </text>
                        <rect
                            x={x(i) - 30}
                            y="20"
                            width="60"
                            height="175"
                            fill="transparent"
                            tabIndex={0}
                            aria-label={`${d.day}: ${d.total} hồ sơ, ${d.high} rủi ro cao`}
                            onFocus={() => setHover(i)}
                            onBlur={() => setHover(-1)}
                            onMouseEnter={() => setHover(i)}
                            onMouseLeave={() => setHover(-1)}
                        />
                        {hover === i && (
                            <g pointerEvents="none">
                                <line x1={x(i)} x2={x(i)} y1="22" y2="188" stroke="#b5c8e9" strokeDasharray="4 3" />
                                <circle cx={x(i)} cy={y(d.total)} r="5" fill="white" stroke="#3478e5" strokeWidth="2" />
                                <rect
                                    x={Math.max(0, Math.min(x(i) - 70, 510))}
                                    y="0"
                                    width="140"
                                    height="27"
                                    rx="6"
                                    fill="#182f52"
                                />
                                <text
                                    x={Math.max(70, Math.min(x(i), 580))}
                                    y="17"
                                    textAnchor="middle"
                                    fill="white"
                                    fontSize="12"
                                >
                                    {d.total} hồ sơ · {d.high} cao
                                </text>
                            </g>
                        )}
                    </g>
                ))}
            </svg>
        </div>
    )
}
export function ReviewDonut({ summary }: { summary: DashboardSummary }) {
    const values = [summary.newCases, summary.reviewingCases, summary.confirmedFraud, summary.falsePositives]
    const colors = ['#3c78e5', '#96b8f0', '#ed936e', '#8bb9a7']
    const labels = [statusLabel.NEW, statusLabel.REVIEWING, statusLabel.CONFIRMED_FRAUD, statusLabel.FALSE_POSITIVE]
    let start = 0
    const gradient = values
        .map((v, i) => {
            const from = start
            start += summary.total ? (v / summary.total) * 100 : 0
            return `${colors[i]} ${from}% ${start}%`
        })
        .join(',')
    return (
        <div className="review-donut-layout">
            <div
                className="review-donut"
                style={{ background: summary.total ? `conic-gradient(${gradient})` : '#e9edf3' }}
            >
                <div>
                    <strong>{number(summary.total)}</strong>
                    <span>HỒ SƠ</span>
                </div>
            </div>
            <div className="donut-legend">
                {values.map((v, i) => (
                    <div key={i}>
                        <span style={{ background: colors[i] }} />
                        <span>{labels[i]}</span>
                        <b>{v}</b>
                    </div>
                ))}
            </div>
        </div>
    )
}
export function RiskDistribution({ rows }: { rows: FraudSummary[] }) {
    const levels = [
        { key: 'LOW', label: 'Thấp', color: '#8bb9a7' },
        { key: 'MEDIUM', label: 'Trung bình', color: '#d9b36c' },
        { key: 'HIGH', label: 'Cao', color: '#e99c78' },
        { key: 'CRITICAL', label: 'Nghiêm trọng', color: '#d97674' },
    ]
    return (
        <div className="risk-distribution">
            {levels.map((l) => {
                const count = rows.filter((r) => r.riskLevel === l.key).length
                return (
                    <div key={l.key}>
                        <div>
                            <span>
                                <i style={{ background: l.color }} />
                                {l.label}
                            </span>
                            <span>
                                <b>{count}</b> / {rows.length}
                            </span>
                        </div>
                        <div className="distribution-track">
                            <i
                                style={{
                                    width: `${rows.length ? (count / rows.length) * 100 : 0}%`,
                                    background: l.color,
                                }}
                            />
                        </div>
                    </div>
                )
            })}
        </div>
    )
}
