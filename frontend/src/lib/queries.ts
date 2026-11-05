import { useQuery } from '@tanstack/react-query'
import { demoDetail, demoGraph, demoList, demoSummary } from './demo'
import { getDetail, getFraudTransactions, getGraph, getSummary, getIpIntelligence } from '../services/fraud.service'
import type { FraudFilters, Mode } from '../types/fraud.types'
export function useCases(mode: Mode, filters: FraudFilters = {}, enabled = true) {
    return useQuery({
        queryKey: ['cases', mode, filters],
        queryFn: () => (mode === 'demo' ? Promise.resolve(demoList(filters)) : getFraudTransactions(filters)),
        enabled,
    })
}
export function useSummary(mode: Mode, period: Pick<FraudFilters, 'from' | 'to'> = {}) {
    return useQuery({
        queryKey: ['summary', mode, period],
        queryFn: () => (mode === 'demo' ? Promise.resolve(demoSummary(period)) : getSummary(period)),
    })
}
export function useDetail(mode: Mode, id: string) {
    return useQuery({
        queryKey: ['detail', mode, id],
        queryFn: () => (mode === 'demo' ? Promise.resolve(demoDetail(id)) : getDetail(id)),
        enabled: !!id,
    })
}
export function useGraph(mode: Mode, id: string) {
    return useQuery({
        queryKey: ['graph', mode, id],
        queryFn: () => (mode === 'demo' ? Promise.resolve(demoGraph(id)) : getGraph(id)),
        enabled: !!id,
    })
}

export function useIpIntelligence(enabled: boolean) {
    return useQuery({ queryKey: ['ip-intelligence', 'live'], queryFn: getIpIntelligence, enabled })
}
