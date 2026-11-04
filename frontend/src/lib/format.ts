export const number = (n: number) => new Intl.NumberFormat('vi-VN').format(n)
export const money = (n: number, currency = 'VND') =>
    new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(n)
export const shortMoney = (n: number) => (n >= 1e9 ? `${(n / 1e9).toFixed(2)} tỷ` : `${(n / 1e6).toFixed(1)} tr`)
export const dateTime = (date: string) =>
    new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short' }).format(
        new Date(date),
    )
export function exportCsv(rows: Record<string, unknown>[], filename: string) {
    if (!rows.length) return
    const fields = Object.keys(rows[0])
    const cell = (value: unknown) => {
        let text = String(value ?? '')
        if (/^[=+@\-\t\r]/.test(text)) text = `'${text}`
        return `"${text.replaceAll('"', '""')}"`
    }
    const blob = new Blob(
        [
            '\uFEFF' +
                [fields.map(cell).join(','), ...rows.map((row) => fields.map((f) => cell(row[f])).join(','))].join(
                    '\r\n',
                ),
        ],
        { type: 'text/csv;charset=utf-8' },
    )
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
}
