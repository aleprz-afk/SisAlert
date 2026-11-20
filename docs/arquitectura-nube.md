# Entregable 1 · Arquitectura de infraestructura en la nube (Azure)

Arquitectura objetivo de SisAlert en Microsoft Azure. Para la ejecución local con cuatro terminales, ver `infraestructura.md`. El código ya está preparado para este despliegue; la creación de los recursos (Docker, CI/CD e IaC) es el paso siguiente.

## 1. Diagrama

```mermaid
flowchart TB
    U([Usuarios: comunidad,<br/>organismos de socorro, admin]) -->|HTTPS 443| ING

    subgraph AZ["Azure · grupo de recursos rg-sisalert"]
        subgraph CAE["Container Apps Environment (red virtual propia)"]
            ING{{Ingreso público<br/>TLS gestionado}} --> WEB["frontend<br/>nginx + React<br/>ÚNICO servicio público"]
            WEB -->|/auth-api · ingreso interno| AUTH["auth-service<br/>.NET 10"]
            WEB -->|/alertas-api · ingreso interno| ALE["alertas-service<br/>Spring Boot · 1 réplica<br/>(monitoreo programado)"]
            ALE -->|/predict · /boletin<br/>interno + X-Api-Key| IA["ai-service<br/>FastAPI + scikit-learn"]
        end

        PG[("Azure Database for PostgreSQL<br/>Flexible Server · SSL obligatorio<br/>esquemas alertas / identidad")]
        KV[["Key Vault<br/>secretos"]]
        ACR[["Container Registry<br/>imágenes versionadas"]]
        LOG[["Log Analytics<br/>logs y métricas"]]
        ID(["Identidad administrada"])
    end

    AUTH -->|TLS · identidad_app| PG
    ALE -->|TLS · alertas_app| PG
    ALE -->|HTTPS| OM[(Open-Meteo<br/>lluvia y pronóstico)]
    IA -->|HTTPS| GEM[(Google Gemini<br/>boletines)]

    ID -.lee secretos.-> KV
    CAE -.imágenes.-> ACR
    CAE -.stdout/stderr.-> LOG
    GH([GitHub Actions<br/>tests · build · push · deploy]) -.-> ACR
```

## 2. Componentes

| Recurso de Azure | Qué aloja | Configuración clave |
|---|---|---|
| **Container Apps Environment** | Los 4 contenedores, en una red virtual propia | Plan de consumo: se paga por uso |
| `frontend` (Container App) | nginx con la app React compilada; reenvía `/auth-api` y `/alertas-api` (`frontend/nginx/default.conf.template`) | **Ingreso externo**, HTTPS obligatorio. 1 a 3 réplicas por carga HTTP |
| `auth-service` | API de identidad (.NET 10) | **Ingreso interno**. 1 a 3 réplicas |
| `alertas-service` | Microservicio de alertas (Spring Boot) | **Ingreso interno**. **1 réplica fija**: el monitoreo automático corre dentro del proceso y con 2 réplicas sincronizaría dos veces |
| `ai-service` | Modelo predictivo y boletines (FastAPI) | **Ingreso interno** y `X-Api-Key`. 1 a 2 réplicas |
| **Azure Database for PostgreSQL Flexible Server** | Base `sisalert`, esquemas `alertas` e `identidad` | Plan Burstable B1ms, SSL obligatorio, copias automáticas (7 días), firewall solo para servicios de Azure |
| **Key Vault** | Clave JWT, contraseñas de la base, contraseña inicial de admin, `AI_API_KEY`, `GEMINI_API_KEY` | Fuente de verdad de los secretos. El script de despliegue los lee de ahí y se los entrega a cada Container App como **secretos cifrados de Container Apps** (el entorno *Express* que crean algunas suscripciones de estudiante no admite referencias directas a Key Vault). Nunca pasan por el repositorio |
| **Container Registry** | Imágenes de los 4 servicios, etiquetadas por commit | Plan Basic |
| **Log Analytics** | Logs de todos los contenedores | Las apps escriben en la consola y Azure los recoge |

## 3. Red y seguridad

- **Punto de entrada de los usuarios:** el ingreso del `frontend`, con TLS gestionado por Azure. El navegador solo habla con él.
- **Identidad, alertas e IA: internos o públicos según el tipo de entorno.**
  - **Diseño objetivo (entorno estándar):** ingreso **interno**; el frontend los llama por su nombre corto (`http://ca-auth`) y no se pueden alcanzar desde Internet.
  - **Despliegue actual (entorno *Express*):** las suscripciones de estudiante crean el entorno en modo *Express* (`environmentMode = Express`, fijo al crearlo). Ese modo no admite `allowInsecure` ni deja llamar a una app interna por su dirección completa, así que se usa el **plan B** (`desplegar.ps1 -ServiciosPublicos`): ingreso público solo por **HTTPS**.
  - **Con el plan B, cada servicio se protege solo**, comprobado en el despliegue:
    - alertas exige JWT con roles (`401` sin token);
    - la IA exige `X-Api-Key` (`401` sin clave);
    - identidad solo expone login y registro, con límite de intentos, y Swagger está apagado (`404`).
  - **Para volver al diseño objetivo** basta con crear el entorno en modo estándar y desplegar sin `-ServiciosPublicos`.
- **El navegador solo habla con un origen.** nginx reenvía `/auth-api` y `/alertas-api` a los servicios internos, así que no hay CORS en producción.
- **Base de datos:**
  - SSL obligatorio. alertas-service se niega a arrancar si `DB_URL` no lo pide.
  - Un usuario por servicio, con permisos solo sobre su esquema (`infra/db/usuarios-por-servicio.sql`).
- **Secretos:** se guardan en Key Vault y llegan a los contenedores como secretos cifrados de Container Apps; nunca pasan por el repositorio. En producción, si falta uno, el servicio no arranca y dice cuál falta.
- **Encabezados de seguridad del frontend:** CSP restrictiva, `X-Frame-Options: DENY`, HSTS y `nosniff`. nginx no revela su versión.
- **Aplicación:**
  - JWT validado en alertas-service, con roles.
  - PBKDF2 para las contraseñas.
  - Máximo 10 intentos de login por minuto por IP. La IP real llega en `X-Forwarded-For` y la API confía en los últimos `Proxy__SaltosConfiables = 2` saltos.

## 4. Sondas de salud

| Servicio | Vida (liveness) | Disponibilidad (readiness, verifica la base) |
|---|---|---|
| frontend | `GET /health` | — |
| auth-service | `GET /health` | `GET /health/ready` |
| alertas-service | `GET /actuator/health/liveness` | `GET /actuator/health/readiness` |
| ai-service | `GET /health` | — |

Si se cae la base, la sonda de disponibilidad falla: Azure deja de enviarle tráfico a esa réplica, sin reiniciarla, y la vuelve a usar cuando la base regresa. Esto se probó en local deteniendo PostgreSQL. Se recomienda un tiempo de espera de 5 s en la sonda de disponibilidad.

## 5. Variables por servicio

Todas salen de `.env.example`. Los valores sensibles salen de Key Vault y se configuran como secretos cifrados de cada Container App.

| Servicio | Variables |
|---|---|
| frontend | `PORT=8080`, `AUTH_API_URL`, `ALERTAS_API_URL` (URLs internas de Container Apps) |
| auth-service | `ASPNETCORE_ENVIRONMENT=Production`, `ConnectionStrings__Identity` 🔒, `Jwt__Key` 🔒, `Seed__AdminPassword` 🔒, `Persistencia__EsquemaHistorial=identidad`, `Proxy__SaltosConfiables=2` |
| alertas-service | `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USER`, `DB_PASSWORD` 🔒, `Jwt__Key` 🔒, `ALERTAS_CORS_ORIGENES`, `AI_SERVICE_URL`, `AI_API_KEY` 🔒 |
| ai-service | `AI_API_KEY` 🔒, `GEMINI_API_KEY` 🔒 (opcional) |

🔒 = secreto: guardado en Key Vault y entregado como secreto cifrado de Container Apps.

## 6. Escalado y disponibilidad

- **Servicios sin estado** (frontend, identidad e IA): escalan horizontalmente por número de peticiones simultáneas. El estado vive en PostgreSQL.
- **alertas-service:** una réplica fija, porque el monitoreo programado vive en el proceso. Para escalarlo habría que separar el monitoreo en un *Container Apps Job* programado (trabajo futuro).
- **Apagado ordenado:** Spring termina las peticiones en curso antes de apagarse (`server.shutdown=graceful`), así que los despliegues no cortan sincronizaciones.
- **Base de datos:** copias automáticas y restauración a un punto en el tiempo, que provee el servicio gestionado.

## 7. Automatización

| Pieza | Dónde | Estado |
|---|---|---|
| Imágenes Docker de los 4 servicios y `docker-compose.yml` | `*/Dockerfile`, `docker-compose.yml` | Listo |
| Integración continua: pruebas, análisis de dependencias e imágenes en cada PR | `.github/workflows/ci.yml` | Listo |
| **Infraestructura como código**: todo este diagrama en Bicep | `infra/azure/main.bicep` | Listo |
| Despliegue con un comando: infraestructura, imágenes, usuarios de la base y apps | `infra/azure/desplegar.ps1` (guía en `infra/azure/README.md`) | Listo |
| Pausar y encender para ahorrar crédito | `infra/azure/pausar.ps1` | Listo |
| Despliegue automático desde GitHub Actions en cada merge a `main` | — | Siguiente paso (necesita credenciales federadas de la suscripción) |

## 8. Costo estimado (Azure for Students, USD 100 de crédito)

| Recurso | Aproximado al mes |
|---|---|
| PostgreSQL Flexible B1ms + 32 GB | USD 13–18 |
| Container Apps (plan de consumo, pocas réplicas) | USD 0–10 |
| Container Registry Basic | USD 5 |
| Key Vault + Log Analytics | USD 0–5 |
| **Total** | **unos USD 20–35** |

Para no gastar el crédito: detener el servidor de PostgreSQL y escalar los Container Apps a 0 réplicas cuando no se use. Gemini con plan gratuito no suma costo.
