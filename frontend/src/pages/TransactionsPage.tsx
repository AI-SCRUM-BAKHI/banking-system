import { useEffect, useState } from 'react'
import { Download, Search, SlidersHorizontal, Sparkles, X } from 'lucide-react'
import { AiAnalysisPanel } from '../components/AiAnalysisPanel'
import { CaseTable } from '../components/CaseTable'
import { ErrorState, Loading, Modal, Panel, Pagination } from '../components/ui'
import { useConsole } from '../lib/console-context'
import { useCases } from '../lib/queries'
import { errorMessage } from '../api/axios'
import { exportCsv } from '../lib/format'
import type { ReviewStatus, RiskLevel } from '../types/fraud.types'
export function TransactionsPage() {
    const { session, openCase, notify } = useConsole()
    const [keyword, setKeyword] = useState('')
    const [search, setSearch] = useState('')
    const [risk, setRisk] = useState<RiskLevel | ''>('')
    const [status, setStatus] = useState<ReviewStatus | ''>('')
    const [page, setPage] = useState(0)
    const [sort, setSort] = useState('createdAt,desc')
    const [from, setFrom] = useState('')
    const [to, setTo] = useState('')
    const [dates, setDates] = useState(false)
    const [analysisRefInput, setAnalysisRefInput] = useState('')
    const [analysisRef, setAnalysisRef] = useState('')
    useEffect(() => {
        const timer = setTimeout(() => {
            setSearch(keyword)
            setPage(0)
        }, 300)
        return () => clearTimeout(timer)
    }, [keyword])
    const badRange = !!from && !!to && from > to
    const filters = {
        keyword: search,
        riskLevel: risk || undefined,
        reviewStatus: status || undefined,
        page,
        size: 10,
        sort,
        from: from ? new Date(from + 'T00:00:00+07:00').toISOString() : undefined,
        to: to ? new Date(to + 'T23:59:59.999+07:00').toISOString() : undefined,
    }
    const query = useCases(session.mode, filters, !badRange)
    const rows = query.data?.content || []
    return (
        <>
            <div className="page-title">
                <div>
                    <div className="eyebrow">RISK OPERATIONS / CASE MONITORING</div>
                    <h1>
                        Không bỏ lỡ một tín hiệu<span>.</span>
                    </h1>
                    <p>Tìm kiếm, phân loại và điều tra từng hồ sơ cảnh báo.</p>
                </div>
                <button
                    className="btn secondary small"
                    disabled={!rows.length || badRange}
                    onClick={() => {
                        exportCsv(
                            rows.map((r) => ({
                                transactionRef: r.transactionRef,
                                fromAccount: r.fromAccountMasked,
                                toAccount: r.toAccountMasked,
                                amount: r.amount,
                                currency: r.currency,
                                riskScore: r.riskScore,
                                riskLevel: r.riskLevel,
                                reviewStatus: r.reviewStatus,
                                transactionTime: r.transactionTime,
                            })),
                            'aegis-cases-page.csv',
                        )
                        notify('Đã xuất CSV của trang hiện tại.')
                    }}
                >
                    <Download size={15} />
                    Xuất trang này
                </button>
            </div>
            {session.mode === 'live' && (
                <form
                    className="ai-ref-lookup"
                    onSubmit={(event) => {
                        event.preventDefault()
                        setAnalysisRef(analysisRefInput.trim())
                    }}
                >
                    <div>
                        <label htmlFor="ai-transaction-ref">Phân tích theo mã giao dịch</label>
                        <p>Tra cứu giao dịch đã đánh giá FRAUD, kể cả khi chưa tạo hồ sơ điều tra.</p>
                    </div>
                    <input
                        id="ai-transaction-ref"
                        value={analysisRefInput}
                        onChange={(event) => setAnalysisRefInput(event.target.value)}
                        placeholder="Ví dụ: TX-001"
                        maxLength={80}
                        required
                    />
                    <button type="submit" className="btn secondary small" disabled={!analysisRefInput.trim()}>
                        <Sparkles size={15} />
                        Xem phân tích AI
                    </button>
                </form>
            )}
            {analysisRef && (
                <Modal title="Phân tích AI giao dịch" onClose={() => setAnalysisRef('')} className="ai-analysis-modal">
                    <AiAnalysisPanel key={analysisRef} transactionRef={analysisRef} mode={session.mode} />
                </Modal>
            )}
            <Panel className="transactions-panel">
                <div className="filters-bar">
                    <label className="search-input">
                        <Search size={17} />
                        <input
                            value={keyword}
                            onChange={(e) => setKeyword(e.target.value)}
                            aria-label="Tìm hồ sơ"
                            placeholder="Tìm mã giao dịch hoặc tài khoản…"
                        />
                        {keyword && (
                            <button className="icon-button" aria-label="Xóa tìm kiếm" onClick={() => setKeyword('')}>
                                <X size={14} />
                            </button>
                        )}
                    </label>
                    <select
                        aria-label="Mức rủi ro"
                        value={risk}
                        onChange={(e) => {
                            setRisk(e.target.value as RiskLevel | '')
                            setPage(0)
                        }}
                    >
                        <option value="">Mọi mức rủi ro</option>
                        <option value="CRITICAL">Nghiêm trọng</option>
                        <option value="HIGH">Cao</option>
                        <option value="MEDIUM">Trung bình</option>
                        <option value="LOW">Thấp</option>
                    </select>
                    <select
                        aria-label="Trạng thái điều tra"
                        value={status}
                        onChange={(e) => {
                            setStatus(e.target.value as ReviewStatus | '')
                            setPage(0)
                        }}
                    >
                        <option value="">Mọi trạng thái</option>
                        <option value="NEW">Mới phát hiện</option>
                        <option value="REVIEWING">Đang điều tra</option>
                        <option value="CONFIRMED_FRAUD">Xác nhận fraud</option>
                        <option value="FALSE_POSITIVE">Cảnh báo nhầm</option>
                    </select>
                    <button
                        className={'btn secondary small ' + (dates ? 'pressed' : '')}
                        aria-expanded={dates}
                        onClick={() => setDates(!dates)}
                    >
                        <SlidersHorizontal size={15} />
                        Bộ lọc
                    </button>
                </div>
                {dates && (
                    <div className="expanded-filters">
                        <label>
                            Từ ngày
                            <input
                                type="date"
                                aria-label="Từ ngày"
                                value={from}
                                onChange={(e) => {
                                    setFrom(e.target.value)
                                    setPage(0)
                                }}
                            />
                        </label>
                        <label>
                            Đến ngày
                            <input
                                type="date"
                                aria-label="Đến ngày"
                                value={to}
                                onChange={(e) => {
                                    setTo(e.target.value)
                                    setPage(0)
                                }}
                            />
                        </label>
                        <label>
                            Sắp xếp
                            <select
                                value={sort}
                                onChange={(e) => {
                                    setSort(e.target.value)
                                    setPage(0)
                                }}
                            >
                                <option value="createdAt,desc">Mới tiếp nhận nhất</option>
                                <option value="riskScore,desc">Rủi ro cao nhất</option>
                                <option value="amount,desc">Số tiền lớn nhất</option>
                                <option value="transactionTime,asc">Thời gian tăng dần</option>
                            </select>
                        </label>
                        <button
                            className="text-button"
                            onClick={() => {
                                setFrom('')
                                setTo('')
                                setRisk('')
                                setStatus('')
                                setKeyword('')
                                setSort('createdAt,desc')
                                setPage(0)
                            }}
                        >
                            Xóa bộ lọc <X size={13} />
                        </button>
                    </div>
                )}
                {badRange ? (
                    <p className="form-error" role="alert">
                        Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.
                    </p>
                ) : query.isPending ? (
                    <Loading />
                ) : query.isError ? (
                    <ErrorState message={errorMessage(query.error)} retry={() => query.refetch()} />
                ) : (
                    <>
                        <div className="results-meta">
                            <span>
                                <i className="live-dot" />
                                {query.data?.totalElements || 0} hồ sơ phù hợp
                            </span>
                            <span>
                                Phân trang phía {session.mode === 'demo' ? 'demo' : 'máy chủ'} · 10 hồ sơ / trang
                            </span>
                        </div>
                        <CaseTable rows={rows} onSelect={openCase} />
                        <Pagination
                            page={page}
                            totalPages={query.data?.totalPages || 0}
                            total={query.data?.totalElements || 0}
                            size={10}
                            onPage={setPage}
                        />
                    </>
                )}
            </Panel>
        </>
    )
}
