export function PlaceholderPage({ title }: { title: string }) {
    return (
        <section className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
            <p className="text-sm font-medium text-indigo-600">Coming next</p>
            <h1 className="mt-2 text-2xl font-semibold text-slate-950">{title}</h1>
            <p className="mt-3 text-slate-600">Feature module placeholder for the first implementation sprint.</p>
        </section>
    )
}
