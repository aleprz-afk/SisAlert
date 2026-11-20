# syntax=docker/dockerfile:1
# Frontend (React + Vite) servido por nginx

# ---- Compilación ----
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- Ejecución: nginx sin privilegios de root ----
FROM nginxinc/nginx-unprivileged:1.27-alpine
# La imagen genera la configuración a partir de la plantilla al arrancar (envsubst con
# PORT, AUTH_API_URL y ALERTAS_API_URL).
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist /usr/share/nginx/html

ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/health" || exit 1
