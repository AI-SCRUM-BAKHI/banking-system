import { useCallback, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import { ArrowRight, LoaderCircle, Plus, ShieldCheck, Users } from 'lucide-react'
import { useConsole } from '../lib/console-context'
import { apiClient, errorMessage } from '../api/axios'
import { demoUser } from '../lib/demo'
import { ErrorState, Loading, Modal, Panel, Pagination, Pill } from '../components/ui'
import type { ApiResponse } from '../types/api.types'
import type { CurrentUser } from '../types/auth.types'
import type { Page } from '../types/fraud.types'
const initialDemoUsers: CurrentUser[] = [
    demoUser,
    {
        id: 'demo-2',
        username: 'quanghuy',
        fullName: 'Trần Quang Huy',
        email: 'huy@example.com',
        role: 'BANK_USER',
        status: 'ACTIVE',
    },
    {
        id: 'demo-3',
        username: 'thao.nguyen',
        fullName: 'Nguyễn Phương Thảo',
        email: 'thao@example.com',
        role: 'BANK_USER',
        status: 'ACTIVE',
    },
]
export function UsersPage() {
    const { session, notify } = useConsole()
    const [modal, setModal] = useState(false)
    const [page, setPage] = useState(0)
    const [role, setRole] = useState('')
    const [local, setLocal] = useState(initialDemoUsers)
    const client = useQueryClient()
    const close = useCallback(() => setModal(false), [])
    const query = useQuery({
        queryKey: ['users', session.mode, page, role, local],
        enabled: session.user.role === 'ADMIN',
        queryFn: async () => {
            if (session.mode === 'demo') {
                const rows = local.filter((u) => !role || u.role === role)
                return {
                    content: rows.slice(page * 10, (page + 1) * 10),
                    page,
                    size: 10,
                    totalElements: rows.length,
                    totalPages: Math.ceil(rows.length / 10),
                }
            }
            return (
                await apiClient.get<ApiResponse<Page<CurrentUser>>>('/users', {
                    params: { page, size: 10, role: role || undefined },
                })
            ).data.data
        },
    })
    const toggle = useMutation({
        mutationFn: async (user: CurrentUser) => {
            const status = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
            if (session.mode === 'demo') {
                setLocal((rows) => rows.map((u) => (u.id === user.id ? { ...u, status } : u)))
                return
            }
            await apiClient.patch('/users/' + user.id + '/status', { status })
        },
        onSuccess: () => {
            client.invalidateQueries({ queryKey: ['users'] })
            notify('Đã cập nhật trạng thái thành viên.')
        },
    })
    if (session.user.role !== 'ADMIN') return <Navigate to="/console" replace />
    return (
        <>
            <div className="page-title">
                <div>
                    <div className="eyebrow">ORGANIZATION / TEAM ACCESS</div>
                    <h1>
                        Đúng người. Đúng kết nối<span>.</span>
                    </h1>
                    <p>Cấp tài khoản cho đội kỹ thuật và vận hành ngân hàng.</p>
                </div>
                <button className="btn primary small" onClick={() => setModal(true)}>
                    <Plus size={16} />
                    Thêm thành viên
                </button>
            </div>
            <div className="info-banner">
                <ShieldCheck size={19} />
                <p>
                    Tạo tài khoản qua API chỉ dành cho ADMIN. Vai trò BANK_USER được truy cập hồ sơ, đồ thị và quy trình
                    điều tra; không quản lý thành viên.
                </p>
            </div>
            <Panel
                title="Thành viên workspace"
                subtitle="Tài khoản được quản trị viên cấp quyền"
                action={
                    <select
                        aria-label="Lọc vai trò"
                        value={role}
                        onChange={(e) => {
                            setRole(e.target.value)
                            setPage(0)
                        }}
                    >
                        <option value="">Tất cả vai trò</option>
                        <option value="ADMIN">ADMIN</option>
                        <option value="BANK_USER">BANK_USER</option>
                    </select>
                }
            >
                {query.isPending ? (
                    <Loading />
                ) : query.isError ? (
                    <ErrorState message={errorMessage(query.error)} retry={() => query.refetch()} />
                ) : (
                    <>
                        <div className="table-scroll">
                            <table>
                                <thead>
                                    <tr>
                                        <th>THÀNH VIÊN</th>
                                        <th>TÊN ĐĂNG NHẬP</th>
                                        <th>VAI TRÒ</th>
                                        <th>TRẠNG THÁI</th>
                                        <th>THAO TÁC</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {query.data?.content.map((user) => (
                                        <tr key={user.id}>
                                            <td>
                                                <div className="member-cell">
                                                    <span className="avatar">
                                                        {user.fullName
                                                            .split(' ')
                                                            .slice(-2)
                                                            .map((w) => w[0])
                                                            .join('')}
                                                    </span>
                                                    <span>
                                                        <b>{user.fullName}</b>
                                                        <small>{user.email}</small>
                                                    </span>
                                                </div>
                                            </td>
                                            <td className="mono">{user.username}</td>
                                            <td>
                                                <Pill tone={user.role === 'ADMIN' ? 'blue' : 'neutral'}>
                                                    {user.role}
                                                </Pill>
                                            </td>
                                            <td>
                                                <Pill tone={user.status === 'ACTIVE' ? 'green' : 'neutral'}>
                                                    <i />
                                                    {user.status === 'ACTIVE' ? 'Hoạt động' : 'Đã vô hiệu hóa'}
                                                </Pill>
                                            </td>
                                            <td>
                                                {user.id === session.user.id ? (
                                                    <span className="muted">Tài khoản của bạn</span>
                                                ) : (
                                                    <button
                                                        disabled={toggle.isPending}
                                                        className="text-button"
                                                        onClick={() => toggle.mutate(user)}
                                                    >
                                                        {user.status === 'ACTIVE' ? 'Vô hiệu hóa' : 'Kích hoạt'}
                                                        <ArrowRight size={13} />
                                                    </button>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                        <Pagination
                            page={page}
                            total={query.data?.totalElements || 0}
                            size={10}
                            totalPages={query.data?.totalPages || 0}
                            onPage={setPage}
                        />
                    </>
                )}
                {toggle.isError && (
                    <p className="form-error" role="alert">
                        {errorMessage(toggle.error)}
                    </p>
                )}
            </Panel>
            {modal && (
                <CreateUserModal
                    mode={session.mode}
                    close={close}
                    onCreated={(user) => {
                        if (session.mode === 'demo') setLocal((rows) => [user, ...rows])
                        setPage(0)
                        client.invalidateQueries({ queryKey: ['users'] })
                        setModal(false)
                        notify(
                            session.mode === 'demo'
                                ? 'Đã thêm thành viên mô phỏng trong phiên này.'
                                : 'Đã tạo tài khoản trên backend.',
                        )
                    }}
                />
            )}
        </>
    )
}
function CreateUserModal({
    mode,
    close,
    onCreated,
}: {
    mode: 'demo' | 'live'
    close: () => void
    onCreated: (user: CurrentUser) => void
}) {
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState('')
    async function submit(e: FormEvent<HTMLFormElement>) {
        e.preventDefault()
        const data = new FormData(e.currentTarget)
        const body = {
            username: String(data.get('username')),
            fullName: String(data.get('fullName')),
            email: String(data.get('email')),
            role: String(data.get('role')) as CurrentUser['role'],
            password: String(data.get('password')),
        }
        if (new TextEncoder().encode(body.password).length > 72) {
            setError('Mật khẩu tối đa 72 byte UTF-8.')
            return
        }
        setBusy(true)
        setError('')
        try {
            if (mode === 'demo')
                onCreated({
                    id: crypto.randomUUID(),
                    username: body.username,
                    fullName: body.fullName,
                    email: body.email,
                    role: body.role,
                    status: 'ACTIVE',
                })
            else onCreated((await apiClient.post<ApiResponse<CurrentUser>>('/users', body)).data.data)
        } catch (e) {
            setError(errorMessage(e))
        } finally {
            setBusy(false)
        }
    }
    return (
        <Modal title="Thêm thành viên" onClose={close} className="member-modal">
            <div className="form-modal-icon">
                <Users size={25} />
            </div>
            <div className="eyebrow">WORKSPACE ACCESS</div>
            <h2>Một thành viên mới.</h2>
            <p>Cấp tài khoản để cùng kết nối những dấu vết.</p>
            <form onSubmit={submit}>
                <label>
                    Họ và tên
                    <input name="fullName" required maxLength={120} placeholder="Nguyễn Minh Anh" />
                </label>
                <label>
                    Tên đăng nhập
                    <input name="username" required maxLength={50} placeholder="minhanh" />
                </label>
                <label>
                    Email
                    <input name="email" type="email" required maxLength={254} placeholder="operator@yourbank.vn" />
                </label>
                <label>
                    Mật khẩu ban đầu
                    <input
                        name="password"
                        type="password"
                        required
                        minLength={12}
                        maxLength={72}
                        autoComplete="new-password"
                        placeholder="Từ 12 ký tự, tối đa 72 byte UTF-8"
                    />
                </label>
                <label>
                    Vai trò
                    <select name="role" defaultValue="BANK_USER">
                        <option value="BANK_USER">BANK_USER — Nhân viên ngân hàng</option>
                        <option value="ADMIN">ADMIN — Quản trị viên</option>
                    </select>
                </label>
                {error && (
                    <p className="form-error" role="alert">
                        {error}
                    </p>
                )}
                {mode === 'demo' && (
                    <p className="member-demo-note">
                        Chế độ demo: chỉ thêm dữ liệu trong bộ nhớ frontend, không tạo tài khoản thật.
                    </p>
                )}
                <button type="submit" className="btn primary full" disabled={busy}>
                    {busy ? <LoaderCircle size={15} className="spin" /> : <Plus size={15} />}Tạo tài khoản
                </button>
            </form>
        </Modal>
    )
}
