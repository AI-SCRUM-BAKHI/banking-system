import { z } from 'zod'

const envSchema = z.object({
    VITE_API_BASE_URL: z.union([z.literal('/api/v1'), z.string().url()]).default('/api/v1'),
    VITE_APP_NAME: z.string().min(1).default('Banking Fraud Detection'),
})

export const env = envSchema.parse({
    VITE_API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
    VITE_APP_NAME: import.meta.env.VITE_APP_NAME,
})
