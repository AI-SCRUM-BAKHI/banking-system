import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
    const settings = loadEnv(mode, process.cwd(), 'VITE_')
    const configuredBase = settings.VITE_API_BASE_URL
    const target = new URL(
        settings.VITE_API_PROXY_TARGET ||
            (configuredBase && !configuredBase.startsWith('/') ? configuredBase : 'http://localhost:8080/api/v1'),
    )
    return {
        plugins: [react(), tailwindcss()],
        server: {
            watch: { ignored: ['**/coverage/**'] },
            proxy: {
                '/swagger-ui': { target: target.origin, changeOrigin: true },
                '/v3/api-docs': { target: target.origin, changeOrigin: true },
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
