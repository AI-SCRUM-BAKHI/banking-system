import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
    const target = new URL(loadEnv(mode, process.cwd(), 'VITE_').VITE_API_BASE_URL || 'http://localhost:8080/api/v1')
    return {
        plugins: [react(), tailwindcss()],
        server: {
            watch: { ignored: ['**/coverage/**'] },
            proxy: {
                '/api/v1': {
                    target: target.origin,
                    changeOrigin: true,
                    rewrite: (path) => target.pathname.replace(/\/$/, '') + path.slice('/api/v1'.length),
                    configure: (proxy) => proxy.on('proxyReq', (request) => request.removeHeader('origin')),
                },
            },
        },
        test: {
            globals: true,
            environment: 'jsdom',
            setupFiles: './src/test/setup.ts',
        },
    }
})
