# Frontend — Alertas Villavicencio

Aplicación React + Vite. Consume la API de autenticación del docente y el microservicio `alertas-service`.

## Requisitos
- Node.js 20 o superior
- API de autenticación del docente corriendo (mini-identity-api-dotnet) en `http://localhost:5132`
- `alertas-service` corriendo en `http://localhost:8080`

## Ejecutar en local
```bash
npm install
npm run dev
```
Abrir http://localhost:5173

## Proxy de desarrollo (vite.config.js)
| Prefijo en el frontend | Destino |
| --- | --- |
| `/auth-api/*` | API de autenticación (.NET) — `http://localhost:5132` |
| `/alertas-api/*` | alertas-service (Spring Boot) — `http://localhost:8080` |

## Autenticación
1. `LoginPage` envía las credenciales a `POST /api/auth/login`.
2. El JWT recibido se guarda en `sessionStorage` (`AuthContext`).
3. `apiFetch` (`src/api/apiClient.js`) agrega `Authorization: Bearer <token>` a todas las peticiones.
4. Si un servicio responde 401, la sesión se cierra y se vuelve al login.
5. Las rutas dentro de `RutaProtegida` solo son accesibles con sesión iniciada.

## Estructura
```
src/
├── api/          apiClient.js, authApi.js, alertasApi.js
├── context/      AuthContext.jsx
├── components/   RutaProtegida.jsx, Layout.jsx
└── pages/        LoginPage, RegistroPage, EstacionesPage, AlertasPage
```