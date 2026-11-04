import { useState } from 'react'
import { Code2, Copy, Check, ExternalLink, Info, Play, Terminal, LoaderCircle, ShieldCheck } from 'lucide-react'
import { apiClient, errorMessage } from '../api/axios'
import { env } from '../config/env'
import { demoDetail, demoGraph, demoList, demoSummary } from '../lib/demo'
import { useCases } from '../lib/queries'
import { useConsole } from '../lib/console-context'
import { Panel, Pill } from '../components/ui'
import type { FraudFilters } from '../types/fraud.types'
import { EvaluationPlayground } from '../components/EvaluationPlayground'
const endpoints = [
    {
        key: 'summary',
        name: 'Tổng hợp dashboard',
        path: '/fraud-transactions/summary',
        params: {},
        help: 'Thống kê toàn bộ hồ sơ theo khoảng thời gian.',
    },
    {
        key: 'list',
        name: 'Danh sách hồ sơ',
        path: '/fraud-transactions',
        params: { page: 0, size: 5, sort: 'riskScore,desc' },
        help: 'Phân trang và lọc theo riskLevel, reviewStatus, keyword, from, to.',
    },
    {
        key: 'detail',
        name: 'Chi tiết hồ sơ',
        path: '/fraud-transactions/{id}',
        params: {},
        help: 'Thông tin tài khoản, lý do cảnh báo và các rule hits.',
    },
    {
        key: 'rules',
        name: 'Rule hits',
        path: '/fraud-transactions/{id}/rule-hits',
        params: {},
        help: 'Các tín hiệu và điểm đóng góp trong hồ sơ.',
    },
    {
        key: 'graph',
        name: 'Đồ thị dòng tiền',
        path: '/fraud-transactions/{id}/graph',
        params: {},
        help: 'Danh sách nodes và edges của các giao dịch liên quan.',
    },
]
const ingestSample = {
    transactionRef: 'TX-EXAMPLE-001',
    fromAccountRef: 'ACC-A',
    fromAccountMasked: '**** 1082',
    fromCustomerName: 'Demo A',
    toAccountRef: 'ACC-B',
    toAccountMasked: '**** 3024',
    toCustomerName: 'Demo B',
    amount: 185000000,
    currency: 'VND',
    transactionTime: '2026-11-04T02:30:00Z',
    riskScore: 95,
    riskLevel: 'CRITICAL',
    fraudReason: 'Kết quả do dịch vụ phát hiện gian lận cung cấp.',
    ruleHits: [
        {
            ruleCode: 'MULTI_ACCOUNT_FLOW',
            ruleName: 'Luồng tiền qua nhiều tài khoản',
            scoreContribution: 95,
            explanation: 'Kịch bản mẫu minh họa ingest.',
        },
    ],
    transactionChain: [
        {
            transactionRef: 'TX-EXAMPLE-001',
            fromAccountRef: 'ACC-A',
            fromAccountMasked: '**** 1082',
            toAccountRef: 'ACC-B',
            toAccountMasked: '**** 3024',
            amount: 185000000,
            currency: 'VND',
            transactionTime: '2026-11-04T02:30:00Z',
            hopLevel: 0,
            root: true,
        },
    ],
}
export function ApiPage() {
    const { session, notify } = useConsole()
    const [key, setKey] = useState('summary')
    const [params, setParams] = useState('{}')
    const [id, setId] = useState('')
    const [response, setResponse] = useState<unknown>(null)
    const [result, setResult] = useState('')
    const [busy, setBusy] = useState(false)
    const [failed, setFailed] = useState(false)
    const [tab, setTab] = useState<'detect' | 'playground' | 'ingest'>('detect')
    const [copied, setCopied] = useState(false)
    const examples = useCases(session.mode, { size: 1 })
    const selected = endpoints.find((e) => e.key === key)!
    const actualId = id || examples.data?.content[0]?.id || ''
    const path = selected.path.replace('{id}', encodeURIComponent(actualId))
    const base = new URL(env.VITE_API_BASE_URL, window.location.origin).toString().replace(/\/$/, '')
    const requiresId = selected.path.includes('{id}')
    async function copy(text: string) {
        try {
            await navigator.clipboard.writeText(text)
            setCopied(true)
            notify('Đã sao chép.')
            setTimeout(() => setCopied(false), 1800)
        } catch {
            notify('Không truy cập được clipboard. Bạn có thể chọn và sao chép nội dung.')
        }
    }
    async function run() {
        setBusy(true)
        setResponse(null)
        setFailed(false)
        const start = performance.now()
        try {
            const parsed = JSON.parse(params)
            if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object')
                throw new Error('Query parameters phải là một JSON object.')
            if (requiresId && !actualId) throw new Error('Vui lòng nhập UUID hồ sơ.')
            if (session.mode === 'live') {
                const res = await apiClient.get(path, { params: parsed })
                setResponse(res.data)
                setResult(`${res.status} OK · ${Math.round(performance.now() - start)} ms`)
            } else {
                await new Promise((resolve) => setTimeout(resolve, 350))
                let data: unknown
                switch (key) {
                    case 'list':
                        data = demoList(parsed as FraudFilters)
                        break
                    case 'summary':
                        data = demoSummary(parsed)
                        break
                    case 'detail':
                        data = demoDetail(actualId)
                        break
                    case 'rules':
                        data = demoDetail(actualId).ruleHits
                        break
                    default:
                        data = demoGraph(actualId)
                }
                setResponse({
                    success: true,
                    data,
                    message: 'Frontend demo response — no backend request',
                    timestamp: new Date().toISOString(),
                    requestId: 'demo-' + crypto.randomUUID(),
                })
                setResult('200 · MÔ PHỎNG TRONG TRÌNH DUYỆT')
            }
        } catch (error) {
            setFailed(true)
            setResponse({ error: errorMessage(error) })
            setResult('REQUEST ERROR')
        } finally {
            setBusy(false)
        }
    }
    const ingestCommand = `curl -X POST "${base}/fraud-transactions" \\\n  -H "Content-Type: application/json" \\\n  -H "X-Service-Token: $APP_SERVICE_TOKEN" \\\n  --data @fraud-result.json`
    return (
        <>
            <div className="page-title">
                <div>
                    <div className="eyebrow">DEVELOPER PLATFORM / API CONSOLE</div>
                    <h1>
                        Tích hợp rõ ràng. Kết nối liền mạch<span>.</span>
                    </h1>
                    <p>Khám phá contract, gửi request và kiểm tra cấu trúc response.</p>
                </div>
                <Pill tone="blue">
                    <Code2 size={13} /> REST API / v1
                </Pill>
            </div>
            <div className="api-intro">
                <span className="api-intro-icon">
                    <Terminal size={27} />
                </span>
                <div>
                    <b>{session.mode === 'demo' ? 'Bạn đang ở developer sandbox.' : 'Bạn đang kết nối tới backend.'}</b>
                    <p>
                        {session.mode === 'demo'
                            ? 'Response được mô phỏng trong browser, theo cấu trúc DTO hiện có. Đăng nhập thật để gửi request đến backend.'
                            : 'Detect fraud sử dụng gateway xác thực phiên đăng nhập để chạy engine. Các truy vấn hồ sơ sử dụng bearer token.'}
                    </p>
                </div>
                <Pill tone={session.mode === 'demo' ? 'amber' : 'green'}>
                    {session.mode === 'demo' ? 'DEMO' : 'LIVE API'}
                </Pill>
            </div>
            <div className="page-tabs">
                <button className={tab === 'detect' ? 'active' : ''} onClick={() => setTab('detect')}>
                    <ShieldCheck size={15} />
                    Detect fraud
                </button>
                <button className={tab === 'playground' ? 'active' : ''} onClick={() => setTab('playground')}>
                    <Play size={15} />
                    Query playground
                </button>
                <button className={tab === 'ingest' ? 'active' : ''} onClick={() => setTab('ingest')}>
                    <ShieldCheck size={15} />
                    Producer integration
                </button>
                <a href={base.replace(/\/api\/v1\/?$/, '') + '/swagger-ui.html'} target="_blank" rel="noreferrer">
                    Swagger <ExternalLink size={13} />
                </a>
            </div>
            {tab === 'detect' ? (
                <EvaluationPlayground />
            ) : tab === 'playground' ? (
                <>
                    <div className="endpoint-bar">
                        <span className="http-method">GET</span>
                        <code>
                            {base}
                            {path}
                        </code>
                        <button
                            className="icon-button"
                            aria-label="Sao chép endpoint"
                            onClick={() => copy(base + path)}
                        >
                            {copied ? <Check size={16} /> : <Copy size={16} />}
                        </button>
                    </div>
                    <div className="api-playground">
                        <Panel title="Request builder" subtitle="Chỉ sử dụng các endpoint có sẵn trong backend">
                            <div className="request-controls">
                                <label>
                                    Endpoint
                                    <select
                                        aria-label="Chọn endpoint"
                                        value={key}
                                        onChange={(e) => {
                                            setKey(e.target.value)
                                            setParams(
                                                JSON.stringify(
                                                    endpoints.find((x) => x.key === e.target.value)!.params,
                                                    null,
                                                    2,
                                                ),
                                            )
                                            setResponse(null)
                                            setResult('')
                                        }}
                                    >
                                        {endpoints.map((e) => (
                                            <option value={e.key} key={e.key}>
                                                {e.name}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <p>{selected.help}</p>
                                {requiresId && (
                                    <label>
                                        UUID hồ sơ
                                        <input
                                            aria-label="UUID hồ sơ"
                                            value={actualId}
                                            onChange={(e) => setId(e.target.value)}
                                            placeholder="00000000-0000-4000-8000-000000000001"
                                        />
                                    </label>
                                )}
                            </div>
                            <div className="editor-header">
                                <span>QUERY PARAMETERS</span>
                                <span>JSON</span>
                            </div>
                            <textarea
                                className="api-editor"
                                aria-label="Query parameters JSON"
                                spellCheck={false}
                                value={params}
                                onChange={(e) => setParams(e.target.value)}
                            />
                            <div className="request-actions">
                                <span>
                                    <ShieldCheck size={13} />
                                    {session.mode === 'demo' ? 'Không gọi backend' : 'Bearer token tự động'}
                                </span>
                                <button className="btn primary small" disabled={busy} onClick={run}>
                                    {busy ? <LoaderCircle size={14} className="spin" /> : <Play size={14} />}Gửi request
                                </button>
                            </div>
                        </Panel>
                        <Panel
                            title="Response"
                            subtitle={
                                session.mode === 'demo'
                                    ? 'Mô phỏng theo envelope của backend'
                                    : 'Response thực từ máy chủ'
                            }
                            action={
                                result ? (
                                    <Pill tone={failed ? 'red' : 'green'}>{result}</Pill>
                                ) : (
                                    <Pill tone="neutral">Sẵn sàng</Pill>
                                )
                            }
                            className="api-response"
                        >
                            {response ? (
                                <>
                                    <pre aria-live="polite">{JSON.stringify(response, null, 2)}</pre>
                                    <button
                                        className="text-button response-copy"
                                        onClick={() => copy(JSON.stringify(response, null, 2))}
                                    >
                                        <Copy size={13} />
                                        Sao chép response
                                    </button>
                                </>
                            ) : (
                                <div className="response-placeholder">
                                    <div>
                                        <Code2 size={32} />
                                    </div>
                                    <h3>Một request. Nhiều thấu hiểu.</h3>
                                    <p>
                                        Chọn endpoint và gửi request để xem
                                        <br />
                                        dữ liệu trả về có cấu trúc.
                                    </p>
                                    <span>JSON · REST · API v1</span>
                                </div>
                            )}
                        </Panel>
                    </div>
                    <Panel
                        title="Contract & quyền truy cập"
                        subtitle="Đối chiếu trực tiếp với controller và DTO trong repo"
                    >
                        <div className="table-scroll">
                            <table className="api-contract-table">
                                <thead>
                                    <tr>
                                        <th>PHƯƠNG THỨC</th>
                                        <th>ENDPOINT</th>
                                        <th>CHỨC NĂNG / QUYỀN</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {[
                                        ['POST', '/auth/login', 'Đăng nhập bằng username và password'],
                                        ['POST', '/auth/refresh', 'Làm mới token; refresh token được xoay vòng'],
                                        ['GET', '/auth/me', 'Thông tin người dùng đang đăng nhập'],
                                        ['GET', '/fraud-transactions', 'Danh sách; ADMIN / BANK_USER'],
                                        ['GET', '/fraud-transactions/summary', 'Thống kê dashboard; ADMIN / BANK_USER'],
                                        ['GET', '/fraud-transactions/{id}', 'Chi tiết hồ sơ; ADMIN / BANK_USER'],
                                        [
                                            'GET',
                                            '/fraud-transactions/{id}/rule-hits',
                                            'Tín hiệu giải thích; ADMIN / BANK_USER',
                                        ],
                                        ['GET', '/fraud-transactions/{id}/graph', 'Nodes và edges; ADMIN / BANK_USER'],
                                        [
                                            'PATCH',
                                            '/fraud-transactions/{id}/review-status',
                                            'Body: { "status": "REVIEWING" }',
                                        ],
                                        ['POST', '/users', 'Tạo tài khoản; chỉ ADMIN'],
                                        [
                                            'POST',
                                            '/transactions/evaluate',
                                            'Đánh giá fraud; gateway xác thực JWT và gọi engine bằng service token',
                                        ],
                                    ].map(([method, url, desc]) => (
                                        <tr key={url + method}>
                                            <td>
                                                <span className={'http-method ' + method.toLowerCase()}>{method}</span>
                                            </td>
                                            <td className="mono">{url}</td>
                                            <td>{desc}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </Panel>
                </>
            ) : (
                <>
                    <div className="info-banner amber">
                        <Info size={19} />
                        <p>
                            <b>Ingest nhận kết quả đã chấm điểm, không tự dự đoán fraud.</b> Chỉ dịch vụ tin cậy có
                            X-Service-Token được gọi endpoint này. Service token không được nhập hoặc lưu trong
                            frontend.
                        </p>
                    </div>
                    <div className="producer-grid">
                        <Panel title="Producer → Banking API" subtitle="Gọi từ máy chủ của dịch vụ phát hiện gian lận">
                            <div className="producer-flow">
                                <span>
                                    Dịch vụ fraud
                                    <br />
                                    <b>Score + rule hits</b>
                                </span>
                                <span>→</span>
                                <span>
                                    Banking backend
                                    <br />
                                    <b>Ingest + lưu hồ sơ</b>
                                </span>
                                <span>→</span>
                                <span>
                                    AEGIS console
                                    <br />
                                    <b>Điều tra + review</b>
                                </span>
                            </div>
                            <div className="code-block-head">
                                <span>SERVER-SIDE EXAMPLE</span>
                                <button
                                    className="icon-button"
                                    aria-label="Sao chép lệnh cURL ingest"
                                    onClick={() => copy(ingestCommand)}
                                >
                                    <Copy size={15} />
                                </button>
                            </div>
                            <pre className="producer-code">{ingestCommand}</pre>
                            <div className="api-explanation">
                                <h3>Ràng buộc aggregate</h3>
                                <ul>
                                    <li>transactionRef duy nhất, số tiền dương, riskScore từ 0 đến 100.</li>
                                    <li>transactionChain có đúng một entry root, hopLevel = 0.</li>
                                    <li>
                                        Entry root khớp mã, tài khoản, số tiền, tiền tệ và thời điểm của giao dịch
                                        chính.
                                    </li>
                                    <li>ruleCode và mã giao dịch trong chain không được trùng.</li>
                                </ul>
                            </div>
                        </Panel>
                        <Panel
                            title="fraud-result.json"
                            subtitle="Payload mẫu đúng cấu trúc FraudRequests.Create"
                            action={
                                <button
                                    className="icon-button"
                                    aria-label="Sao chép payload ingest"
                                    onClick={() => copy(JSON.stringify(ingestSample, null, 2))}
                                >
                                    <Copy size={16} />
                                </button>
                            }
                        >
                            <pre className="ingest-json">{JSON.stringify(ingestSample, null, 2)}</pre>
                        </Panel>
                    </div>
                </>
            )}
            <div className="api-bottom-notes">
                <div>
                    <h3>Envelope thống nhất</h3>
                    <p>
                        success, data, message, timestamp, requestId.
                        <br />
                        Lỗi có error.code, error.message và statusCode.
                    </p>
                </div>
                <div>
                    <h3>Về endpoint chấm điểm</h3>
                    <p>
                        POST /transactions/evaluate chạy engine rule-score-v1.
                        <br />
                        Mở tab Detect fraud để gửi input và xem kết quả từ backend.
                    </p>
                </div>
                <div>
                    <h3>Quy trình điều tra</h3>
                    <p>
                        NEW → REVIEWING → CONFIRMED_FRAUD / FALSE_POSITIVE.
                        <br />
                        Các chuyển trạng thái khác bị backend từ chối.
                    </p>
                </div>
            </div>
        </>
    )
}
