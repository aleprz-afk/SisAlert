# Sistema de Alertas Tempranas de Crecientes — Villavicencio

* **Asignatura:** Teleco 1 · Proyecto final · 2026-II
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
| API de autenticación | ASP.NET Core (.NET 10) + EF Core/PostgreSQL | 5132 | `auth-service/` (basada en [mini-identity-api-dotnet](https://github.com/wolfcor10/mini-identity-api-dotnet), con persistencia y CRUD agregados) |
| Microservicio de alertas | Java + Spring Boot, PostgreSQL | 8081 | `alertas-service/` |
 
Toda la comunicación es HTTP/REST con JSON. El navegador solo habla con el frontend (puerto 5173) y el proxy de Vite reenvía las llamadas, así que no hay problemas de CORS en desarrollo:
 
| El navegador llama a | Se reenvía a |
|---|---|
| `/auth-api/...` | `http://localhost:5132/...` |
| `/alertas-api/...` | `http://localhost:8081/...` |
 
Más detalle en `docs/infraestructura.md`, `docs/aplicacion.md` y `docs/modelo-datos.md`.
 
## Estructura del repositorio
 
```
SisAlert/
├── frontend/          React + Vite (login, tablero, alertas, zonas)
├── alertas-service/   Spring Boot (zonas, lecturas, alertas, umbrales, CRUD de zonas)
├── auth-service/      ASP.NET Core (.NET 10) con EF Core (usuarios, roles, permisos)
├── docs/              Diagramas de infraestructura, aplicación y datos
└── README.md
```

Ambos servicios comparten la misma instancia de PostgreSQL (`sisalert`) pero cada uno tiene **su propio esquema**: `alertas` (`zona`, `lectura`, `alerta`, versionado con Flyway) e `identidad` (`usuarios`, `roles`, `permisos` y sus tablas puente, versionado con EF Core). Así se pueden arrancar en cualquier orden y en la nube cada uno puede tener un usuario con permisos solo sobre su esquema.
 
## Requisitos
 
| Herramienta | Versión | Para qué |
|---|---|---|
| Git | reciente | Clonar los repositorios |
| .NET SDK | 10 | API de autenticación |
| JDK | 17 o superior | Microservicio de alertas (Maven va incluido con `mvnw`) |
| Node.js | 20 o superior | Frontend |
| Docker | reciente (opcional) | Contenedor de PostgreSQL (y despliegue con compose) |
 
Comprobar versiones:
 
```bash
git --version
dotnet --version
java -version
node -v
```
 
## Cómo levantar todo
 
Se necesitan **tres terminales**, una por componente, más el contenedor de PostgreSQL. Conviene arrancarlos en este orden.

En local **no hay que configurar nada**: cada servicio arranca en modo desarrollo con valores listos para el PostgreSQL de abajo. Las credenciales de producción nunca están en el repositorio (ver [Configuración y variables de entorno](#configuración-y-variables-de-entorno)).

### 0. Base de datos PostgreSQL

Los dos servicios la necesitan. La forma más rápida de tenerla es un contenedor:

```bash
docker run -d --name sisalert-db --restart unless-stopped   -e POSTGRES_DB=sisalert -e POSTGRES_USER=sisalert -e POSTGRES_PASSWORD=sisalert   -p 5432:5432 -v sisalert-pgdata:/var/lib/postgresql/data postgres:16-alpine
```

Si el contenedor ya existe pero está detenido: `docker start sisalert-db`. Los datos viven en el volumen `sisalert-pgdata` y sobreviven a reinicios del contenedor y de los servicios. Cada servicio crea sus tablas al arrancar: alertas con Flyway (esquema `alertas`) e identidad con EF Core (esquema `identidad`).
 
### 1. API de autenticación (terminal 1)

El proyecto ya está integrado en `auth-service/` (no hay que clonar nada aparte). Usa la misma base PostgreSQL que el microservicio de alertas y guarda usuarios, roles y permisos en tablas propias mediante EF Core.

```bash
cd SisAlert/auth-service/src/MiniIdentityApi.Api
dotnet run --launch-profile http
```

Queda escuchando en `http://localhost:5132`. Swagger está en `http://localhost:5132/swagger`.

El perfil `http` arranca en modo `Development`, que toma la cadena de conexión, la clave JWT de desarrollo y la contraseña inicial de admin de `appsettings.Development.json`. Al arrancar aplica sus migraciones de EF Core y crea el usuario inicial si no existe:

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `Admin123*` | Admin |

`Admin123*` es solo la contraseña de desarrollo. En producción la define la variable `Seed__AdminPassword`. Si `admin` ya existe en la base, no se vuelve a crear ni se cambia su contraseña: si alguien la cambió o desactivó el usuario, se puede borrar y reiniciar la API para que lo cree de nuevo (ver [Problemas frecuentes](#problemas-frecuentes)).

Los usuarios registrados desde la pantalla de registro **ya no se pierden** al reiniciar: quedan en PostgreSQL. Las contraseñas se almacenan con hash y sal, nunca en texto plano.
 
### 2. Microservicio de alertas (terminal 2)

```bash
cd SisAlert/alertas-service
./mvnw spring-boot:run          # Linux / macOS
mvnw.cmd spring-boot:run        # Windows
```

Sin variables de entorno arranca con el perfil `dev` (`application-dev.properties`), que apunta al PostgreSQL del paso 0 y sincroniza automáticamente cada minuto.

Queda escuchando en `http://localhost:8081`. La primera vez siembra las tres zonas de riesgo; en arranques posteriores conserva zonas, lecturas y alertas. Las zonas empiezan sin lecturas hasta que se sincroniza.

Las pruebas unitarias se corren con `./mvnw test` (en Windows, `mvnw.cmd test`) y usan H2 en memoria, así que no necesitan PostgreSQL.

La fuente de datos se elige en `application.properties` con `alertas.fuente`:

| Valor | Fuente |
|---|---|
| `open-meteo` (por defecto) | Lluvia real en vivo para Villavicencio desde Open-Meteo; necesita internet |
| `ideam` | Datos abiertos del IDEAM en datos.gov.co; el token opcional va en la variable de entorno `ALERTAS_IDEAM_APP_TOKEN` |
| `simulada` | Lluvia aleatoria entre 0 y 90 mm; respaldo para demostrar sin internet |
| `escenario` | Resultados fijos para demostrar con lluvia en época seca; se elige con `alertas.escenario` |

No hace falta editar el archivo: Spring también lee estas propiedades desde variables de entorno (`ALERTAS_FUENTE`, `ALERTAS_ESCENARIO`). Por ejemplo, para la demostración con lluvia:

```powershell
$env:ALERTAS_FUENTE = "escenario"; $env:ALERTAS_ESCENARIO = "tormenta"; .\mvnw.cmd spring-boot:run
```

| `ALERTAS_ESCENARIO` | Resultado al sincronizar |
|---|---|
| `seca` | Todas las zonas en NORMAL |
| `tormenta` (por defecto) | Guatiquía Norte en PRECAUCIÓN, Guayuriba Sur en PELIGRO, Caño Grande Centro en NORMAL |
| `creciente` | Todas las zonas en PELIGRO |

Para volver a los datos reales basta con cerrar esa terminal y arrancar el servicio normal.

Los umbrales de alerta están en el mismo archivo (`alertas.umbral.precaucion-mm` y `alertas.umbral.peligro-mm`) y se cambian sin recompilar.

El microservicio **valida el JWT** de la API de identidad en cada petición: firma, emisor, audiencia y vencimiento. Usa la misma clave `Jwt__Key`. Ver [Seguridad](#seguridad).
 
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
4. **Zonas** (`/zonas`): crear, editar y eliminar zonas de riesgo. Al eliminar una zona se borran en cascada sus lecturas y alertas.

## Configuración y variables de entorno

Cada servicio tiene dos modos:

| | Desarrollo (local) | Producción (Azure) |
|---|---|---|
| alertas-service | Perfil `dev`, activo por defecto (`application-dev.properties`) | `SPRING_PROFILES_ACTIVE=prod` (`application-prod.properties`) |
| API de identidad | `ASPNETCORE_ENVIRONMENT=Development` (`appsettings.Development.json`) | `ASPNETCORE_ENVIRONMENT=Production` |
| Credenciales | Valores locales de prueba incluidos | **Solo variables de entorno**; en Azure, secretos de Key Vault |
| Si falta una variable | — | El servicio **no arranca** y el error dice cuál falta |

La lista completa de variables de producción, con ejemplos, está en [`.env.example`](.env.example). Las principales:

| Variable | Servicio | Para qué |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | alertas | Conexión a PostgreSQL (en Azure con `?sslmode=require`) |
| `ConnectionStrings__Identity` | identidad | Conexión a PostgreSQL (en Azure con `SSL Mode=Require`) |
| `Jwt__Key` | identidad y alertas | Clave de firma de los tokens, la **misma** en ambos; mínimo 32 caracteres |
| `Seed__AdminPassword` | identidad | Contraseña inicial de `admin` |
| `ALERTAS_CORS_ORIGENES` | alertas | Dominio(s) del frontend desplegado |
| `ALERTAS_FUENTE`, `ALERTAS_ESCENARIO` | alertas | Fuente de datos y escenario de demostración |

`.env` y `.env.*` están en `.gitignore`; solo se sube la plantilla `.env.example`.

En producción, además:
- Swagger no se publica.
- Los errores no devuelven detalles internos.
- El monitoreo automático corre cada 15 minutos en vez de cada minuto.

## Base de datos en la nube

Los servicios no dependen de una base local: basta con apuntar las variables de conexión a cualquier PostgreSQL. Para Azure Database for PostgreSQL (o cualquier base remota):

1. **Crear un usuario por servicio**, una sola vez, con el administrador del servidor:

   ```bash
   psql "host=<servidor>.postgres.database.azure.com dbname=sisalert user=<admin> sslmode=require"         -v alertas_pass='<contraseña-alertas>' -v identidad_pass='<contraseña-identidad>'         -f infra/db/usuarios-por-servicio.sql
   ```

   Crea `alertas_app` e `identidad_app`. Cada uno solo puede usar su propio esquema: `alertas_app` recibe `permission denied` si intenta leer `identidad.usuarios`. Nadie puede crear objetos en `public`.

2. **Configurar los servicios** con su usuario y SSL obligatorio (ver `.env.example`):
   - alertas: `DB_URL=jdbc:postgresql://<servidor>:5432/sisalert?sslmode=require`, `DB_USER=alertas_app`.
   - identidad: `ConnectionStrings__Identity=...;Username=identidad_app;SSL Mode=Require` y `Persistencia__EsquemaHistorial=identidad`.

3. **Arrancar.** Cada servicio crea o actualiza sus tablas con sus migraciones; no hace falta correr nada a mano.

Protecciones:
- En perfil `prod`, alertas-service **no arranca** si `DB_URL` no pide SSL. Solo se permite sin SSL con `DB_SSL_OPCIONAL=true`, para una base en red privada.
- Las conexiones se renuevan antes de que Azure corte las inactivas.
- La API de identidad reintenta ante cortes transitorios.

**Retención de datos.** Todos los días a las 3:00 a. m. se borran las lecturas de más de 365 días y las alertas históricas de más de 730. La alerta vigente nunca se borra. Se ajusta con `ALERTAS_RETENCION_LECTURAS_DIAS` y `ALERTAS_RETENCION_ALERTAS_DIAS`; con `0` se desactiva.

Los respaldos y la restauración a un punto en el tiempo los hace el servicio gestionado de Azure (7 días por defecto, configurable hasta 35).

## Endpoints del microservicio de alertas

Todas exigen `Authorization: Bearer <token>`. Sin token, o con uno inválido o vencido, la respuesta es `401`. Si el usuario no tiene el rol requerido, es `403`.

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/zonas` | Con sesión | Lista de zonas de riesgo (incluye `codigoEstacion`) |
| GET | `/api/zonas/{id}` | Con sesión | Una zona por id |
| POST | `/api/zonas` | Admin | Crea una zona (`nombre`, `cuerpoAgua`, `comunidades`, `codigoEstacion`) |
| PUT | `/api/zonas/{id}` | Admin | Actualiza una zona |
| DELETE | `/api/zonas/{id}` | Admin | Elimina la zona y, en cascada, sus lecturas y alertas |
| GET | `/api/alertas` | Con sesión | Una entrada por zona con su alerta vigente (`nivel` en `null` si aún no hay lecturas) |
| GET | `/api/lecturas/zonas/{id}/historico` | Con sesión | Últimas 20 lecturas de una zona |
| POST | `/api/lecturas/sincronizar` | Admin | Consulta la fuente externa, guarda las lecturas y recalcula las alertas; devuelve lo mismo que `GET /api/alertas` |

Los niveles son `NORMAL`, `PRECAUCION` y `PELIGRO`. Si la fuente externa falla, `sincronizar` responde `502` con `{"error":"FUENTE_EXTERNA_NO_DISPONIBLE","mensaje":"..."}` y no cambia ninguna alerta. Los errores de validación responden `400`, los recursos inexistentes `404` y los duplicados `409`, siempre con `{"error":"CODIGO","mensaje":"texto"}`. El detalle de cada respuesta está en `docs/aplicacion.md` §5.

## Endpoints de la API de identidad

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/auth/register` | Registra un usuario |
| POST | `/api/auth/login` | Valida credenciales y devuelve el JWT |
| GET | `/api/users` | Lista usuarios (solo Admin) |
| GET | `/api/users/{id}` | Consulta un usuario |
| PUT | `/api/users/{id}` | Edita nombre de usuario y correo (solo Admin) |
| DELETE | `/api/users/{id}` | Elimina un usuario; protege al último administrador (solo Admin) |
| PATCH | `/api/users/{id}/activate` \| `/deactivate` | Activa o desactiva un usuario (solo Admin) |
| POST | `/api/users/{id}/roles` | Asigna un rol (solo Admin) |
| GET | `/api/roles` | Lista roles (solo Admin) |
| POST | `/api/roles` | Crea un rol (solo Admin) |

Los endpoints protegidos exigen `Authorization: Bearer <token>` y rol `Admin` cuando se indica.

## Comprobación rápida desde la terminal

Con los dos servicios corriendo. En PowerShell:

```powershell
# 1. Iniciar sesión como admin y guardar el token
$token = (Invoke-RestMethod -Method Post http://localhost:5132/api/auth/login -ContentType "application/json" -Body '{"usernameOrEmail":"admin","password":"Admin123*"}').accessToken
$h = @{ Authorization = "Bearer $token" }

# 2. Zonas cargadas al arrancar
Invoke-RestMethod http://localhost:8081/api/zonas -Headers $h

# 3. Sincronizar: devuelve cada zona con su nivel, mensaje y lluvia en mm
Invoke-RestMethod -Method Post http://localhost:8081/api/lecturas/sincronizar -Headers $h

# 4. Sin token: 401
Invoke-RestMethod http://localhost:8081/api/alertas
```
 
## Seguridad

| Medida | Dónde |
|---|---|
| JWT validado en el microservicio (firma HS256, emisor, audiencia, vencimiento) | alertas-service (Spring Security) |
| Roles: consultar con cualquier sesión; sincronizar y crear, editar o borrar zonas solo `Admin` | alertas-service; el frontend además oculta esas opciones a quien no es Admin |
| Contraseñas con PBKDF2-SHA256 (600.000 iteraciones). Los hashes SHA-256 anteriores se actualizan solos en el siguiente login | API de identidad |
| Contraseña mínima de 8 caracteres al registrarse | API de identidad y pantalla de registro |
| Máximo 10 intentos de login o registro por minuto por IP (`429`). Se cambia con `RateLimit__IntentosPorMinuto` | API de identidad |
| El login no revela si un usuario existe o está desactivado cuando la contraseña es incorrecta | API de identidad |
| CORS solo para los orígenes configurados (`ALERTAS_CORS_ORIGENES`, `Cors__Origenes`) | Ambos servicios |
| Credenciales y claves solo por variables de entorno en producción | Ambos servicios (ver [Configuración](#configuración-y-variables-de-entorno)) |
| Dependencias sin vulnerabilidades conocidas (`dotnet list package --vulnerable`) | API de identidad |

Los usuarios que se registran desde la pantalla no tienen rol: pueden ver el tablero, las alertas y el histórico. Para darles el rol `Admin`, un administrador usa `POST /api/users/{id}/roles` desde Swagger.

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
| "Usuario o contraseña incorrectos" | Credenciales inválidas o usuario desactivado | Revisar el usuario en Swagger (`/api/users`) o usar `admin` |
| `admin` / `Admin123*` no entra | En esa base `admin` ya existía con otra contraseña, quedó desactivado o la API no está conectada a PostgreSQL | Revisar que la terminal 1 diga `Now listening`. Si sí, borrar `admin` y reiniciar la API para que lo cree de nuevo: `docker exec sisalert-db psql -U sisalert -d sisalert -c "DELETE FROM identidad.usuarios_roles WHERE usuario_id IN (SELECT id FROM identidad.usuarios WHERE username='admin'); DELETE FROM identidad.usuarios WHERE username='admin';"` |
| `Perfil prod: faltan variables de entorno obligatorias: ...` o `Falta la clave JWT / la cadena de conexión` | Se arrancó en modo producción sin definir las variables | Definirlas (ver `.env.example`) o arrancar en modo desarrollo |
| "No se pudo conectar con la base de datos" | PostgreSQL no está corriendo | `docker start sisalert-db` o revisar `DB_URL` / `ConnectionStrings__Identity` |
| Quedaron tablas `zona`, `lectura`, `alerta` y `flyway_schema_history` en el esquema `public` | Base creada antes de separar los esquemas; ya no se usan (las zonas se vuelven a sembrar en `alertas`) | Opcional: `docker exec sisalert-db psql -U sisalert -d sisalert -c "DROP TABLE public.alerta, public.lectura, public.zona, public.flyway_schema_history"` |
| El microservicio no arranca con `Flyway` o `EF Core` | Migraciones pendientes o base mal apuntada | Revisar el log; ambos aplican migraciones solos al arrancar |
| El tablero muestra el aviso de modo demostración | El microservicio de alertas no responde en 8081 | Revisar la terminal 2 y pulsar **Reintentar** |
| Todas las zonas aparecen en "Sin datos" | Todavía no se ha sincronizado | Pulsar **Sincronizar lecturas** en el tablero |
| "No fue posible consultar la fuente de datos meteorológicos" al sincronizar | La fuente externa no respondió (502) | Revisar la conexión; las alertas anteriores se conservan |
| `Port 8081 was already in use` | Otra aplicación usa el puerto | Cerrarla, o cambiar `server.port` en `application.properties` y el destino en `frontend/vite.config.js` |
| `./mvnw: Permission denied` | Falta permiso de ejecución | `chmod +x mvnw` |
 
## Forma de trabajo en Git
 
Cada integrante trabaja en su propia rama. Nadie sube directo a `main`: todo entra por pull request con revisión de otro integrante.
 
