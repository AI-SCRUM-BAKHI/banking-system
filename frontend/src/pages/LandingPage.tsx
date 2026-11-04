import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
    ArrowDown,
    ArrowRight,
    ArrowUpRight,
    Activity,
    Network,
    Code2,
    Check,
    ShieldCheck,
    ScanLine,
    Workflow,
    ChevronRight,
    Play,
    Pause,
} from 'lucide-react'
import { Brand, Pill, Reveal } from '../components/ui'
import { RadarScene } from '../components/NetworkScene'

export function LandingPage({ onDemo }: { onDemo: () => void }) {
    const [paused, setPaused] = useState(false)
    return (
        <div className={'landing ' + (paused ? 'motion-paused' : '')}>
            <header className="marketing-nav">
                <Link to="/" aria-label="AEGIS trang chủ">
                    <Brand />
                </Link>
                <nav aria-label="Điều hướng chính">
                    <a href="#platform">Nền tảng</a>
                    <a href="#workflow">Cách hoạt động</a>
                    <Link to="/login">
                        Dành cho developer <ArrowUpRight size={12} />
                    </Link>
                </nav>
                <div>
                    <Link className="nav-login" to="/login">
                        Đăng nhập
                    </Link>
                    <button className="btn small dark" onClick={onDemo}>
                        Khám phá demo <ArrowUpRight size={15} />
                    </button>
                </div>
            </header>
            <main id="main-content">
                <section className="marketing-hero">
                    <div className="hero-text">
                        <Reveal>
                            <div className="eyebrow">
                                <span className="eyebrow-line" /> THE INTELLIGENCE BEHIND TRUST
                            </div>
                            <h1>
                                Gian lận để lại
                                <br />
                                dấu vết.
                                <br />
                                <span>AEGIS kết nối chúng.</span>
                            </h1>
                            <p>
                                Nhìn xa hơn một giao dịch. Kết nối tín hiệu,
                                <br className="desktop-break" /> khám phá mạng lưới và điều tra rủi ro với góc nhìn toàn
                                cảnh.
                            </p>
                            <div className="hero-buttons">
                                <button className="btn primary" onClick={onDemo}>
                                    Trải nghiệm nền tảng <ArrowUpRight size={17} />
                                </button>
                                <Link className="btn ghost" to="/login">
                                    <Code2 size={17} /> Developer console
                                </Link>
                            </div>
                            <div className="hero-assurance">
                                <span>
                                    <Check size={13} /> Sandbox sẵn dữ liệu
                                </span>
                                <span>
                                    <Check size={13} /> Không cần cấu hình
                                </span>
                            </div>
                        </Reveal>
                        <div className="hero-index">
                            <span>01 — FROM SIGNAL TO INSIGHT</span>
                            <a href="#platform" aria-label="Khám phá nền tảng">
                                <ArrowDown size={16} />
                            </a>
                        </div>
                    </div>
                    <Reveal className="hero-visual" delay={150}>
                        <div className="hero-visual-tag">
                            <span className="live-dot" /> SEE THE INVISIBLE.
                        </div>
                        <RadarScene />
                        <div className="hero-caption">
                            <span>
                                <ShieldCheck size={14} /> Giữ niềm tin ở mọi điểm kết nối.
                            </span>
                            <button
                                className="icon-button"
                                aria-label={paused ? 'Tiếp tục animation' : 'Tạm dừng animation'}
                                onClick={() => setPaused(!paused)}
                            >
                                {paused ? <Play size={13} /> : <Pause size={13} />}
                            </button>
                        </div>
                    </Reveal>
                </section>
                <div className="marketing-strip">
                    <span>
                        ONE PLATFORM.
                        <br />
                        <b>EVERY CONNECTION.</b>
                    </span>
                    <div>
                        <Activity size={21} />
                        Transaction monitoring
                    </div>
                    <div>
                        <Network size={21} />
                        Network investigation
                    </div>
                    <div>
                        <Code2 size={21} />
                        API-first integration
                    </div>
                    <div>
                        <ShieldCheck size={21} />
                        Explainable signals
                    </div>
                </div>
                <section className="platform-section" id="platform">
                    <Reveal>
                        <div className="section-intro">
                            <div>
                                <div className="eyebrow">THE PLATFORM / 01</div>
                                <h2>
                                    Biến dấu hiệu rời rạc
                                    <br />
                                    thành bức tranh rõ ràng.
                                </h2>
                            </div>
                            <p>
                                Một không gian làm việc cho đội ngũ chống gian lận.
                                <br />
                                Từ cảnh báo đầu tiên đến quyết định cuối cùng.
                            </p>
                        </div>
                    </Reveal>
                    <div className="feature-grid">
                        {[
                            {
                                Icon: ScanLine,
                                title: 'Mọi tín hiệu đều đáng được nhìn thấy.',
                                body: 'Theo dõi hồ sơ cảnh báo, phân loại mức rủi ro và ưu tiên những trường hợp cần điều tra.',
                                tag: 'MONITOR',
                                n: '01',
                            },
                            {
                                Icon: Network,
                                title: 'Theo dòng tiền. Tìm điểm kết nối.',
                                body: 'Khám phá tài khoản trung gian, giao dịch liên quan và bằng chứng qua đồ thị dòng tiền trực quan.',
                                tag: 'CONNECT',
                                n: '02',
                            },
                            {
                                Icon: Workflow,
                                title: 'Điều tra có quy trình. Quyết định có căn cứ.',
                                body: 'Đối chiếu rule hits, xem giải thích và chuyển hồ sơ từ tiếp nhận đến kết luận điều tra.',
                                tag: 'INVESTIGATE',
                                n: '03',
                            },
                        ].map(({ Icon, title, body, tag, n }, i) => (
                            <Reveal delay={i * 80} key={tag}>
                                <article className="feature-card">
                                    <div className="feature-card-top">
                                        <span className="feature-icon">
                                            <Icon size={25} strokeWidth={1.5} />
                                        </span>
                                        <span>{n}</span>
                                    </div>
                                    <span className="eyebrow">{tag}</span>
                                    <h3>{title}</h3>
                                    <p>{body}</p>
                                    <button className="text-button" onClick={onDemo}>
                                        Khám phá <ArrowUpRight size={15} />
                                    </button>
                                </article>
                            </Reveal>
                        ))}
                    </div>
                </section>
                <section className="workflow-section" id="workflow">
                    <Reveal>
                        <div className="eyebrow">BUILT FOR YOUR WORKFLOW / 02</div>
                        <h2>
                            Từ dữ liệu.
                            <br />
                            Đến thấu hiểu.
                        </h2>
                        <p>
                            Đội kỹ thuật tích hợp qua API.
                            <br />
                            Đội vận hành điều tra trên một giao diện thống nhất.
                        </p>
                        <Link to="/login" className="text-button">
                            Mở developer console <ArrowRight size={16} />
                        </Link>
                    </Reveal>
                    <Reveal className="workflow-code" delay={100}>
                        <div className="code-window-top">
                            <span>
                                <i />
                                <i />
                                <i />
                            </span>
                            <span>AEGIS / API RESPONSE</span>
                            <Pill tone="green">200 OK</Pill>
                        </div>
                        <pre>
                            <span className="code-comment">// Hồ sơ cảnh báo · ví dụ response</span>
                            {'\n'}
                            {'{\n  '}
                            <span className="code-key">"transactionRef"</span>
                            {': "TX-2026-010481",\n  '}
                            <span className="code-key">"riskScore"</span>
                            {': '}
                            <span className="code-number">95</span>
                            {',\n  '}
                            <span className="code-key">"riskLevel"</span>
                            {': '}
                            <span className="code-warning">"CRITICAL"</span>
                            {',\n  '}
                            <span className="code-key">"reviewStatus"</span>
                            {': "NEW",\n  '}
                            <span className="code-key">"ruleHits"</span>
                            {': [\n    { "ruleCode": "MULTI_ACCOUNT_FLOW" }\n  ]\n}'}
                        </pre>
                        <div className="code-window-bottom">
                            <span>
                                <i className="live-dot" /> CONNECTED INTELLIGENCE
                            </span>
                            <Code2 size={16} />
                        </div>
                    </Reveal>
                </section>
                <Reveal className="cta-section">
                    <div>
                        <div className="eyebrow">YOUR NEXT MOVE STARTS HERE</div>
                        <h2>
                            Nhìn rõ rủi ro.
                            <br />
                            <span>Tự tin hành động.</span>
                        </h2>
                    </div>
                    <div>
                        <p>
                            Khám phá nền tảng với dữ liệu mô phỏng
                            <br />
                            hoặc kết nối tới backend của ngân hàng.
                        </p>
                        <button className="btn primary" onClick={onDemo}>
                            Bắt đầu khám phá <ArrowUpRight size={17} />
                        </button>
                        <Link to="/login">
                            Đã có tài khoản? Đăng nhập <ChevronRight size={13} />
                        </Link>
                    </div>
                    <div className="cta-orbit" />
                </Reveal>
            </main>
            <footer className="marketing-footer">
                <Brand />
                <p>Banking Fraud Detection · Sản phẩm demo</p>
                <span>© 2026 AEGIS. Designed for clarity.</span>
            </footer>
        </div>
    )
}
