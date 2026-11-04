import { ArrowUpRight, ArrowRight } from 'lucide-react'
import { dateTime, money } from '../lib/format'
import { RiskBadge, StatusBadge, Empty } from './ui'
import type { FraudSummary } from '../types/fraud.types'
export function CaseTable({
    rows,
    onSelect,
    showIp = false,
}: {
    rows: FraudSummary[]
    onSelect: (id: string) => void
    showIp?: boolean
}) {
    return rows.length ? (
        <div className="table-scroll">
            <table>
                <thead>
                    <tr>
                        <th>GIAO DỊCH / THỜI GIAN</th>
                        <th>TÀI KHOẢN</th>
                        {showIp && <th>ĐỊA CHỈ IP</th>}
                        <th>GIÁ TRỊ</th>
                        <th>RỦI RO</th>
                        <th>ĐIỀU TRA</th>
                        <th />
                    </tr>
                </thead>
                <tbody>
                    {rows.map((row) => (
                        <tr key={row.id}>
                            <td>
                                <button className="table-ref" onClick={() => onSelect(row.id)}>
                                    {row.transactionRef}
                                </button>
                                <small>{dateTime(row.transactionTime)}</small>
                            </td>
                            <td>
                                <div className="table-accounts">
                                    <span>{row.fromAccountMasked}</span>
                                    <ArrowRight size={12} />
                                    <span>{row.toAccountMasked}</span>
                                </div>
                            </td>
                            {showIp && (
                                <td>
                                    <span className="mono ip-text">{row.demoIp || '—'}</span>
                                </td>
                            )}
                            <td className="table-amount">{money(row.amount, row.currency)}</td>
                            <td>
                                <div className="table-risk">
                                    <RiskBadge risk={row.riskLevel} />
                                    <span className="mono">{row.riskScore}</span>
                                </div>
                            </td>
                            <td>
                                <StatusBadge status={row.reviewStatus} />
                            </td>
                            <td>
                                <button
                                    className="icon-button row-arrow"
                                    aria-label={'Xem ' + row.transactionRef}
                                    onClick={() => onSelect(row.id)}
                                >
                                    <ArrowUpRight size={16} />
                                </button>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    ) : (
        <Empty />
    )
}
