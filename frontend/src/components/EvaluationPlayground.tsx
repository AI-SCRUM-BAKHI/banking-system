import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
    AlertTriangle,
    ArrowRight,
    CheckCircle2,
    Code2,
    Copy,
    Info,
    LoaderCircle,
    Play,
    RefreshCw,
    ShieldAlert,
    ShieldCheck,
} from 'lucide-react'
import { evaluationRequestSchema, evaluationRules, newEvaluationSample } from '../lib/evaluation'
import type { EvaluationRequest, EvaluationResult } from '../lib/evaluation'
import type { ApiResponse } from '../types/api.types'
import { evaluateTransaction } from '../services/evaluation.service'
import { errorMessage } from '../api/axios'
import { useConsole } from '../lib/console-context'
import { Panel, Pill, RiskBadge } from './ui'
import { evaluationExamples } from '../lib/evaluation-examples'

type Fields = {
    transactionRef: string
    senderAccountId: string
    receiverAccountId: string
    amountVnd: string
    transactionTime: string
    postedAt: string
}
function fieldsFrom(request: EvaluationRequest): Fields {
    return { ...request, amountVnd: String(request.amountVnd), postedAt: request.postedAt || '' }
}
function requestFrom(fields: Fields) {
    return {
        transactionRef: fields.transactionRef,
        senderAccountId: fields.senderAccountId,
        receiverAccountId: fields.receiverAccountId,
        amountVnd: Number(fields.amountVnd),
        currency: 'VND',
        transactionTime: fields.transactionTime,
        ...(fields.postedAt.trim() ? { postedAt: fields.postedAt.trim() } : {}),
    }
}
export function EvaluationPlayground() {
    const { session, notify } = useConsole()
    const [fields, setFields] = useState<Fields>(() => fieldsFrom(newEvaluationSample()))
    const [editor, setEditor] = useState<'form' | 'json'>('form')
    const [json, setJson] = useState('')
    const [response, setResponse] = useState<ApiResponse<EvaluationResult> | null>(null)
    const [submitted, setSubmitted] = useState<EvaluationRequest | null>(null)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')
    const [elapsed, setElapsed] = useState(0)
    const endpoint = window.location.origin + '/api/v1/transactions/evaluate'
    const live = session.mode === 'live'
    const result = response?.data
    const tone = result?.isFraud === true ? 'red' : result?.isFraud === false ? 'green' : 'amber'
    const decision =
        result?.isFraud === true
            ? 'Phát hiện dấu hiệu fraud'
            : result?.isFraud === false
              ? 'Chưa phát hiện fraud'
              : 'Chưa đủ dữ liệu để kết luận'
    function switchEditor(next: 'form' | 'json') {
        if (next === editor) return
        setError('')
        if (next === 'json') setJson(JSON.stringify(requestFrom(fields), null, 2))
        else {
            try {
                const parsed = evaluationRequestSchema.safeParse(JSON.parse(json))
                if (!parsed.success) {
                    setError(parsed.error.issues.map((i) => i.path.join('.') + ': ' + i.message).join(' '))
                    return
                }
                setFields(fieldsFrom(parsed.data))
            } catch {
                setError('JSON không hợp lệ. Kiểm tra cú pháp trước khi chuyển sang form.')
                return
            }
        }
        setEditor(next)
    }
    function sample() {
        const next = newEvaluationSample()
        setFields(fieldsFrom(next))
        setJson(JSON.stringify(next, null, 2))
        setResponse(null)
        setSubmitted(null)
        setError('')
    }
    async function send(event: FormEvent) {
        event.preventDefault()
        if (!live || busy) return
        setError('')
        let request: EvaluationRequest
        try {
            const parsed = evaluationRequestSchema.safeParse(editor === 'json' ? JSON.parse(json) : requestFrom(fields))
            if (!parsed.success) {
                setError(parsed.error.issues.map((i) => i.path.join('.') + ': ' + i.message).join(' '))
                return
            }
            request = parsed.data
            if (
                Date.parse(request.transactionTime) > Date.now() ||
                (request.postedAt && Date.parse(request.postedAt) > Date.now())
            ) {
                setError('Thời gian giao dịch và ghi có phải là thời điểm đã phát sinh.')
                return
            }
        } catch {
            setError('JSON không hợp lệ. Vui lòng kiểm tra cú pháp.')
            return
        }
        setBusy(true)
        setResponse(null)
        const start = performance.now()
        try {
            const next = await evaluateTransaction(request)
            setResponse(next)
            setSubmitted(request)
            setElapsed(Math.round(performance.now() - start))
            notify('Đã nhận kết quả đánh giá từ backend.')
        } catch (e) {
            setError(errorMessage(e))
        } finally {
            setBusy(false)
        }
    }
    async function copy(value: unknown) {
        try {
            await navigator.clipboard.writeText(typeof value === 'string' ? value : JSON.stringify(value, null, 2))
            notify('Đã sao chép.')
        } catch {
            notify('Chọn nội dung JSON để sao chép thủ công.')
        }
    }
    return (
        <section className="evaluation-workspace">
            {!live && (
                <div className="info-banner amber">
                    <Info size={20} />
                    <div>
                        <p>Đăng nhập bằng tài khoản ngân hàng để đánh giá giao dịch trên backend.</p>
                        <Link className="text-button" to="/login">
                            Đăng nhập tài khoản thật
                            <ArrowRight size={14} />
                        </Link>
                    </div>
                </div>
            )}
            <div className="endpoint-bar">
                <span className="http-method post">POST</span>
                <code>{endpoint}</code>
                <button
                    className="icon-button"
                    aria-label="Sao chép endpoint detect fraud"
                    onClick={() => copy(endpoint)}
                >
                    <Copy size={16} />
                </button>
            </div>
            <div className="evaluation-grid">
                <Panel
                    title="Đánh giá một giao dịch"
                    subtitle="Nhập giao dịch đã phát sinh, engine sử dụng lịch sử trên máy chủ"
                    action={
                        <div className="evaluation-editor-tabs">
                            <button
                                className={editor === 'form' ? 'active' : ''}
                                disabled={busy}
                                onClick={() => switchEditor('form')}
                            >
                                Form
                            </button>
                            <button
                                className={editor === 'json' ? 'active' : ''}
                                disabled={busy}
                                onClick={() => switchEditor('json')}
                            >
                                JSON
                            </button>
                        </div>
                    }
                >
                    <form onSubmit={send} className="evaluation-form">
                        <label className="evaluation-presets">
                            Thử lại giao dịch lịch sử đã nạp
                            <select
                                aria-label="Giao dịch lịch sử"
                                defaultValue=""
                                disabled={busy || !live}
                                onChange={(event) => {
                                    const example = evaluationExamples.find((item) => item.key === event.target.value)
                                    if (!example) return
                                    setFields(fieldsFrom(example.request))
                                    setJson(JSON.stringify(example.request, null, 2))
                                    setResponse(null)
                                    setSubmitted(null)
                                    setError('')
                                }}
                            >
                                <option value="" disabled>
                                    Chọn bản ghi để lấy lại kết quả đã lưu…
                                </option>
                                {evaluationExamples.map((item) => (
                                    <option key={item.key} value={item.key}>
                                        {item.name} · {item.request.transactionRef}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <fieldset disabled={busy || !live}>
                            {editor === 'form' ? (
                                <>
                                    <label>
                                        Mã giao dịch
                                        <input
                                            aria-label="Mã giao dịch đánh giá"
                                            value={fields.transactionRef}
                                            maxLength={80}
                                            required
                                            onChange={(e) => setFields({ ...fields, transactionRef: e.target.value })}
                                        />
                                    </label>
                                    <div className="evaluation-field-pair">
                                        <label>
                                            Tài khoản gửi
                                            <input
                                                aria-label="Tài khoản gửi"
                                                value={fields.senderAccountId}
                                                maxLength={80}
                                                required
                                                onChange={(e) =>
                                                    setFields({ ...fields, senderAccountId: e.target.value })
                                                }
                                            />
                                        </label>
                                        <label>
                                            Tài khoản nhận
                                            <input
                                                aria-label="Tài khoản nhận"
                                                value={fields.receiverAccountId}
                                                maxLength={80}
                                                required
                                                onChange={(e) =>
                                                    setFields({ ...fields, receiverAccountId: e.target.value })
                                                }
                                            />
                                        </label>
                                    </div>
                                    <label>
                                        Số tiền · VND
                                        <input
                                            aria-label="Số tiền VND"
                                            inputMode="numeric"
                                            pattern="[0-9]+"
                                            value={fields.amountVnd}
                                            required
                                            onChange={(e) => setFields({ ...fields, amountVnd: e.target.value })}
                                        />
                                    </label>
                                    <label>
                                        Thời gian giao dịch · ISO 8601
                                        <input
                                            aria-label="Thời gian giao dịch"
                                            value={fields.transactionTime}
                                            required
                                            onChange={(e) => setFields({ ...fields, transactionTime: e.target.value })}
                                        />
                                    </label>
                                    <label>
                                        Thời điểm ghi có · tùy chọn
                                        <input
                                            aria-label="Thời điểm ghi có"
                                            placeholder="2026-11-04T14:30:00+07:00"
                                            value={fields.postedAt}
                                            onChange={(e) => setFields({ ...fields, postedAt: e.target.value })}
                                        />
                                    </label>
                                    <small className="evaluation-form-help">
                                        Timestamp kèm Z hoặc UTC offset. Tài khoản gửi và nhận phải khác nhau.
                                    </small>
                                </>
                            ) : (
                                <textarea
                                    className="api-editor evaluation-json-editor"
                                    aria-label="Request detect fraud JSON"
                                    spellCheck={false}
                                    value={json}
                                    onChange={(e) => setJson(e.target.value)}
                                />
                            )}
                        </fieldset>
                        {error && (
                            <div className="form-error" role="alert">
                                {error}
                                <small>
                                    HTTP 409 có thể do mã đã tồn tại với dữ liệu khác hoặc thời gian trước mốc ingest
                                    toàn cục. Retry cùng mã cần giữ nguyên toàn bộ input.
                                </small>
                            </div>
                        )}
                        <div className="evaluation-actions">
                            <button type="button" className="text-button" disabled={busy} onClick={sample}>
                                <RefreshCw size={14} />
                                Mẫu mới
                            </button>
                            <button type="submit" className="btn primary" disabled={busy || !live}>
                                {busy ? <LoaderCircle className="spin" size={15} /> : <Play size={15} />}{' '}
                                {busy ? 'Đang đánh giá…' : 'Detect fraud'}
                            </button>
                        </div>
                        <div className="evaluation-auth-note">
                            <ShieldCheck size={13} />
                            Phiên đăng nhập được xác thực tại gateway. Service token được giữ trên máy chủ.
                        </div>
                    </form>
                </Panel>
                <Panel
                    title="Kết quả từ engine"
                    subtitle="rule-score-v1 · Phản hồi thực của backend"
                    action={
                        result ? <Pill tone="green">200 OK · {elapsed} ms</Pill> : <Pill tone="neutral">Sẵn sàng</Pill>
                    }
                    className="evaluation-response"
                >
                    {busy ? (
                        <div className="response-placeholder">
                            <LoaderCircle className="spin" size={33} />
                            <h3>Đang phân tích giao dịch…</h3>
                            <p>Đối chiếu lịch sử và bảy quy tắc trên backend.</p>
                        </div>
                    ) : result ? (
                        <>
                            <div className={'evaluation-verdict ' + tone}>
                                {result.isFraud === true ? (
                                    <ShieldAlert size={29} />
                                ) : result.isFraud === false ? (
                                    <CheckCircle2 size={29} />
                                ) : (
                                    <AlertTriangle size={29} />
                                )}
                                <div>
                                    <span>{result.decision}</span>
                                    <h3>{decision}</h3>
                                    <p className="mono">{result.transactionRef}</p>
                                </div>
                            </div>
                            <div className="evaluation-result-metrics">
                                <div>
                                    <span>RISK SCORE</span>
                                    <strong>
                                        {result.riskScore}
                                        <small>/100</small>
                                    </strong>
                                    <RiskBadge risk={result.riskLevel} />
                                </div>
                                <div>
                                    <span>NHÃN isFraud</span>
                                    <strong className="mono">
                                        {result.isFraud === null ? 'null' : String(result.isFraud)}
                                    </strong>
                                    <Pill tone={result.evaluationStatus === 'COMPLETE' ? 'green' : 'amber'}>
                                        {result.evaluationStatus}
                                    </Pill>
                                </div>
                            </div>
                            <div className="evaluation-score-scale">
                                <div>
                                    <i style={{ width: result.riskScore + '%' }} />
                                </div>
                                <span>Ngưỡng fraud {result.fraudThreshold} · Điểm quy tắc, không phải xác suất</span>
                            </div>
                            {result.evaluationStatus === 'PARTIAL' && (
                                <div className="evaluation-partial">
                                    <Info size={16} />
                                    <p>
                                        Lịch sử hoặc coverage còn thiếu. Điểm hiện tại phản ánh các rule đã tính được.
                                        Nhãn null nghĩa là chưa đủ dữ liệu để kết luận.
                                    </p>
                                </div>
                            )}
                            <div className="evaluation-rule-results">
                                <h3>
                                    Tín hiệu kích hoạt <span>{result.triggeredRules.length}</span>
                                </h3>
                                {result.triggeredRules.length ? (
                                    result.triggeredRules.map((rule) => (
                                        <article key={rule.ruleCode}>
                                            <div>
                                                <b>
                                                    {evaluationRules.find((r) => r.code === rule.ruleCode)?.name ||
                                                        rule.ruleCode}
                                                </b>
                                                <code>{rule.ruleCode}</code>
                                                {rule.suppressedBy && <small>Gộp điểm vào {rule.suppressedBy}</small>}
                                            </div>
                                            <Pill tone={rule.suppressedBy ? 'neutral' : 'orange'}>
                                                +{rule.scoreContribution}
                                            </Pill>
                                        </article>
                                    ))
                                ) : (
                                    <p>Chưa có rule kích hoạt trong những rule đã đánh giá được.</p>
                                )}
                                {result.unevaluatedRules.length > 0 && (
                                    <details open>
                                        <summary>{result.unevaluatedRules.length} rule chưa đủ dữ liệu</summary>
                                        {result.unevaluatedRules.map((rule) => (
                                            <div className="unevaluated-rule" key={rule.ruleCode}>
                                                <b>{rule.ruleCode}</b>
                                                <p>{rule.reason}</p>
                                            </div>
                                        ))}
                                    </details>
                                )}
                            </div>
                            <details className="evaluation-raw">
                                <summary>
                                    <Code2 size={15} />
                                    Request & response JSON
                                </summary>
                                <button className="text-button" onClick={() => copy(submitted)}>
                                    Sao chép request
                                    <Copy size={12} />
                                </button>
                                <pre>{JSON.stringify(submitted, null, 2)}</pre>
                                <button className="text-button" onClick={() => copy(response)}>
                                    Sao chép response
                                    <Copy size={12} />
                                </button>
                                <pre aria-live="polite">{JSON.stringify(response, null, 2)}</pre>
                            </details>
                            <div className="evaluation-request-id">
                                Request ID <code>{response.requestId}</code>
                            </div>
                        </>
                    ) : (
                        <div className="response-placeholder">
                            <div>
                                <ShieldCheck size={34} />
                            </div>
                            <h3>Từ giao dịch đến kết luận.</h3>
                            <p>
                                Gửi input để xem isFraud, riskScore,
                                <br />
                                decision và các rule giải thích.
                            </p>
                            <span>FRAUD · NOT_FRAUD · INSUFFICIENT_DATA</span>
                        </div>
                    )}
                </Panel>
            </div>
            <div className="info-banner">
                <Info size={18} />
                <p>
                    Mỗi lần đánh giá thành công lưu giao dịch vào lịch sử engine. Kết quả đánh giá và hồ sơ điều tra là
                    hai luồng lưu trữ riêng trong backend. Dùng lại cùng mã và cùng input để lấy lại kết quả cũ.
                </p>
            </div>
            <Panel
                title="Bảy lớp tín hiệu"
                subtitle="Các rule trong cùng nhóm lấy đóng góp cao nhất, tổng điểm tối đa 100"
            >
                <div className="evaluation-rules-grid">
                    {evaluationRules.map((rule) => (
                        <article key={rule.code}>
                            <div>
                                <code>{rule.code}</code>
                                <Pill tone="blue">+{rule.points}</Pill>
                            </div>
                            <h3>{rule.name}</h3>
                            <p>{rule.description}</p>
                        </article>
                    ))}
                </div>
            </Panel>
        </section>
    )
}
