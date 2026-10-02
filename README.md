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
 
Queda escuchando en `http://localhost:8081`. Al arrancar carga la zona de ejemplo `cano-buque-bajo` y empieza a simular el nivel del caño. Los datos están en H2 en memoria y se reinician con el servicio.
 
El servicio valida el token que emite la API de autenticación con la misma clave, emisor y audiencia. Los valores por defecto ya coinciden con los de la API del docente; si se cambian allá, hay que cambiarlos aquí con variables de entorno:
 
| Variable | Valor por defecto |
|---|---|
| `JWT_KEY` | el mismo `Jwt:Key` del `appsettings.json` de la API |
| `JWT_ISSUER` | `MiniIdentityApi` |
| `JWT_AUDIENCE` | `MiniIdentityApiUsers` |
 
### 3. Frontend (terminal 3)
 
```bash
cd SisAlert/frontend
npm install        # solo la primera vez
npm run dev
```
 
Abrir **http://localhost:5173** e iniciar sesión con `admin` / `Admin123*`.
 
## Endpoints del microservicio de alertas
 
Todos exigen la cabecera `Authorization: Bearer <token>`. Sin token responden `401`.
 
| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/zonas/{zonaId}/estado` | Nivel actual, color del semáforo, tendencia y umbrales de la zona |
| GET | `/api/alertas?zonaId={zonaId}` | Alertas de la zona, de la más reciente a la más antigua |
| POST | `/api/alertas/{alertaId}/reconocer` | Marca la alerta como vista; el usuario se toma del token |
 
## Comprobación rápida desde la terminal
 
Con los dos servicios corriendo:
 
```bash
# 1. Pedir un token
curl -s -X POST http://localhost:5132/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"admin","password":"Admin123*"}'
 
# 2. Copiar el token de la respuesta y consultar la zona
TOKEN="pegar-aquí-el-token"
curl -s http://localhost:8081/api/zonas/cano-buque-bajo/estado \
  -H "Authorization: Bearer $TOKEN"
 
# 3. Sin token debe responder 401
curl -i http://localhost:8081/api/zonas/cano-buque-bajo/estado
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
| El servicio de alertas responde 401 con un token válido | La clave JWT no coincide con la de la API | Revisar `JWT_KEY`, `JWT_ISSUER` y `JWT_AUDIENCE` |
| `Port 8081 was already in use` | Otra aplicación usa el puerto | Cerrarla, o cambiar `server.port` en `application.properties` y el destino en `frontend/vite.config.js` |
| `./mvnw: Permission denied` | Falta permiso de ejecución | `chmod +x mvnw` |
 
## Forma de trabajo en Git
 
Cada integrante trabaja en su propia rama. Nadie sube directo a `main`: todo entra por pull request con revisión de otro integrante.
 
