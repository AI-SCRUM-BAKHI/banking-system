import type { Plugin } from 'vite'
export function evaluationGateway(target: URL, serviceToken: string): Plugin {
    return {
        name: 'authenticated-evaluation-gateway',
        configureServer(server) {
            server.middlewares.use(async (request, response, next) => {
                if (request.url?.split('?')[0] !== '/api/v1/transactions/evaluate') return next()
                const fail = (status: number, message: string) => {
                    response.statusCode = status
                    response.setHeader('Content-Type', 'application/json')
                    response.end(JSON.stringify({ success: false, error: { code: 'EVALUATION_GATEWAY', message } }))
                }
                if (request.method !== 'POST') return fail(405, 'Use POST for transaction evaluation.')
                if (!serviceToken)
                    return fail(503, 'Configure APP_SERVICE_TOKEN in backend/.env for the evaluation gateway.')
                if (!request.headers.authorization) return fail(401, 'Authentication required')
                try {
                    const auth = await fetch(target.origin + target.pathname.replace(/\/$/, '') + '/auth/me', {
                        headers: { Authorization: request.headers.authorization },
                        signal: AbortSignal.timeout(10000),
                    })
                    if (!auth.ok)
                        return fail(
                            auth.status === 401 || auth.status === 403 ? auth.status : 502,
                            'Authentication failed at the banking backend.',
                        )
                    next()
                } catch {
                    return fail(502, 'Cannot reach the banking backend.')
                }
            })
        },
    }
}
