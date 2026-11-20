# Frontend — Alertas Villavicencio

Aplicación React + Vite. Consume la API de identidad (`auth-service`) y el microservicio `alertas-service`.

## Requisitos
- Node.js 20 o superior
- `auth-service` corriendo en `http://localhost:5132` (con PostgreSQL)
- `alertas-service` corriendo en `http://localhost:8081` (con PostgreSQL)

## Ejecutar en local
```bash
npm install
npm run dev
```
Abrir http://localhost:5173

## Proxy de desarrollo (vite.config.js)
| Prefijo en el frontend | Destino |
| --- | --- |
| `/auth-api/*` | auth-service (.NET) — `http://localhost:5132` |
| `/alertas-api/*` | alertas-service (Spring Boot) — `http://localhost:8081` |

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
├── context/      AuthContext.jsx, sesionContext.js, useAuth.js
├── components/   RutaProtegida.jsx, Layout.jsx, ChipModoDemo.jsx
└── pages/        LoginPage, RegistroPage, EstacionesPage, AlertasPage, ZonasPage
```

## Tablero y alertas (integración con alertas-service)

`src/api/alertasApi.js` es el único punto de contacto con el Servicio de Alertas. Las
llamadas siguen el contrato y pasan por `apiFetch`, así que el token viaja solo:

| Función | Endpoint |
| --- | --- |
| `getAlertas()` | `GET /alertas-api/api/alertas` — alerta vigente por zona, cada 5 s |
| `sincronizar()` | `POST /alertas-api/api/lecturas/sincronizar` — consulta fuente, guarda lecturas y recalcula |
| `getZonas()` | `GET /alertas-api/api/zonas` — lista para el CRUD |
| `crearZona(zona)` | `POST /alertas-api/api/zonas` |
| `actualizarZona(id, zona)` | `PUT /alertas-api/api/zonas/{id}` |
| `eliminarZona(id)` | `DELETE /alertas-api/api/zonas/{id}` (borra en cascada lecturas y alertas) |

- `EstacionesPage` (menú **Tablero**) pinta el semáforo por zona y el botón de sincronización.
- `AlertasPage` lista únicamente las zonas en precaución o peligro, de la más grave a la menor.
- `ZonasPage` (menú **Zonas**) permite crear, editar y eliminar zonas. Siempre usa el servicio real: el modo demostración no gestiona zonas.

### Modo demostración
Mientras `alertas-service` no esté corriendo, las llamadas caen en `alertasMock.js`
(creciente simulada que pasa de verde a rojo en poco más de un minuto) y aparece un chip
avisando en pantalla. Mientras siga en modo demostración se reintenta el servicio real cada
15 segundos y, en cuanto responde, se vuelve solo a datos reales; también se puede pulsar
**Reintentar**. El comportamiento se controla con `VITE_MOCK_ALERTAS`:

```bash
# .env.local
VITE_MOCK_ALERTAS=false   # falla a la vista si el servicio no responde (para verificar integración real)
VITE_MOCK_ALERTAS=true    # siempre datos de demostración
# sin la variable: automático (por defecto)
```

> CORS: en desarrollo el proxy de Vite evita CORS por completo. Si el frontend se sirve
> desde otro origen (por ejemplo `npm run preview`), el servicio debe permitirlo.