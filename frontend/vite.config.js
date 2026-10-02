import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth-api': {
        // API de autenticación del profesor (.NET)
        target: 'http://localhost:5132', 
        changeOrigin: true,
        secure: false,
        rewrite: (p) => p.replace(/^\/auth-api/, ''),
      },
      // Microservicio de alertas (Spring Boot)
      '/alertas-api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/alertas-api/, ''),
      },
    },
  },
})