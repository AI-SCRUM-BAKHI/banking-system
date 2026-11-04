import { useState } from 'react'
import { Globe, Info, Network, Search, ArrowUpRight, ShieldAlert } from 'lucide-react'
import { useConsole } from '../lib/console-context'
import { useCases } from '../lib/queries'
import { groupDemoIps } from '../lib/investigation'
import { Empty, ErrorState, Loading, Panel, Pill } from '../components/ui'
import { CaseTable } from '../components/CaseTable'
import { shortMoney } from '../lib/format'
import { errorMessage } from '../api/axios'
export function IpPage() {
    const { session, openCase } = useConsole()
    const [ip, setIp] = useState('')
    const [search, setSearch] = useState('')
    const query = useCases('demo', { size: 1000 }, session.mode === 'demo')
    const groups = groupDemoIps(query.data?.content || [])
    const repeated = groups.filter((g) => g.count > 1)
    const current = groups.find((g) => g.ip === ip) || repeated[0]
    const accounts = [
        ...new Set(current?.records.flatMap((row) => [row.fromAccountMasked, row.toAccountMasked]) || []),
    ].slice(0, 6)
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
                <Pill tone="amber">
                    <Globe size={13} /> DEMO CAPABILITY
                </Pill>
            </div>
            <div className="info-banner amber">
                <Info size={19} />
                <p>
                    <b>IP Intelligence là kịch bản frontend mô phỏng.</b> Backend hiện chưa trả trường IP. Địa chỉ trong
                    demo được tạo từ dải minh họa, không phải IP ngân hàng thực.
                </p>
            </div>
            {session.mode === 'live' ? (
                <Panel>
                    <Empty
                        title="Backend chưa cung cấp địa chỉ IP"
                        text="Dữ liệu live không bị gán IP giả. Bạn có thể khám phá đồ thị dòng tiền tại mục Liên kết & dòng tiền."
                    />
                </Panel>
            ) : query.isPending ? (
                <Loading />
            ) : query.isError ? (
                <ErrorState message={errorMessage(query.error)} retry={() => query.refetch()} />
            ) : (
                <>
                    <div className="ip-summary-grid">
                        <div>
                            <Globe size={20} />
                            <strong>{groups.length}</strong>
                            <span>IP trong dữ liệu mẫu</span>
                        </div>
                        <div>
                            <Network size={20} />
                            <strong>{repeated.length}</strong>
                            <span>Cụm IP dùng chung</span>
                        </div>
                        <div>
                            <ShieldAlert size={20} />
                            <strong>{groups.filter((g) => g.confirmed > 0).length}</strong>
                            <span>IP có hồ sơ xác nhận fraud</span>
                        </div>
                    </div>
                    <div className="ip-workspace">
                        <Panel title="Cụm IP cần đối chiếu" subtitle="Xếp theo số hồ sơ đã xác nhận fraud">
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
                                {groups
                                    .filter((g) => g.ip.includes(search))
                                    .map((g) => (
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
                                                {g.accounts} tài khoản · {g.count} hồ sơ
                                            </p>
                                            <div className="ip-metrics">
                                                <span>
                                                    <i />
                                                    {g.confirmed} xác nhận fraud
                                                </span>
                                                <span>{g.highRisk} rủi ro cao</span>
                                            </div>
                                            <div className="ip-group-bar">
                                                <i style={{ width: `${(g.confirmed / g.count) * 100}%` }} />
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
                                            <span>Hồ sơ cùng IP</span>
                                        </div>
                                        <div>
                                            <strong>{current.accounts}</strong>
                                            <span>Tài khoản</span>
                                        </div>
                                        <div>
                                            <strong>{current.confirmed}</strong>
                                            <span>Xác nhận fraud</span>
                                        </div>
                                        <div>
                                            <strong>{shortMoney(current.amount)}</strong>
                                            <span>Tổng giá trị · VND</span>
                                        </div>
                                    </div>
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
                        subtitle="Chỉ nhãn Xác nhận fraud là kết luận review; mức rủi ro được hiển thị riêng"
                    >
                        <CaseTable rows={current?.records || []} onSelect={openCase} showIp />
                    </Panel>
                </>
            )}
        </>
    )
}
