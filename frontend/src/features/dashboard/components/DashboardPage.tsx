export function DashboardPage() {
    return (
        <div className="space-y-8">
            <div>
                <p className="text-sm font-medium text-indigo-600">Operations overview</p>
                <h1 className="mt-2 text-3xl font-bold tracking-tight">Fraud detection dashboard</h1>
                <p className="mt-3 max-w-2xl text-slate-600">
                    A focused workspace for reviewing suspicious transactions, account relationships, and investigation
                    evidence.
                </p>
            </div>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {[
                    ['Open alerts', '—'],
                    ['High risk today', '—'],
                    ['Investigations', '—'],
                    ['Confirmed fraud', '—'],
                ].map(([label, value]) => (
                    <article key={label} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                        <p className="text-sm text-slate-500">{label}</p>
                        <p className="mt-3 text-3xl font-semibold text-slate-950">{value}</p>
                    </article>
                ))}
            </div>
            <section className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center">
                <h2 className="text-lg font-semibold">Connect the first API slice</h2>
                <p className="mx-auto mt-2 max-w-xl text-sm text-slate-600">
                    The application shell is ready. The next feature can add alert queries through the shared API client
                    and TanStack Query.
                </p>
            </section>
        </div>
    )
}
