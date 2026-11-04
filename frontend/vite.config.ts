import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { loadEnv } from 'vite'
import { resolve } from 'node:path'
import { evaluationGateway } from './evaluation-gateway.ts'

export default defineConfig(({ mode }) => {
    const settings = loadEnv(mode, process.cwd(), 'VITE_')
    const configuredBase = settings.VITE_API_BASE_URL
    const target = new URL(
        settings.VITE_API_PROXY_TARGET ||
            (configuredBase && !configuredBase.startsWith('/') ? configuredBase : 'http://localhost:8080/api/v1'),
    )
    const serviceToken =
        process.env.APP_SERVICE_TOKEN ||
        loadEnv(mode, resolve(process.cwd(), '../backend'), 'APP_').APP_SERVICE_TOKEN ||
        ''
    return {
        plugins: [evaluationGateway(target, serviceToken), react(), tailwindcss()],
        server: {
            watch: { ignored: ['**/coverage/**'] },
            proxy: {
                '/swagger-ui': { target: target.origin, changeOrigin: true },
                '/v3/api-docs': { target: target.origin, changeOrigin: true },
                '/api/v1': {
                    target: target.origin,
                    changeOrigin: true,
                    rewrite: (path) => target.pathname.replace(/\/$/, '') + path.slice('/api/v1'.length),
                    configure: (proxy) =>
                        proxy.on('proxyReq', (request, original) => {
                            request.removeHeader('origin')
                            if (original.url?.split('?')[0] === '/api/v1/transactions/evaluate') {
                                request.setHeader('X-Service-Token', serviceToken)
                                request.removeHeader('Authorization')
                            }
                        }),
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
