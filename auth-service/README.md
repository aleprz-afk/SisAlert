# auth-service — API de identidad de SisAlert

Microservicio de autenticación y autorización del proyecto **SisAlert**. Está basado en
[mini-identity-api-dotnet](https://github.com/wolfcor10/mini-identity-api-dotnet) y fue
modificado e integrado al repositorio con autorización del docente.

## Qué se mantuvo

- Arquitectura por capas: `Domain`, `Application`, `Infrastructure`, `Api` y `Tests`.
- Endpoints originales de registro, login, usuarios, roles y demo.
- Hashing de contraseñas con sal, JWT con emisor/audiencia y autorización por roles.
- Pruebas unitarias con xUnit.

## Qué se agregó o cambió

| Cambio | Detalle |
|---|---|
| Persistencia real | EF Core + PostgreSQL en lugar de `InMemoryUserRepository` / `InMemoryRoleRepository`. |
| Tablas propias | `usuarios`, `roles`, `permisos`, `usuarios_roles`, `roles_permisos` en la base `sisalert`. |
| Migraciones | EF Core migrations en `src/MiniIdentityApi.Infrastructure/Persistence/Migrations`. |
| CRUD de usuarios | `PUT /api/users/{id}` y `DELETE /api/users/{id}`; el resto de operaciones ahora persiste. |
| Respuestas seguras | `GET /api/users` ya no expone el hash ni la sal de la contraseña. |
| Errores consistentes | Middleware que traduce excepciones a `{ error, mensaje }` con 400/401/404/409/500. |
| Protección del administrador | No se puede eliminar el último usuario con rol `Admin`. |
| Repositorios en memoria | Se conservan para las pruebas unitarias. |

## Requisitos

- .NET SDK 10
- PostgreSQL 16 (el mismo contenedor del resto del proyecto)

## Ejecutar

Desde la raíz del repositorio, con PostgreSQL ya corriendo:

```bash
cd auth-service/src/MiniIdentityApi.Api
dotnet run --launch-profile http
```

Queda en `http://localhost:5132` y Swagger en `http://localhost:5132/swagger`.

La conexión se configura en `appsettings.json` (`ConnectionStrings:Identity`) o con la
variable de entorno:

```bash
ConnectionStrings__Identity="Host=localhost;Port=5432;Database=sisalert;Username=sisalert;Password=sisalert"
```

Al arrancar aplica las migraciones y crea el administrador inicial si no existe:

| Usuario | Contraseña |
|---|---|
| `admin` | `Admin123*` |

## Pruebas

```bash
cd auth-service
dotnet test MiniIdentityApi.slnx
```

Las pruebas usan los repositorios en memoria, así que no necesitan PostgreSQL.

## Migraciones

La herramienta `dotnet-ef` está declarada en `dotnet-tools.json`, por lo que no hace falta
instalarla globalmente.

```bash
cd auth-service
dotnet tool restore
dotnet tool run dotnet-ef migrations add NombreDeLaMigracion \
  --project src/MiniIdentityApi.Infrastructure \
  --startup-project src/MiniIdentityApi.Api \
  --output-dir Persistence/Migrations
```

La aplicación aplica las migraciones pendientes sola con `Database.Migrate()` al iniciar.
