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
├── api/          apiClient.js, authApi.js, alertasApi.js, alertasMock.js
├── context/      AuthContext.jsx
├── components/   RutaProtegida.jsx, Layout.jsx, ChipModoDemo.jsx
└── pages/        LoginPage, RegistroPage, EstacionesPage, AlertasPage
```

## Tablero y alertas (integración con alertas-service)

`src/api/alertasApi.js` es el único punto de contacto con el Servicio de Alertas. Las tres
llamadas siguen el contrato y pasan por `apiFetch`, así que el token viaja solo:

| Función | Endpoint |
| --- | --- |
| `getEstadoZona(zonaId)` | `GET /alertas-api/api/zonas/{zonaId}/estado` — se consulta cada 5 s |
| `getAlertas(zonaId)` | `GET /alertas-api/api/alertas?zonaId=...` — se consulta cada 5 s |
| `reconocerAlerta(alertaId)` | `POST /alertas-api/api/alertas/{alertaId}/reconocer` — sin cuerpo |

- `EstacionesPage` (menú **Tablero**) pinta semáforo, nivel, tendencia y umbrales.
- `AlertasPage` lista las alertas de más reciente a más antigua y registra el reconocimiento.

### Modo demostración
Mientras `alertas-service` no esté corriendo, las llamadas caen en `alertasMock.js`
(creciente simulada que pasa de verde a rojo en poco más de un minuto) y aparece un chip
avisando en pantalla. En cuanto el servicio real responde, se usan sus datos: se puede
pulsar **Reintentar** en el chip o recargar la página. El comportamiento se controla con
`VITE_MOCK_ALERTAS`:

```bash
# .env.local
VITE_MOCK_ALERTAS=false   # falla a la vista si el servicio no responde (para verificar integración real)
VITE_MOCK_ALERTAS=true    # siempre datos de demostración
# sin la variable: automático (por defecto)
```

> CORS: en desarrollo el proxy de Vite evita CORS por completo. Si el frontend se sirve
> desde otro origen (por ejemplo `npm run preview`), el servicio debe permitirlo.