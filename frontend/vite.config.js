import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    // A demonstração importa a regra do Nexus direto do backend (uma fonte só).
    fs: { allow: ['..'] },
    proxy: {
      '/api': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
      '/public': 'http://localhost:8080',
      '/webhooks': 'http://localhost:8080'
    }
  },
  // A demonstração vira um arquivo HTML único (scripts/gerar-demo.mjs), então sai num pacote só.
  build: mode === 'demo'
    ? { outDir: 'dist-demo', cssCodeSplit: false, rollupOptions: { output: { inlineDynamicImports: true } } }
    : {}
}))
