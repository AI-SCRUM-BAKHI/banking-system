import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { AnimatedNumber } from './index'
afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
})
it('updates filtered metrics when reduced motion is enabled', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }))
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
        callback(performance.now() + 1000)
        return 1
    })
    vi.stubGlobal('cancelAnimationFrame', () => {})
    const view = render(<AnimatedNumber value={24} />)
    expect(screen.getByText('24')).toBeInTheDocument()
    view.rerender(<AnimatedNumber value={25} />)
    expect(screen.getByText('25')).toBeInTheDocument()
})
