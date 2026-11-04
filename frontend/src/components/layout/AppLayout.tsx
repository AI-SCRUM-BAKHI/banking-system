import type { ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router-dom'

const links = [
    { label: 'Tổng quan', href: '/dashboard', icon: 'grid' },
    { label: 'Giao dịch', href: '/transactions', icon: 'arrows' },
    { label: 'Cảnh báo fraud', href: '/fraud-alerts', icon: 'shield' },
    { label: 'Điều tra', href: '/investigations', icon: 'network' },
]

function NavigationIcon({ name }: { name: string }) {
    const paths: Record<string, ReactNode> = {
        grid: (
            <>
                <rect x="3" y="3" width="7" height="7" rx="1" />
                <rect x="14" y="3" width="7" height="7" rx="1" />
                <rect x="3" y="14" width="7" height="7" rx="1" />
                <rect x="14" y="14" width="7" height="7" rx="1" />
            </>
        ),
        arrows: <path d="M4 7h14M15 4l3 3-3 3M20 17H6m3-3-3 3 3 3" />,
        shield: (
            <>
                <path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z" />
                <path d="m9 12 2 2 4-4" />
            </>
        ),
        network: (
            <>
                <circle cx="5" cy="6" r="2" />
                <circle cx="19" cy="6" r="2" />
                <circle cx="12" cy="18" r="2" />
                <path d="m7 7.5 3.5 8.7M17 7.5l-3.5 8.7M7 6h10" />
            </>
        ),
    }

    return (
        <svg
            className="size-[18px] shrink-0"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            aria-hidden="true"
        >
            {paths[name]}
        </svg>
    )
}

export function AppLayout() {
    return (
        <div className="grid min-h-dvh grid-cols-[248px_minmax(0,1fr)] max-md:grid-cols-1">
            <a
                className="fixed left-3 top-3 z-[500] -translate-y-[160%] rounded-md bg-accent px-3.5 py-2.5 font-bold text-[#14200a] focus:translate-y-0"
                href="#main-content"
            >
                Bỏ qua điều hướng
            </a>

            <aside
                className="sticky top-0 flex h-dvh flex-col border-r border-line bg-[#1d211d]/95 px-4 py-6 max-md:static max-md:h-auto max-md:border-r-0 max-md:border-b max-md:px-3.5 max-md:py-2.5"
                aria-label="Thanh điều hướng ứng dụng"
            >
                <NavLink
                    to="/dashboard"
                    className="flex items-center gap-3 px-2.5 pb-[30px] pt-0.5 text-copy no-underline max-md:gap-2.5 max-md:px-0.5 max-md:pb-2.5"
                >
                    <span className="grid size-[38px] place-items-center rounded-md border border-accent/45 bg-gradient-to-br from-accent/20 to-accent/5 text-accent-bright max-md:size-8">
                        <svg
                            viewBox="0 0 24 24"
                            className="size-[21px]"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.7"
                            aria-hidden="true"
                        >
                            <path d="M12 2.8 20 6v5.3c0 5.1-3.4 8.2-8 10-4.6-1.8-8-4.9-8-10V6l8-3.2Z" />
                            <path d="M8 12h8M12 8v8" />
                        </svg>
                    </span>
                    <span>
                        <span className="block text-[13px] font-bold tracking-[0.08em]">BANKSHIELD</span>
                        <span className="mt-0.5 block text-[9px] uppercase tracking-[0.14em] text-dim">
                            Fraud intelligence
                        </span>
                    </span>
                </NavLink>

                <div className="mb-2.5 px-3 text-[10px] font-semibold uppercase tracking-[0.14em] text-dim max-md:hidden">
                    Không gian làm việc
                </div>
                <nav
                    className="flex flex-col gap-[5px] max-md:flex-row max-md:gap-[5px] max-md:overflow-x-auto max-md:pb-0.5"
                    aria-label="Điều hướng chính"
                >
                    {links.map(({ label, href, icon }) => (
                        <NavLink
                            key={href}
                            to={href}
                            className={({ isActive }) =>
                                `relative flex min-h-11 items-center gap-3 rounded-md border px-3 text-[12px] no-underline transition-colors duration-200 max-md:min-h-10 max-md:flex-none max-md:gap-2 max-md:px-2.5 max-md:text-[10px] ${isActive ? 'border-accent/20 bg-accent/10 text-accent-bright before:absolute before:bottom-2.5 before:left-[-1px] before:top-2.5 before:w-0.5 before:rounded-sm before:bg-accent max-md:before:bottom-[-1px] max-md:before:left-2.5 max-md:before:right-2.5 max-md:before:top-auto max-md:before:h-0.5 max-md:before:w-auto' : 'border-transparent text-muted hover:bg-panel-hover hover:text-copy'}`
                            }
                        >
                            <NavigationIcon name={icon} />
                            <span>{label}</span>
                        </NavLink>
                    ))}
                </nav>

                <div className="mt-auto max-md:hidden">
                    <div className="mb-2.5 px-3 text-[10px] font-semibold uppercase tracking-[0.14em] text-dim">
                        Trạng thái
                    </div>
                    <div className="flex items-center gap-2.5 rounded-md border border-line bg-panel p-3">
                        <span className="signal-pulse size-[7px] shrink-0 rounded-full bg-signal" aria-hidden="true" />
                        <span>
                            <span className="block text-[11px] font-semibold text-copy">Bản xem trước</span>
                            <span className="block text-[10px] text-dim">Chưa kết nối API</span>
                        </span>
                    </div>
                </div>
            </aside>

            <div className="min-w-0">
                <header className="flex min-h-[68px] items-center justify-between gap-5 border-b border-line bg-canvas/80 px-[clamp(20px,3vw,44px)] max-md:min-h-[52px] max-md:px-4">
                    <div className="text-[11px] text-muted max-[420px]:text-[9px]">
                        <strong className="font-medium text-copy">Ngân hàng Bakhi</strong>{' '}
                        <span aria-hidden="true">/</span> Trung tâm giám sát
                    </div>
                    <div className="flex items-center gap-[18px] max-md:gap-2.5">
                        <span className="inline-flex items-center gap-2 rounded-full border border-line px-2.5 py-1.5 text-[10px] uppercase tracking-[0.06em] text-muted max-[420px]:px-2 max-[420px]:text-[8px]">
                            <span className="size-1.5 rounded-full bg-signal" aria-hidden="true" />
                            Bản xem trước
                        </span>
                        <div
                            className="flex items-center gap-[9px] text-[11px] text-muted"
                            aria-label="Chế độ người dùng xem trước"
                        >
                            <span
                                className="grid size-[30px] place-items-center rounded-full border border-[#505a4d] bg-panel-raised text-[10px] font-semibold text-accent-bright"
                                aria-hidden="true"
                            >
                                BA
                            </span>
                            <span className="max-md:hidden">Bank analyst</span>
                        </div>
                    </div>
                </header>
                <main
                    className="mx-auto w-full max-w-[1600px] px-[clamp(20px,3vw,44px)] pb-12 pt-8 max-md:px-4 max-md:pb-[34px] max-md:pt-6"
                    id="main-content"
                >
                    <Outlet />
                </main>
            </div>
        </div>
    )
}
