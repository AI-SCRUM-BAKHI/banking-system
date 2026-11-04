import { useState } from 'react'
import { ArrowRight, ArrowUpRight, Fingerprint, Info, Network, Search, ShieldAlert } from 'lucide-react'
import { useConsole } from '../lib/console-context'
import { useCases, useGraph } from '../lib/queries'
import { FlowGraph } from '../components/NetworkScene'
import { Empty, ErrorState, Loading, Panel, Pill, RiskBadge } from '../components/ui'
import { errorMessage } from '../api/axios'
import { dateTime, money } from '../lib/format'
export function NetworkPage() {
    const { session, openCase } = useConsole()
    const [chosen, setChosen] = useState('')
    const [account, setAccount] = useState('')
    const [search, setSearch] = useState('')
    const cases = useCases(session.mode, { size: 100, sort: 'riskScore,desc' })
    const id = chosen || cases.data?.content[0]?.id || ''
    const graph = useGraph(session.mode, id)
    const selected = graph.data?.nodes.find((n) => n.id === account)
    const record = cases.data?.content.find((r) => r.id === id)
    return (
        <>
            <div className="page-title">
                <div>
                    <div className="eyebrow">CONNECTED INTELLIGENCE / MONEY FLOW</div>
                    <h1>
                        Mỗi dòng tiền. Một câu chuyện<span>.</span>
                    </h1>
                    <p>Kết nối tài khoản và giao dịch để khám phá những gì nằm phía sau cảnh báo.</p>
                </div>
                <Pill tone="blue">
                    <Network size={13} /> Directed transaction graph
                </Pill>
            </div>
            <div className="info-banner">
                <Info size={18} />
                <p>
                    Đồ thị thể hiện các giao dịch liên quan trong hồ sơ, không khẳng định nguồn gốc của từng đồng tiền.
                    Chọn tài khoản để xem liên kết trực tiếp.
                </p>
            </div>
            <div className="network-workspace">
                <Panel title="Chọn hồ sơ" subtitle="Tối đa 100 hồ sơ có điểm rủi ro cao nhất" className="case-picker">
                    <label className="search-input">
                        <Search size={15} />
                        <input
                            aria-label="Tìm trong hồ sơ đồ thị"
                            placeholder="Tìm mã hồ sơ…"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                        />
                    </label>
                    {cases.isPending ? (
                        <Loading />
                    ) : cases.isError ? (
                        <ErrorState message={errorMessage(cases.error)} retry={() => cases.refetch()} />
                    ) : cases.data?.content.length ? (
                        <div className="case-picker-list">
                            {cases.data.content
                                .filter((r) => r.transactionRef.toLowerCase().includes(search.toLowerCase()))
                                .map((r) => (
                                    <button
                                        key={r.id}
                                        className={id === r.id ? 'active' : ''}
                                        onClick={() => {
                                            setChosen(r.id)
                                            setAccount('')
                                        }}
                                    >
                                        <div>
                                            <b>{r.transactionRef}</b>
                                            <span className="mono">{r.riskScore}</span>
                                        </div>
                                        <p>
                                            {r.fromAccountMasked}
                                            <ArrowRight size={11} />
                                            {r.toAccountMasked}
                                        </p>
                                        <div>
                                            <small>{dateTime(r.transactionTime)}</small>
                                            <span className={'case-risk-dot ' + r.riskLevel} />
                                        </div>
                                    </button>
                                ))}
                        </div>
                    ) : (
                        <Empty />
                    )}
                </Panel>
                <Panel
                    title="Bản đồ dòng tiền"
                    subtitle={record?.transactionRef || 'Chọn một hồ sơ để bắt đầu'}
                    action={
                        record ? (
                            <button className="text-button" onClick={() => openCase(id)}>
                                Mở hồ sơ <ArrowUpRight size={14} />
                            </button>
                        ) : undefined
                    }
                    className="graph-panel"
                >
                    {!id ? (
                        <Empty title="Chưa có hồ sơ để phân tích" text="Dữ liệu sẽ xuất hiện khi có hồ sơ cảnh báo." />
                    ) : graph.isPending ? (
                        <Loading />
                    ) : graph.isError ? (
                        <ErrorState message={errorMessage(graph.error)} retry={() => graph.refetch()} />
                    ) : graph.data?.nodes.length ? (
                        <>
                            <div className="graph-stats">
                                <span>
                                    <i />
                                    {graph.data.nodes.length} tài khoản
                                </span>
                                <span>{graph.data.edges.length} giao dịch liên kết</span>
                                {record && <RiskBadge risk={record.riskLevel} />}
                            </div>
                            <FlowGraph graph={graph.data} selected={account} onSelect={setAccount} />
                            <div className="graph-key">
                                <span>
                                    <i />
                                    Liên kết giao dịch
                                </span>
                                <span>
                                    <i className="orange" />
                                    Giao dịch gốc
                                </span>
                                <span>Mũi tên = hướng chuyển tiền</span>
                            </div>
                            {selected ? (
                                <div className="selected-account">
                                    <span className="account-icon">
                                        <Fingerprint size={22} />
                                    </span>
                                    <div>
                                        <small>TÀI KHOẢN ĐANG CHỌN</small>
                                        <h3>{selected.masked}</h3>
                                        <p>{selected.customerName || 'Không có thông tin tên khách hàng'}</p>
                                    </div>
                                    <Pill tone="blue">
                                        {
                                            graph.data.edges.filter(
                                                (e) => e.fromAccountRef === account || e.toAccountRef === account,
                                            ).length
                                        }{' '}
                                        liên kết
                                    </Pill>
                                </div>
                            ) : (
                                <div className="graph-tip">
                                    <Fingerprint size={17} />
                                    Chọn một nút tài khoản để khám phá liên kết.
                                </div>
                            )}
                            <div className="chain-list graph-chain-list">
                                {graph.data.edges
                                    .filter(
                                        (e) => !account || e.fromAccountRef === account || e.toAccountRef === account,
                                    )
                                    .map((edge) => (
                                        <div key={edge.id}>
                                            <span>
                                                <b>{edge.transactionRef}</b>
                                                <small>
                                                    {dateTime(edge.transactionTime)} · Hop {edge.hopLevel}{' '}
                                                    {edge.root ? '· Giao dịch gốc' : ''}
                                                </small>
                                            </span>
                                            <b>{money(edge.amount, edge.currency)}</b>
                                        </div>
                                    ))}
                            </div>
                        </>
                    ) : (
                        <Empty title="Chưa có dữ liệu đồ thị" text="Hồ sơ này chưa có giao dịch liên quan." />
                    )}
                </Panel>
            </div>
            <div className="subtle-note">
                <ShieldAlert size={15} />
                Liên kết là bằng chứng hỗ trợ điều tra; kết luận fraud được quản lý riêng theo trạng thái review.
            </div>
        </>
    )
}
