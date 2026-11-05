import { useState } from 'react'
import { Globe, Info, Network, Search, ArrowUpRight, ShieldAlert } from 'lucide-react'
import { useConsole } from '../lib/console-context'
import { useCases, useIpIntelligence } from '../lib/queries'
import { groupDemoIps } from '../lib/investigation'
import { Empty, ErrorState, Loading, Panel, Pill } from '../components/ui'
import { CaseTable } from '../components/CaseTable'
import { shortMoney } from '../lib/format'
import { errorMessage } from '../api/axios'
export function IpPage() {
    const { session, openCase } = useConsole()
    const [ip, setIp] = useState('')
    const [search, setSearch] = useState('')
    const live = session.mode === 'live'
    const demoQuery = useCases('demo', { size: 1000 }, !live)
    const liveQuery = useIpIntelligence(live)
    const query = live ? liveQuery : demoQuery
    const groups = live
        ? liveQuery.data?.groups || []
        : groupDemoIps(demoQuery.data?.content || []).map((g) => ({
              ...g,
              sourceFraud: 0,
              engineFraud: 0,
              accountLabels: [] as string[],
          }))
    const repeated = groups.filter((g) => (live ? g.sourceFraud > 1 : g.count > 1))
    const filtered = groups.filter((g) => g.ip.includes(search.trim()))
    const current = filtered.find((g) => g.ip === ip) || filtered[0]
    const accounts = current?.accountLabels.length
        ? current.accountLabels
        : [...new Set(current?.records.flatMap((row) => [row.fromAccountMasked, row.toAccountMasked]) || [])].slice(
              0,
              6,
          )
    return (
        <>
            <div className="page-title">
                <div>
                    <div className="eyebrow">NETWORK SIGNALS / IP INTELLIGENCE</div>
                    <h1>
                        Một địa chỉ. Nhiều dấu vết<span>.</span>
                    </h1>
                    <p>Đối chiếu các hồ sơ dùng chung IP và những kết luận fraud liên quan.</p>
                </div>
                <Pill tone={live ? 'green' : 'amber'}>
                    <Globe size={13} /> {live ? 'LIVE · CSV SOURCE' : 'DEMO CAPABILITY'}
                </Pill>
            </div>
            <div className="info-banner amber">
                <Info size={19} />
                <p>
                    {live ? (
                        <>
                            <b>IP từ dữ liệu CSV đã nhập.</b> Đối chiếu{' '}
                            {liveQuery.data?.transactionsWithIp.toLocaleString('vi-VN') || '…'} giao dịch có IP. Nhãn
                            fraud của CSV, dự đoán của engine và kết luận điều tra được thống kê riêng.
                        </>
                    ) : (
                        <>
                            <b>Dữ liệu demo mô phỏng.</b> Các địa chỉ trong chế độ demo thuộc dải IP minh họa.
                        </>
                    )}
                </p>
            </div>
            {query.isPending ? (
                <Loading />
            ) : query.isError ? (
                <ErrorState message={errorMessage(query.error)} retry={() => query.refetch()} />
            ) : (
                <>
                    <div className="ip-summary-grid">
                        <div>
                            <Globe size={20} />
                            <strong>{groups.length}</strong>
                            <span>{live ? 'IP trong dữ liệu đã nhập' : 'IP trong dữ liệu mẫu'}</span>
                        </div>
                        <div>
                            <Network size={20} />
                            <strong>{repeated.length}</strong>
                            <span>{live ? 'IP có nhiều giao dịch nhãn fraud' : 'Cụm IP dùng chung'}</span>
                        </div>
                        <div>
                            <ShieldAlert size={20} />
                            <strong>
                                {live
                                    ? liveQuery.data?.sourceFraudTransactions || 0
                                    : groups.filter((g) => g.confirmed > 0).length}
                            </strong>
                            <span>{live ? 'Giao dịch nhãn fraud CSV' : 'IP có hồ sơ xác nhận fraud'}</span>
                        </div>
                    </div>
                    <div className="ip-workspace">
                        <Panel
                            title="Cụm IP cần đối chiếu"
                            subtitle={
                                live
                                    ? 'Ưu tiên IP có nhiều giao dịch gắn nhãn fraud từ CSV'
                                    : 'Xếp theo số hồ sơ đã xác nhận fraud'
                            }
                        >
                            <label className="search-input ip-search">
                                <Search size={16} />
                                <input
                                    aria-label="Tìm địa chỉ IP"
                                    placeholder="Tìm địa chỉ IP…"
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                />
                            </label>
                            <div className="ip-group-list">
                                {!filtered.length && (
                                    <Empty
                                        title="Không tìm thấy IP"
                                        text="Thử địa chỉ khác hoặc xóa bộ lọc tìm kiếm."
                                    />
                                )}
                                {filtered.map((g) => (
                                    <button
                                        className={current?.ip === g.ip ? 'active' : ''}
                                        key={g.ip}
                                        onClick={() => setIp(g.ip)}
                                    >
                                        <div>
                                            <span className="ip-globe">
                                                <Globe size={17} />
                                            </span>
                                            <b className="mono">{g.ip}</b>
                                            <ArrowUpRight size={15} />
                                        </div>
                                        <p>
                                            {g.accounts} tài khoản · {g.count} giao dịch
                                        </p>
                                        {live && <p className="ip-source-label">{g.sourceFraud} nhãn fraud CSV</p>}
                                        <div className="ip-metrics">
                                            <span>
                                                <i />
                                                {g.confirmed} xác nhận fraud
                                            </span>
                                            <span>{g.highRisk} rủi ro cao</span>
                                        </div>
                                        <div className="ip-group-bar">
                                            <i
                                                style={{
                                                    width: `${((live ? g.sourceFraud : g.confirmed) / g.count) * 100}%`,
                                                }}
                                            />
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </Panel>
                        <Panel
                            title={current?.ip || 'Chọn địa chỉ IP'}
                            subtitle="Mối liên kết tổng hợp giữa IP, tài khoản và hồ sơ"
                        >
                            <div className="ip-constellation" aria-label="Mạng lưới các tài khoản cùng IP">
                                <div className="ip-center">
                                    <Globe size={29} />
                                    <span className="mono">{current?.ip}</span>
                                    <small>SHARED CONNECTION</small>
                                </div>
                                <svg viewBox="0 0 600 300" aria-hidden="true">
                                    {accounts.map((account, i) => {
                                        const angle = (i * 2 * Math.PI) / accounts.length
                                        const x = 300 + Math.cos(angle) * 210,
                                            y = 150 + Math.sin(angle) * 95
                                        return (
                                            <g key={i}>
                                                <line x1="300" y1="150" x2={x} y2={y} />
                                                <circle cx={x} cy={y} r="17" />
                                                <circle className="ip-signal" cx={x} cy={y} r="4" />
                                                <text x={x} y={y + 35} textAnchor="middle">
                                                    {account}
                                                </text>
                                            </g>
                                        )
                                    })}
                                </svg>
                                <span className="ip-visual-note">Hiển thị tối đa 6 tài khoản từ cụm IP đang chọn</span>
                            </div>
                            {current && (
                                <>
                                    <div className="ip-selected-stats">
                                        <div>
                                            <strong>{current.count}</strong>
                                            <span>Giao dịch cùng IP</span>
                                        </div>
                                        <div>
                                            <strong>{current.accounts}</strong>
                                            <span>Tài khoản</span>
                                        </div>
                                        <div>
                                            <strong>{live ? current.sourceFraud : current.confirmed}</strong>
                                            <span>{live ? 'Nhãn fraud CSV' : 'Xác nhận fraud'}</span>
                                        </div>
                                        <div>
                                            <strong>{shortMoney(current.amount)}</strong>
                                            <span>Tổng giá trị · VND</span>
                                        </div>
                                    </div>
                                    {live && (
                                        <p className="ip-engine-note">
                                            {current.engineFraud} dự đoán fraud từ engine · {current.confirmed} hồ sơ đã
                                            xác nhận fraud qua điều tra
                                        </p>
                                    )}
                                    <div className="ip-caution">
                                        <Info size={15} />
                                        Trùng IP có thể do NAT hoặc VPN; không đủ để tự kết luận gian lận.
                                    </div>
                                </>
                            )}
                        </Panel>
                    </div>
                    <Panel
                        title={current ? 'Hồ sơ từ ' + current.ip : 'Hồ sơ liên quan'}
                        subtitle={
                            live
                                ? 'Hồ sơ cảnh báo khớp IP gốc trong CSV. Trạng thái điều tra và điểm engine được giữ nguyên.'
                                : 'Chỉ nhãn Xác nhận fraud là kết luận review; mức rủi ro được hiển thị riêng'
                        }
                    >
                        <CaseTable rows={current?.records || []} onSelect={openCase} showIp />
                    </Panel>
                </>
            )}
        </>
    )
}
