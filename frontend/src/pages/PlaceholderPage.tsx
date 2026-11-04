export function PlaceholderPage({ title }: { title: string }) {
    return (
        <section className="max-w-[760px] rounded-md border border-line bg-gradient-to-br from-panel-raised/80 to-panel p-[26px] shadow-[0_10px_30px_rgba(0,0,0,0.16)]">
            <div className="text-[10px] font-semibold uppercase tracking-[0.14em] text-dim">Phân hệ nghiệp vụ</div>
            <h1 className="my-2 text-2xl font-semibold tracking-[-0.05em]">{title}</h1>
            <p className="mb-4 text-xs text-muted">
                Khung giao diện đã sẵn sàng. Phân hệ này sẽ được kết nối với API trong bước tiếp theo.
            </p>
            <span className="inline-flex items-center gap-2 rounded-full border border-line px-2.5 py-1.5 text-[10px] uppercase tracking-[0.06em] text-muted">
                <span className="size-1.5 rounded-full bg-signal" aria-hidden="true" />
                Đang thiết kế
            </span>
        </section>
    )
}
