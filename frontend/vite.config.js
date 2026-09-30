import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth-api': {
        target: 'http://localhost:5132', // el puerto del Paso 1
        changeOrigin: true,
        secure: false,
        rewrite: (p) => p.replace(/^\/auth-api/, ''),
      },
    },
  },
})