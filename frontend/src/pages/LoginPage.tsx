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
    UserPlus,
} from 'lucide-react'
import { Brand, Pill } from '../components/ui'
import { RadarScene } from '../components/NetworkScene'
import { login, register, currentUser } from '../services/auth.service'
import { setTokens } from '../lib/session'
import { errorMessage } from '../api/axios'
import type { CurrentUser } from '../types/auth.types'

export function LoginPage({ onLogin, onDemo }: { onLogin: (user: CurrentUser) => void; onDemo: () => void }) {
    const [show, setShow] = useState(false)
    const [showConfirm, setShowConfirm] = useState(false)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')
    const [registration, setRegistration] = useState(false)

        async function submitLogin(e: FormEvent<HTMLFormElement>) {
            e.preventDefault()
            const data = new FormData(e.currentTarget)
            setBusy(true)
            setError('')
            try {
                const tokens = await login({
                    username: String(data.get('username') || '').trim(),
                    password: String(data.get('password') || ''),
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

        async function submitRegister(e: FormEvent<HTMLFormElement>) {
            e.preventDefault()
            const data = new FormData(e.currentTarget)
            const username = String(data.get('username') || '').trim()
            const fullName = String(data.get('fullName') || '').trim()
            const email = String(data.get('email') || '').trim()
            const password = String(data.get('password') || '')
            const confirmPassword = String(data.get('confirmPassword') || '')

            if (!fullName || !email || !username || !password) {
                setError('Vui lòng điền đầy đủ các trường thông tin.')
                return
            }
            if (password.length < 12) {
                setError('Mật khẩu phải chứa ít nhất 12 ký tự.')
                return
            }
            if (password !== confirmPassword) {
                setError('Mật khẩu xác nhận không khớp.')
                return
            }

            setBusy(true)
            setError('')
            try {
                const tokens = await register({
                    username,
                    password,
                    fullName,
                    email,
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
                        <h2>{registration ? 'Đăng ký tài khoản.' : 'Chào mừng trở lại.'}</h2>
                        <p>
                            {registration
                                    ? 'Tạo tài khoản mới để bắt đầu giám sát và phân tích giao dịch gian lận.'
                                    : 'Đăng nhập để tiếp tục điều tra, kết nối và thấu hiểu.'}
                        </p>

                        {registration ? (
                                    <form onSubmit={submitRegister}>
                                        <label>
                                            Họ và tên
                                            <input
                                                name="fullName"
                                                placeholder="Ví dụ: Nguyễn Văn A"
                                                required
                                                autoComplete="name"
                                            />
                                        </label>
                                        <label>
                                            Địa chỉ Email
                                            <input
                                                name="email"
                                                type="email"
                                                placeholder="name@example.com"
                                                required
                                                autoComplete="email"
                                            />
                                        </label>
                                        <label>
                                            Tên đăng nhập
                                            <input
                                                name="username"
                                                placeholder="Chọn tên đăng nhập (ví dụ: nguyenvana)"
                                                required
                                                autoComplete="username"
                                            />
                                        </label>
                                        <label>
                                            Mật khẩu (tối thiểu 12 ký tự)
                                            <div className="password-field">
                                                <input
                                                    name="password"
                                                    type={show ? 'text' : 'password'}
                                                    placeholder="Nhập mật khẩu ít nhất 12 ký tự"
                                                    required
                                                    minLength={12}
                                                    autoComplete="new-password"
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
                                        <label>
                                            Xác nhận mật khẩu
                                            <div className="password-field">
                                                <input
                                                    name="confirmPassword"
                                                    type={showConfirm ? 'text' : 'password'}
                                                    placeholder="Nhập lại mật khẩu"
                                                    required
                                                    minLength={12}
                                                    autoComplete="new-password"
                                                />
                                                <button
                                                    type="button"
                                                    className="icon-button"
                                                    aria-label={showConfirm ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                                                    onClick={() => setShowConfirm(!showConfirm)}
                                                >
                                                    {showConfirm ? <EyeOff size={17} /> : <Eye size={17} />}
                                                </button>
                                            </div>
                                        </label>

                                        {error && (
                                            <p className="form-error" role="alert">
                                                {error}
                                            </p>
                                        )}

                                        <button className="btn primary full" type="submit" disabled={busy}>
                                            {busy ? <LoaderCircle size={17} className="spin" /> : <UserPlus size={16} />}
                                            {busy ? 'Đang tạo tài khoản…' : 'Đăng ký ngay'}
                                        </button>

                                    <div className="login-register">
                                        Bạn đã có tài khoản?{' '}
                                        <button
                                            type="button"
                                            onClick={() => {
                                                setRegistration(false)
                                                setError('')
                                            }}
                                        >
                                            Đăng nhập ngay <ArrowRight size={12} />
                                        </button>
                                    </div>
                                </form>
                                ) : (
                                <>
                                        <form onSubmit={submitLogin}>
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
                                                <button
                                                    type="button"
                                                    onClick={() => {
                                                        setRegistration(true)
                                                        setError('')
                                                    }}
                                                >
                                                    Đăng ký tài khoản mới <ArrowUpRight size={12} />
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
