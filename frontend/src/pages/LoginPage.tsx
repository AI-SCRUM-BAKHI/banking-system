import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
    ArrowLeft,
    ArrowRight,
    ArrowUpRight,
    Eye,
    EyeOff,
    LockKeyhole,
    Play,
    ShieldCheck,
    LoaderCircle,
} from 'lucide-react'
import { Brand, Pill } from '../components/ui'
import { RadarScene } from '../components/NetworkScene'
import { login, currentUser } from '../services/auth.service'
import { setTokens } from '../lib/session'
import { errorMessage } from '../api/axios'
import type { CurrentUser } from '../types/auth.types'

export function LoginPage({ onLogin, onDemo }: { onLogin: (user: CurrentUser) => void; onDemo: () => void }) {
    const [show, setShow] = useState(false)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')
    const [registration, setRegistration] = useState(false)
    async function submit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault()
        const data = new FormData(e.currentTarget)
        setBusy(true)
        setError('')
        try {
            const tokens = await login({
                username: String(data.get('username')),
                password: String(data.get('password')),
            })
            setTokens(tokens)
            const user = await currentUser()
            onLogin(user)
        } catch (e) {
            setTokens(null)
            setError(errorMessage(e))
        } finally {
            setBusy(false)
        }
    }
    return (
        <main className="login-page" id="main-content">
            <section className="login-story">
                <Link to="/">
                    <Brand light />
                </Link>
                <div className="login-story-copy">
                    <Pill tone="glass">
                        <span className="live-dot" /> BANKING FRAUD INTELLIGENCE
                    </Pill>
                    <h1>
                        Niềm tin được xây
                        <br />
                        từ những kết nối
                        <br />
                        <span>bạn nhìn thấy.</span>
                    </h1>
                    <p>
                        Một góc nhìn toàn cảnh cho những người
                        <br />
                        đứng sau mỗi giao dịch an toàn.
                    </p>
                </div>
                <RadarScene compact />
                <div className="login-story-bottom">
                    <ShieldCheck size={18} />
                    <span>Clarity in every signal.</span>
                    <span>AEGIS / 2026</span>
                </div>
            </section>
            <section className="login-form-side">
                <Link to="/" className="back-link">
                    <ArrowLeft size={15} /> Về trang chủ
                </Link>
                <div className="login-form-wrap">
                    <div className="eyebrow">WELCOME TO YOUR WORKSPACE</div>
                    <h2>{registration ? 'Kết nối với AEGIS.' : 'Chào mừng trở lại.'}</h2>
                    <p>
                        {registration
                            ? 'Tài khoản được cấp bởi quản trị viên ngân hàng.'
                            : 'Đăng nhập để tiếp tục điều tra, kết nối và thấu hiểu.'}
                    </p>
                    {registration ? (
                        <div className="registration-info">
                            <LockKeyhole size={29} />
                            <h3>Workspace dành riêng cho tổ chức</h3>
                            <p>
                                Để truy cập dữ liệu thật, quản trị viên tạo tài khoản BANK_USER trong mục Thành viên.
                                Backend hiện không hỗ trợ tự đăng ký công khai.
                            </p>
                            <button className="btn primary full" onClick={onDemo}>
                                Khám phá workspace demo <ArrowUpRight size={16} />
                            </button>
                            <button className="text-button" onClick={() => setRegistration(false)}>
                                Tôi đã có tài khoản <ArrowRight size={15} />
                            </button>
                        </div>
                    ) : (
                        <>
                            <form onSubmit={submit}>
                                <label>
                                    Tên đăng nhập
                                    <input
                                        name="username"
                                        placeholder="Nhập tên đăng nhập"
                                        required
                                        autoComplete="username"
                                    />
                                </label>
                                <label>
                                    Mật khẩu
                                    <div className="password-field">
                                        <input
                                            name="password"
                                            type={show ? 'text' : 'password'}
                                            placeholder="Nhập mật khẩu"
                                            required
                                            autoComplete="current-password"
                                        />
                                        <button
                                            type="button"
                                            className="icon-button"
                                            aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                                            onClick={() => setShow(!show)}
                                        >
                                            {show ? <EyeOff size={17} /> : <Eye size={17} />}
                                        </button>
                                    </div>
                                </label>
                                <div className="login-meta">
                                    <span>
                                        <LockKeyhole size={12} /> Kết nối backend bằng JWT
                                    </span>
                                    <span>Secure workspace</span>
                                </div>
                                {error && (
                                    <p className="form-error" role="alert">
                                        {error}
                                    </p>
                                )}
                                <button className="btn primary full" type="submit" disabled={busy}>
                                    {busy ? <LoaderCircle size={17} className="spin" /> : null}
                                    {busy ? 'Đang kết nối…' : 'Đăng nhập workspace'}
                                    <ArrowRight size={16} />
                                </button>
                            </form>
                            <div className="form-or">
                                <span />
                                hoặc trải nghiệm trước
                                <span />
                            </div>
                            <button className="btn secondary full" onClick={onDemo}>
                                <Play size={15} /> Vào workspace demo
                            </button>
                            <div className="login-demo-note">
                                Không cần tài khoản · Dữ liệu tổng hợp · Không gọi backend
                            </div>
                            <div className="login-register">
                                Bạn chưa có tài khoản?{' '}
                                <button onClick={() => setRegistration(true)}>
                                    Hướng dẫn đăng ký <ArrowUpRight size={12} />
                                </button>
                            </div>
                        </>
                    )}
                    <div className="login-form-footer">Bằng chứng rõ ràng. Quyết định có căn cứ.</div>
                </div>
                <div className="login-copyright">© 2026 AEGIS · Banking Fraud Detection</div>
            </section>
        </main>
    )
}
