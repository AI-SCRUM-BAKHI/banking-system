import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, PropsWithChildren } from 'react'
import {
    AlertCircle,
    ArrowRight,
    ArrowUpRight,
    ChevronLeft,
    ChevronRight,
    LoaderCircle,
    ShieldCheck,
    X,
} from 'lucide-react'
import type { ReviewStatus, RiskLevel } from '../../types/fraud.types'
import { riskLabel, statusLabel } from '../../lib/labels'

export function Brand({ light = false }: { light?: boolean }) {
    return (
        <span className={'brand ' + (light ? 'light' : '')}>
            <span className="brand-mark">
                <ShieldCheck size={23} strokeWidth={1.8} />
            </span>
            <span>
                AEGIS<span className="brand-sub">FRAUD INTELLIGENCE</span>
            </span>
            <span className="brand-reg">®</span>
        </span>
    )
}
export function Pill({ children, tone = 'blue' }: PropsWithChildren<{ tone?: string }>) {
    return <span className={'pill ' + tone}>{children}</span>
}
export function RiskBadge({ risk }: { risk: RiskLevel }) {
    return (
        <Pill tone={risk === 'CRITICAL' ? 'red' : risk === 'HIGH' ? 'orange' : risk === 'MEDIUM' ? 'amber' : 'green'}>
            <i />
            {riskLabel[risk]}
        </Pill>
    )
}
export function StatusBadge({ status }: { status: ReviewStatus }) {
    return (
        <Pill
            tone={
                status === 'CONFIRMED_FRAUD'
                    ? 'red'
                    : status === 'REVIEWING'
                      ? 'blue'
                      : status === 'FALSE_POSITIVE'
                        ? 'green'
                        : 'neutral'
            }
        >
            {statusLabel[status]}
        </Pill>
    )
}
export function Panel({
    title,
    subtitle,
    action,
    children,
    className = '',
}: PropsWithChildren<{ title?: string; subtitle?: string; action?: React.ReactNode; className?: string }>) {
    return (
        <section className={'panel ' + className}>
            {title && (
                <div className="panel-head">
                    <div>
                        <h2>{title}</h2>
                        {subtitle && <p>{subtitle}</p>}
                    </div>
                    {action}
                </div>
            )}
            {children}
        </section>
    )
}
export function Loading({ text = 'Đang tải dữ liệu…' }: { text?: string }) {
    return (
        <div className="state-box" role="status">
            <LoaderCircle className="spin" size={27} />
            <p>{text}</p>
        </div>
    )
}
export function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
    return (
        <div className="state-box error" role="alert">
            <AlertCircle size={27} />
            <h3>Chưa thể tải dữ liệu</h3>
            <p>{message}</p>
            {retry && (
                <button className="btn secondary small" onClick={retry}>
                    Thử lại <ArrowRight size={14} />
                </button>
            )}
        </div>
    )
}
export function Empty({
    title = 'Chưa có hồ sơ phù hợp',
    text = 'Thử điều chỉnh bộ lọc để mở rộng kết quả.',
}: {
    title?: string
    text?: string
}) {
    return (
        <div className="state-box">
            <ShieldCheck size={32} />
            <h3>{title}</h3>
            <p>{text}</p>
        </div>
    )
}
export function Modal({
    title,
    children,
    onClose,
    className = '',
}: PropsWithChildren<{ title: string; onClose: () => void; className?: string }>) {
    const ref = useRef<HTMLDivElement>(null)
    useEffect(() => {
        const previous = document.activeElement as HTMLElement
        const old = document.body.style.overflow
        document.body.style.overflow = 'hidden'
        ref.current?.focus()
        const key = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose()
            if (e.key === 'Tab') {
                const elements = ref.current?.querySelectorAll<HTMLElement>(
                    'button:not(:disabled),a[href],input,select,textarea,[tabindex="0"]',
                )
                if (!elements?.length) return
                const first = elements[0],
                    last = elements[elements.length - 1]
                if (e.shiftKey && (document.activeElement === first || document.activeElement === ref.current)) {
                    e.preventDefault()
                    last.focus()
                } else if (!e.shiftKey && document.activeElement === last) {
                    e.preventDefault()
                    first.focus()
                }
            }
        }
        document.addEventListener('keydown', key)
        return () => {
            document.body.style.overflow = old
            document.removeEventListener('keydown', key)
            previous?.focus()
        }
    }, [onClose])
    return (
        <div className="modal-backdrop" onClick={onClose}>
            <div
                ref={ref}
                className={'modal ' + className}
                role="dialog"
                aria-modal="true"
                aria-label={title}
                tabIndex={-1}
                onClick={(e) => e.stopPropagation()}
            >
                <button className="icon-button modal-close" aria-label="Đóng" onClick={onClose}>
                    <X size={20} />
                </button>
                {children}
            </div>
        </div>
    )
}
export function AnimatedNumber({ value }: { value: number }) {
    const [shown, setShown] = useState(value)
    const current = useRef(value)
    useEffect(() => {
        const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
        let raf = 0
        const start = performance.now(),
            from = current.current
        const step = (now: number) => {
            const progress = reduced ? 1 : Math.min((now - start) / 950, 1)
            const next = Math.round(from + (value - from) * (1 - Math.pow(1 - progress, 3)))
            current.current = next
            setShown(next)
            if (progress < 1) raf = requestAnimationFrame(step)
        }
        raf = requestAnimationFrame(step)
        return () => cancelAnimationFrame(raf)
    }, [value])
    return <>{new Intl.NumberFormat('vi-VN').format(shown)}</>
}
export function Reveal({
    children,
    delay = 0,
    className = '',
}: PropsWithChildren<{ delay?: number; className?: string }>) {
    const ref = useRef<HTMLDivElement>(null)
    useEffect(() => {
        const observer = new IntersectionObserver(
            ([entry]) => {
                if (entry.isIntersecting) {
                    ref.current?.classList.add('revealed')
                    observer.disconnect()
                }
            },
            { threshold: 0.08 },
        )
        if (ref.current) observer.observe(ref.current)
        return () => observer.disconnect()
    }, [])
    return (
        <div ref={ref} className={'reveal ' + className} style={{ '--delay': `${delay}ms` } as CSSProperties}>
            {children}
        </div>
    )
}
export function Pagination({
    page,
    totalPages,
    total,
    size,
    onPage,
}: {
    page: number
    totalPages: number
    total: number
    size: number
    onPage: (p: number) => void
}) {
    return (
        <div className="pagination">
            <span>
                {total ? `${page * size + 1}–${Math.min((page + 1) * size, total)}` : '0'} / {total} hồ sơ
            </span>
            <div>
                <button
                    aria-label="Trang trước"
                    className="icon-button"
                    disabled={page === 0}
                    onClick={() => onPage(page - 1)}
                >
                    <ChevronLeft size={16} />
                </button>
                <b>
                    {page + 1} <span>/ {Math.max(1, totalPages)}</span>
                </b>
                <button
                    aria-label="Trang sau"
                    className="icon-button"
                    disabled={page + 1 >= totalPages}
                    onClick={() => onPage(page + 1)}
                >
                    <ChevronRight size={16} />
                </button>
            </div>
        </div>
    )
}
export function ArrowLink({ children, onClick }: PropsWithChildren<{ onClick: () => void }>) {
    return (
        <button className="text-button" onClick={onClick}>
            {children}
            <ArrowUpRight size={15} />
        </button>
    )
}
