# Sistema de Alertas Tempranas de Crecientes — Villavicencio

* **Asignatura:** Teleco 1 · Parcial I · 2026-II
* **Institución:** Universidad de los Llanos (FCBI, Ingeniería de Sistemas)

## Integrantes
| # | Integrante | Rol principal |
|---|---|---|
| 1 | Alex Pérez Pedraza | Frontend: Pantalla de login e integración con API de autenticación |
| 2 | Brayan Espitia | Frontend: Vista de alertas y consumo de microservicio |
| 3 | Nicolás Villarraga | Microservicio de alertas: Endpoints, modelo y umbrales |
| 4 | Eduar Murcia | Consumo de datos externos (IDEAM), diagramas y README |


## Diagrama de arquitectura del Proyecto
![Arquitectura del MVP](docs/arquitectura.png)

| Componente | Tecnología | Puerto | Dónde está |
|---|---|---|---|
| Frontend | React + Vite | 5173 | `frontend/` |
| API de autenticación | ASP.NET Core (.NET 10) | 5132 | Repositorio del docente: [mini-identity-api-dotnet](https://github.com/wolfcor10/mini-identity-api-dotnet) (se usa sin modificar) |
| Microservicio de alertas | Java + Spring Boot, H2 en memoria | 8081 | `alertas-service/` |
 
Toda la comunicación es HTTP/REST con JSON. El navegador solo habla con el frontend (puerto 5173) y el proxy de Vite reenvía las llamadas, así que no hay problemas de CORS en desarrollo:
 
| El navegador llama a | Se reenvía a |
|---|---|
| `/auth-api/...` | `http://localhost:5132/...` |
| `/alertas-api/...` | `http://localhost:8081/...` |
 
Más detalle en `docs/infraestructura.md`, `docs/aplicacion.md` y `docs/modelo-datos.md`.
 
## Estructura del repositorio
 
```
SisAlert/
├── frontend/          React + Vite (login, tablero, alertas)
├── alertas-service/   Spring Boot (zonas, lecturas, alertas, umbrales)
├── docs/              Diagramas de infraestructura, aplicación y datos
└── README.md
```
 
## Requisitos
 
| Herramienta | Versión | Para qué |
|---|---|---|
| Git | reciente | Clonar los repositorios |
| .NET SDK | 10 | API de autenticación |
| JDK | 17 o superior | Microservicio de alertas (Maven va incluido con `mvnw`) |
| Node.js | 20 o superior | Frontend |
 
Comprobar versiones:
 
```bash
git --version
dotnet --version
java -version
node -v
```
 
## Cómo levantar todo
 
Se necesitan **tres terminales**, una por componente, y conviene arrancarlos en este orden.
 
### 1. API de autenticación (terminal 1)
 
```bash
git clone https://github.com/wolfcor10/mini-identity-api-dotnet.git
cd mini-identity-api-dotnet/src/MiniIdentityApi.Api
dotnet run --launch-profile http
```
 
Queda escuchando en `http://localhost:5132`. Swagger está en `http://localhost:5132/swagger`.
 
Trae un usuario creado al arrancar:
 
| Usuario | Contraseña |
|---|---|
| `admin` | `Admin123*` |
 
Los usuarios viven en memoria: si se reinicia la API, los que se hayan registrado desde la pantalla de registro se pierden y hay que crearlos de nuevo.
 
### 2. Microservicio de alertas (terminal 2)
 
```bash
cd SisAlert/alertas-service
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```
 
Queda escuchando en `http://localhost:8081`. Al arrancar carga las zonas de riesgo en H2 (en memoria, se reinician con el servicio); las zonas empiezan sin lecturas hasta que se sincroniza.

Las pruebas unitarias se corren con `./mvnw test` (en Windows, `mvnw.cmd test`). La consola de H2 está en `http://localhost:8081/h2-console` con JDBC URL `jdbc:h2:mem:alertasdb`, usuario `sa` y sin contraseña.

La fuente de datos se elige en `application.properties` con `alertas.fuente` (`simulada` genera lluvia aleatoria entre 0 y 90 mm). Los umbrales de alerta están en el mismo archivo (`alertas.umbral.precaucion-mm` y `alertas.umbral.peligro-mm`) y se cambian sin recompilar.

El microservicio lee el usuario del token solo para registrar en el log quién sincronizó; **no valida la firma** del token en esta etapa (ver `docs/infraestructura.md` §7).
 
### 3. Frontend (terminal 3)
 
```bash
cd SisAlert/frontend
npm install        # solo la primera vez
npm run dev
```
 
Abrir **http://localhost:5173** e iniciar sesión con `admin` / `Admin123*`.

Recorrido de la demostración:

1. **Tablero** (`/estaciones`): una tarjeta por zona. Al arrancar todas aparecen en gris como "Sin datos".
2. Botón **Sincronizar lecturas**: el microservicio consulta la fuente de datos, guarda las lecturas y recalcula el nivel de cada zona (verde, amarillo o rojo).
3. **Alertas** (`/alertas`): solo las zonas en precaución o peligro, la más grave primero.

## Endpoints del microservicio de alertas

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/zonas` | Lista de zonas de riesgo |
| GET | `/api/alertas` | Una entrada por zona con su alerta vigente (`nivel` en `null` si aún no hay lecturas) |
| POST | `/api/lecturas/sincronizar` | Consulta la fuente externa, guarda las lecturas y recalcula las alertas; devuelve lo mismo que `GET /api/alertas` |

Los niveles son `NORMAL`, `PRECAUCION` y `PELIGRO`. Si la fuente externa falla, `sincronizar` responde `502` con `{"error":"FUENTE_EXTERNA_NO_DISPONIBLE","mensaje":"..."}` y no cambia ninguna alerta. El detalle de cada respuesta está en `docs/aplicacion.md` §5.

## Comprobación rápida desde la terminal

Con el microservicio corriendo (en PowerShell usar `curl.exe` en vez de `curl`):

```bash
# 1. Zonas cargadas al arrancar
curl -s http://localhost:8081/api/zonas

# 2. Alertas antes de sincronizar: nivel en null
curl -s http://localhost:8081/api/alertas

# 3. Sincronizar: devuelve cada zona con su nivel, mensaje y lluvia en mm
curl -s -X POST http://localhost:8081/api/lecturas/sincronizar
```
 
## Modo demostración del frontend
 
Si el microservicio de alertas no responde, el frontend usa datos simulados (`frontend/src/api/alertasMock.js`) y muestra un aviso en pantalla. Se controla con un archivo `frontend/.env.local`:
 
```bash
VITE_MOCK_ALERTAS=false   # siempre el servicio real; si no responde, se ve el error
VITE_MOCK_ALERTAS=true    # siempre datos simulados
# sin la variable: automático
```
 
Para la sustentación se usa `VITE_MOCK_ALERTAS=false`, así queda claro que los datos vienen del servicio real. Después de cambiar el archivo hay que reiniciar `npm run dev`.
 
## Problemas frecuentes
 
| Síntoma | Causa probable | Solución |
|---|---|---|
| "No se pudo conectar con el servidor" al iniciar sesión | La API de autenticación no está corriendo o está en otro puerto | Revisar la terminal 1 y que diga `http://localhost:5132` |
| "Usuario o contraseña incorrectos" con un usuario recién creado | Se reinició la API y se borró de la memoria | Registrarlo otra vez o usar `admin` |
| El tablero muestra el aviso de modo demostración | El microservicio de alertas no responde en 8081 | Revisar la terminal 2 y pulsar **Reintentar** |
| Todas las zonas aparecen en "Sin datos" | Todavía no se ha sincronizado | Pulsar **Sincronizar lecturas** en el tablero |
| "No fue posible consultar la fuente de datos meteorológicos" al sincronizar | La fuente externa no respondió (502) | Revisar la conexión; las alertas anteriores se conservan |
| `Port 8081 was already in use` | Otra aplicación usa el puerto | Cerrarla, o cambiar `server.port` en `application.properties` y el destino en `frontend/vite.config.js` |
| `./mvnw: Permission denied` | Falta permiso de ejecución | `chmod +x mvnw` |
 
## Forma de trabajo en Git
 
Cada integrante trabaja en su propia rama. Nadie sube directo a `main`: todo entra por pull request con revisión de otro integrante.
 
