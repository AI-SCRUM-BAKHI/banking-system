import { useId } from 'react'
import { ShieldCheck, Fingerprint, Radio } from 'lucide-react'
import type { TransactionGraph } from '../types/fraud.types'
import { shortMoney } from '../lib/format'

export function RadarScene({ compact = false }: { compact?: boolean }) {
    return (
        <div
            className={'radar-scene ' + (compact ? 'compact' : '')}
            aria-label="Animation minh họa phân tích mạng lưới giao dịch"
        >
            <div className="scene-grid" />
            <div className="scene-header">
                <span>
                    <i className="live-dot" /> AEGIS INTELLIGENCE ENGINE
                </span>
                <span>DEMO SCENARIO</span>
            </div>
            <div className="radar-orbit orbit-one" />
            <div className="radar-orbit orbit-two" />
            <div className="radar-orbit orbit-three" />
            <div className="radar-sweep" />
            <svg viewBox="0 0 600 460" className="radar-lines" aria-hidden="true">
                <path d="M300 225 145 115 M300 225 465 115 M300 225 495 300 M300 225 155 345 M300 225 72 240" />
                <path className="danger-line" d="M300 225 465 115 495 300 300 225" />
                {[
                    [145, 115],
                    [465, 115],
                    [495, 300],
                    [155, 345],
                    [72, 240],
                ].map(([x, y], i) => (
                    <circle key={i} cx={x} cy={y} r="3">
                        <animate attributeName="r" values="2;5;2" dur={`${2 + i * 0.3}s`} repeatCount="indefinite" />
                    </circle>
                ))}
            </svg>
            <div className="radar-core">
                <ShieldCheck size={38} strokeWidth={1.4} />
                <span className="core-ring" />
            </div>
            <div className="scene-node node-a">
                <Fingerprint size={19} />
                <span>AC · 1082</span>
                <i />
            </div>
            <div className="scene-node node-b danger">
                <Radio size={19} />
                <span>203.0.113.42</span>
                <i />
            </div>
            <div className="scene-node node-c">
                <Fingerprint size={19} />
                <span>AC · 6318</span>
                <i />
            </div>
            <div className="scene-node node-d danger">
                <Fingerprint size={19} />
                <span>AC · 3024</span>
                <i />
            </div>
            <div className="scene-report">
                <div className="report-icon">
                    <ShieldCheck size={20} />
                </div>
                <div>
                    <b>Một kết nối. Cả mạng lưới.</b>
                    <span>Phát hiện tín hiệu liên quan trong kịch bản demo</span>
                </div>
                <span className="report-score">
                    95<small>RISK SCORE</small>
                </span>
            </div>
            <div className="scene-footer">
                <span>NETWORK ANALYSIS</span>
                <span>
                    <i className="live-dot" /> SIGNAL DETECTED
                </span>
            </div>
        </div>
    )
}
export function FlowGraph({
    graph,
    selected,
    onSelect,
}: {
    graph: TransactionGraph
    selected?: string
    onSelect?: (id: string) => void
}) {
    const uid = useId().replaceAll(':', '')
    const positions = new Map(
        graph.nodes.map((n, i) => {
            if (graph.nodes.length === 1) return [n.id, { x: 350, y: 180 }]
            const angle = -Math.PI / 2 + i * ((2 * Math.PI) / graph.nodes.length)
            return [n.id, { x: 350 + Math.cos(angle) * 230, y: 190 + Math.sin(angle) * 125 }]
        }),
    )
    return (
        <svg className="flow-graph" viewBox="0 0 700 400" role="img" aria-label="Đồ thị dòng tiền giữa các tài khoản">
            <defs>
                <marker
                    id={uid + 'arrow'}
                    viewBox="0 0 10 10"
                    refX="32"
                    refY="5"
                    markerWidth="6"
                    markerHeight="6"
                    orient="auto"
                >
                    <path d="m0 0 10 5-10 5z" fill="#6389d5" />
                </marker>
                <pattern id={uid + 'dots'} width="22" height="22" patternUnits="userSpaceOnUse">
                    <circle cx="1" cy="1" r=".8" fill="#ccd4e3" />
                </pattern>
            </defs>
            <rect width="700" height="400" fill={`url(#${uid}dots)`} />
            {graph.edges.map((edge, i) => {
                const from = positions.get(edge.fromAccountRef),
                    to = positions.get(edge.toAccountRef)
                if (!from || !to) return null
                return (
                    <g key={edge.id}>
                        <path
                            className={'flow-edge ' + (edge.root ? 'root-edge' : '')}
                            d={`M ${from.x} ${from.y} L ${to.x} ${to.y}`}
                            markerEnd={`url(#${uid}arrow)`}
                        />
                        <circle r="3" fill={edge.root ? '#eb754e' : '#3777e9'} className="flow-particle">
                            <animateMotion
                                dur={`${2.4 + i * 0.4}s`}
                                repeatCount="indefinite"
                                path={`M ${from.x} ${from.y} L ${to.x} ${to.y}`}
                            />
                        </circle>
                        <rect
                            x={(from.x + to.x) / 2 - 36}
                            y={(from.y + to.y) / 2 - 10}
                            width="72"
                            height="20"
                            rx="5"
                            fill="#fff"
                            stroke="#e3e8f0"
                        />
                        <text
                            x={(from.x + to.x) / 2}
                            y={(from.y + to.y) / 2 + 4}
                            textAnchor="middle"
                            fontSize="9"
                            fill="#65718a"
                        >
                            {shortMoney(edge.amount)}
                        </text>
                    </g>
                )
            })}
            {graph.nodes.map((node, i) => {
                const pos = positions.get(node.id)!
                return (
                    <g
                        className={'graph-account ' + (selected === node.id ? 'is-selected' : '')}
                        key={node.id}
                        tabIndex={onSelect ? 0 : undefined}
                        role={onSelect ? 'button' : undefined}
                        aria-label={onSelect ? 'Xem tài khoản ' + node.masked : undefined}
                        onClick={() => onSelect?.(node.id)}
                        onKeyDown={(e) => e.key === 'Enter' && onSelect?.(node.id)}
                    >
                        <circle
                            cx={pos.x}
                            cy={pos.y}
                            r="28"
                            fill={i === 1 ? '#132d53' : '#f1f5fd'}
                            stroke={i === 1 ? '#132d53' : '#aabfdf'}
                            strokeWidth="1.5"
                        />
                        <path
                            d={`M${pos.x - 8} ${pos.y - 7}h16v14h-16z M${pos.x - 5} ${pos.y - 3}h10 M${pos.x - 5} ${pos.y + 2}h10`}
                            stroke={i === 1 ? '#d8e8ff' : '#426ba8'}
                            fill="none"
                        />
                        <text x={pos.x} y={pos.y + 47} textAnchor="middle" fontSize="11" fill="#344664">
                            {node.masked}
                        </text>
                        <text x={pos.x} y={pos.y + 62} textAnchor="middle" fontSize="8" fill="#8893a5">
                            {node.customerName || 'Tài khoản'}
                        </text>
                    </g>
                )
            })}
        </svg>
    )
}
