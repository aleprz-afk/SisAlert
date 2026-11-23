# SisAlert — Manual Técnico y Arquitectura Módulo por Módulo

**Proyecto:** Sistema de Alertas Tempranas de Crecientes — Villavicencio  
**Asignatura:** Telemática 1 · Parcial I · Universidad de los Llanos  
**Fecha:** Noviembre 2026  
**Ubicación en el repositorio:** `docs/manual-tecnico-modulos.md`

---

## 1. Visión General del Sistema

SisAlert es un sistema distribuido orientado a microservicios diseñado para el monitoreo de riesgo por crecientes súbitas en los ríos y caños del municipio de Villavicencio. 

El sistema opera mediante **tres procesos independientes** desacoplados, comunicados estrictamente mediante HTTP/REST y JSON:

```mermaid
flowchart TD
    subgraph Cliente["Navegador Web"]
        UI["Interfaz de Usuario (React)"]
    end

    subgraph Host["localhost"]
        Vite["Proxy Inverso (Vite :5173)"]
        AuthApi["API de Autenticación (.NET 10 :5132)"]
        AlertasService["Microservicio de Alertas (Spring Boot :8081)"]
        PG[("Base de Datos (PostgreSQL :5432)")]
    end

    subgraph FuentesExternas["Servicios Meteorológicos Externos"]
        OpenMeteo["Open-Meteo API (Satélite / Radar WMO)"]
        IDEAM["Datos Abiertos IDEAM (datos.gov.co)"]
    end

    UI -->|HTTP :5173| Vite
    Vite -->|"/auth-api/... -> :5132"| AuthApi
    Vite -->|"/alertas-api/... -> :8081"| AlertasService
    AlertasService -->|JDBC TCP :5432| PG
    AlertasService -.->|HTTPS REST| OpenMeteo
    AlertasService -.->|HTTPS REST| IDEAM
```

### ¿Por qué esta arquitectura?
1. **Desacoplamiento de responsabilidades:** La identidad del usuario (seguridad) vive en una API (.NET), la lógica hidrológica de alertas vive en otro microservicio (Spring Boot), y la presentación vive en el cliente (React).
2. **Solución a CORS mediante Proxy Inverso:** El navegador solo se conecta al puerto `5173`. El servidor de Vite reenvía las peticiones a `:5132` o `:8081`, evitando restricciones de origen cruzado en el navegador.

---

## 2. Microservicio de Alertas (`alertas-service`)

Construido en **Java 17/21** con **Spring Boot 3.5**, **Spring Data JPA** y **PostgreSQL 16** (esquema `alertas`, migraciones Flyway). Su responsabilidad exclusiva es gestionar las zonas de riesgo, almacenar lecturas meteorológicas, evaluar los umbrales de alerta y exponer la API REST.

Organización interna por capas:

```
co.edu.unillanos.sisalert.alertas/
├── client/         # Integración con fuentes meteorológicas externas
├── config/         # Precarga de datos y configuración CORS
├── controller/     # Endpoints HTTP REST
├── dto/            # Objetos de transferencia de datos
├── exception/      # Manejo global de excepciones (HTTP 502)
├── model/          # Entidades JPA (Mapeo a tablas PostgreSQL)
├── repository/     # Interfaces de acceso a datos (Spring Data JPA)
├── rules/          # Reglas de negocio puras (Umbrales de semáforo)
├── security/       # Extracción e inspección del token JWT
└── service/        # Lógica de aplicación y orquestación transaccional
```

---

### 2.1 Capa de Modelo (`model/`)

Define las tablas de la base de datos relacional mediante anotaciones JPA (`@Entity`).

| Archivo | Qué hace | Por qué lo hace |
|---|---|---|
| `Zona.java` | Representa una cuenca o sector geográfico de Villavicencio (`id`, `nombre`, `cuerpoAgua`, `comunidades`, `codigoEstacion`). | Separa la ubicación física y comunidades de las mediciones temporales. El nombre es único para evitar duplicidades. |
| `Lectura.java` | Registro inmutable de precipitación (`id`, `zona`, `precipitacionMm`, `fechaHora`, `fuente`). | Almacena los hechos meteorológicos medidos. **Nunca se edita ni se borra**, lo que garantiza auditoría histórica de las lluvias. |
| `Alerta.java` | Almacena el nivel de alerta calculado (`id`, `zona`, `nivel`, `mensaje`, `fechaHora`, `vigente`). | Representa el estado de alerta del sistema. Posee la bandera booleana `vigente` para saber cuál es la alerta actual de la zona. |
| `NivelAlerta.java` | Enumeración: `NORMAL`, `PRECAUCION`, `PELIGRO`. | Tipado estricto que evita inconsistencias de texto y define los 3 estados del semáforo. |

---

### 2.2 Capa de Reglas de Negocio (`rules/`)

Aísla la lógica matemática y de decisión del sistema del resto de la infraestructura.

| Archivo | Qué hace | Por qué lo hace |
|---|---|---|
| `ReglaDeUmbral.java` | Convierte milímetros de lluvia acumulada en un `NivelAlerta` y asigna el mensaje preventivo para el ciudadano. | **Principio de Responsabilidad Única (SRP):** No consulta bases de datos ni servicios web; es una función matemática pura fácil de probar con tests unitarios. Lee los umbrales (`40 mm` y `80 mm`) de `application.properties` para poder cambiarlos sin recompilar. |

---

### 2.3 Capa de Clientes Externos (`client/`)

Aplica el **Patrón Estrategia (Strategy)** y el principio de Inversión de Dependencias (DIP) de SOLID.

```mermaid
classDiagram
    class ClienteDatosExternos {
        <<interface>>
        +nombreFuente() String
        +precipitacionAcumuladaMm(codigoEstacion) double
    }
    class ClienteOpenMeteo {
        +nombreFuente() "OPEN-METEO (En Vivo)"
        +precipitacionAcumuladaMm(codigoEstacion) double
    }
    class ClienteIdeam {
        +nombreFuente() "IDEAM"
        +precipitacionAcumuladaMm(codigoEstacion) double
    }
    class ClienteSimulado {
        +nombreFuente() "SIMULADA"
        +precipitacionAcumuladaMm(codigoEstacion) double
    }

    ClienteDatosExternos <|.. ClienteOpenMeteo : implementa
    ClienteDatosExternos <|.. ClienteIdeam : implementa
    ClienteDatosExternos <|.. ClienteSimulado : implementa
```

| Archivo | Qué hace | Por qué lo hace |
|---|---|---|
| `ClienteDatosExternos.java` | Interfaz que define el contrato: `nombreFuente()` y `precipitacionAcumuladaMm()`. | Permite cambiar de proveedor meteorológico sin modificar una sola línea del servicio ni de la base de datos. |
| `ClienteOpenMeteo.java` | Consulta satélites y radares de la OMM (WMO / ECMWF) para las coordenadas exactas de las 3 zonas de Villavicencio. | Provee **datos verídicos en tiempo real**, con tiempo de respuesta < 200 ms, alta disponibilidad y sin límite estricto de claves. Se activa con `alertas.fuente=open-meteo`. |
| `ClienteIdeam.java` | Consulta la API de Datos Abiertos de Colombia (`datos.gov.co`, dataset `s54a-sgyg`). | Provee conexión oficial con las estaciones telemétricas del IDEAM mediante consultas SoQL. Se activa con `alertas.fuente=ideam`. |
| `ClienteSimulado.java` | Genera lecturas aleatorias controladas entre 0 y 90 mm. | Funciona como **modo de respaldo / demostración** si no hay acceso a internet durante la sustentación. Se activa con `alertas.fuente=simulada`. |

---

### 2.4 Capa de Servicio (`service/`)

Orquesta el flujo de negocio y garantiza la integridad de los datos.

| Archivo | Qué hace | Por qué lo hace |
|---|---|---|
| `SincronizacionService.java` | Ejecuta el flujo principal: 1. Consulta la fuente externa para cada zona. 2. Guarda la `Lectura`. 3. Invalida la alerta anterior (`vigente = false`). 4. Evalúa umbrales y guarda la nueva `Alerta` vigente. Además, expone `obtenerHistorico(zonaId)` consultando las últimas 20 lecturas ordenadas cronológicamente para análisis de tendencias. | Anotado con `@Transactional`: Si la fuente externa falla a mitad del proceso, **hace rollback automático**: no se guardan lecturas incompletas ni se alteran las alertas previas. Centraliza las lecturas para los histogramas de tendencia hidrológica. |
| `MonitoreoAutonomoTask.java` | **Daemon 24/7 de monitoreo en segundo plano:** Ejecuta periódicamente (`@Scheduled`, cada 60 s en demo) la sincronización de las estaciones sin intervención humana. | Transforma a SisAlert en un verdadero Sistema de Alertas Tempranas preventivo. Si ocurre una creciente en la madrugada, el sistema detecta el riesgo y genera la alerta roja automáticamente aunque ningún usuario esté frente a la pantalla. |
| `AlertaService.java` | Consulta las alertas vigentes actuales de todas las zonas y las convierte a `AlertaDto`. | Permite que el tablero consulte rápidamente el estado actual sin ejecutar una nueva sincronización. |
| `ZonaService.java` | Lista las zonas registradas en el catálogo. | Separa la administración de zonas de la lógica de alertas. |

---

### 2.5 Capa de Controladores y Excepciones (`controller/` y `exception/`)

| Archivo | Qué hace | Por qué lo hace |
|---|---|---|
| `LecturaController.java` | Expone `POST /api/lecturas/sincronizar` (sincronización manual bajo demanda con auditoría) y `GET /api/lecturas/zonas/{zonaId}/historico` (recupera las últimas 20 lecturas de una cuenca para gráficas). | Permite disparar la sincronización manual y alimenta el modal de tendencias hidrológicas sin exponer entidades JPA directamente. |
| `AlertaController.java` | Expone `GET /api/alertas`. | Devuelve el semáforo y estado actual de cada zona para el polling de la interfaz. |
| `ZonaController.java` | Expone `GET /api/zonas`. | Retorna la lista de cuencas y comunidades. |
| `ManejadorErrores.java` | Captura `FuenteExternaException` con `@RestControllerAdvice`. | Traduce caídas de las APIs meteorológicas a un código HTTP `502 Bad Gateway` estructurado con JSON limpio, evitando que el servidor lance errores 500 no controlados. |
| `UsuarioToken.java` | Decodifica el token JWT en Base64 para extraer el nombre de usuario (`admin`). | Brinda trazabilidad y auditoría de quién ejecutó la sincronización sin acoplar el microservicio a la validación de llaves criptográficas de la API .NET. |

---

## 3. Frontend (`frontend/`)

Desarrollado en **React 19** con **Vite**, componentes funcionales y React Router DOM. Diseñado bajo el principio de **Cliente Delgado (Thin Client)**: no contiene lógica de umbrales ni calcula niveles; solo presenta los datos calculados por el microservicio.

```
frontend/src/
├── api/            # Clientes HTTP y normalizadores de datos
├── components/     # Layout, Navbar, Rutas Protegidas y Chips
├── context/        # Manejo de estado global de autenticación
├── pages/          # Vistas (Login, Registro, Tablero, Alertas)
└── utils/          # Formateadores de fecha y unidades
```

---

### 3.1 Clientes de Comunicación (`src/api/`)

| Archivo | Qué hace | Por qué lo hace |
|---|---|---|
| `apiClient.js` | Función `apiFetch` centralizada. Adjunta automáticamente `Authorization: Bearer <token>`, intercepta respuestas 401 (redireccionando a `/login`) y limpia respuestas de error. | Evita repetir cabeceras en cada llamada y oculta páginas HTML o stack traces crudos del servidor para que el usuario solo vea mensajes legibles. |
| `alertasApi.js` | Funciones `getAlertas()`, `sincronizar()` y `getHistoricoZona(zonaId)`. Normaliza los estados del backend a clases CSS (`verde`, `amarillo`, `rojo`, `sin-datos`). | Si el backend está apagado y está activado el modo demostración, conmuta a `alertasMock.js`. Si ocurre un error 502 de fuente externa, lo propaga para mostrar la alerta en rojo en pantalla. Provee acceso al histórico de lecturas en PostgreSQL. |
| `authApi.js` | Funciones `login()` y `register()` contra `/auth-api/api/auth`. | Traduce respuestas no estándar de la API de identidad (como HTTP 500 por credenciales erróneas) a mensajes amigables como *"Usuario o contraseña incorrectos"*. |
| `alertasMock.js` | Mock en memoria con las 3 zonas reales y umbrales 40/80 mm. | Permite que el frontend sea probado de forma autónoma sin backend si fuera necesario. |

---

### 3.2 Vistas y Componentes (`src/pages/` y `src/components/`)

| Archivo | Qué hace | Por qué lo hace |
|---|---|---|
| `LoginPage.jsx` | Formulario de acceso ciudadano. Guarda el JWT en `sessionStorage` y navega a `/estaciones`. | Puerta de entrada segura al sistema. |
| `EstacionesPage.jsx` | **Tablero Principal:** Muestra una tarjeta por cada una de las 3 zonas con su indicador de semáforo. Consulta `GET /api/alertas` cada 5 segundos, contiene el botón **«Sincronizar lecturas»**, aloja el mapa georreferenciado e integra el botón para abrir el modal de histórico por cuenca. | Es el centro de monitoreo en tiempo real. Si las alertas están en gris, indica que el sistema arrancó en limpio y requiere sincronizar. |
| `ModalHistoricoZona.jsx` | **Modal de Tendencias y Análisis Hidrológico 24h:** Ventana modal emergente que consume el endpoint histórico. Calcula la pendiente de la lluvia (ascendente ↗, descendente ↘ o estable →), grafica un **histograma interactivo de barras** con líneas guía de umbrales críticos (40 mm amarillo, 80 mm rojo) y desglosa la tabla de mediciones recientes con fecha, hora, mm y fuente. | Permite a los analistas de gestión del riesgo y organismos de socorro determinar si una creciente viene en aumento acelerado o si el caudal ya comenzó a drenar, facilitando decisiones preventivas fundamentadas. |
| `AlertasPage.jsx` | **Vista de Alertas Activas:** Filtra y ordena las zonas que están en `PRECAUCION` o `PELIGRO` (las más críticas primero). | Permite a los organismos de socorro y a la comunidad ver de inmediato dónde hay peligro inminente de desbordamiento. |
| `BannerEmergencia.jsx` | **Banner de Alerta Crítica y Evacuación:** Franja roja parpadeante que se activa automáticamente si alguna cuenca entra en nivel `PELIGRO` (≥ 80 mm). Despliega teléfonos de auxilio de Villavicencio (Bomberos 119, Cruz Roja 132, Defensa Civil 144) y puntos de encuentro seguros del POMCA. | Protege la vida del ciudadano. En un SAT real, el usuario no solo debe saber que hay peligro, sino qué protocolo de evacuación seguir y a quién llamar. |
| `MapaVillavicencio.jsx` | **Centro de Georreferenciación en Vivo:** Integra un mapa satelital/cartográfico interactivo mediante **Leaflet / OpenStreetMap** con marcadores telemétricos en las coordenadas exactas de los 3 sectores de riesgo urbano en Villavicencio (`[4.148, -73.634]` Barzal/Guatiquía Norte, `[4.128, -73.626]` Villacentro/Caño Grande Centro, `[4.075, -73.655]` Catumare/Porfía/Guayuriba Sur). Incluye aros de pulso radar por color del semáforo y popups con detalles hidrológicos. Además, incorpora una **vista de respaldo vectorial offline** con el trazado del río Guatiquía, río Guayuriba y caño Maizaro. | Brinda conciencia situacional geoespacial inmediata a organismos de socorro y ciudadanos. Permite entender qué zonas están aguas arriba y cuáles están aguas abajo. |
| `RutaProtegida.jsx` | Verifica si existe un token en la sesión; si no existe, redirige automáticamente a `/login`. | Impide que usuarios no autenticados accedan al tablero o a las alertas. |

---

## 4. Base de Datos PostgreSQL (migraciones Flyway)

La persistencia se ejecuta en **PostgreSQL 16**, base **`sisalert`**, esquema **`alertas`**. Las tablas las crea y versiona Flyway con `alertas-service/src/main/resources/db/migration` (`V1__esquema_inicial.sql`, `V2__crud_zonas.sql`). La API de identidad usa el esquema `identidad` de la misma base.

```mermaid
erDiagram
    ZONA ||--o{ LECTURA : "registra (1:N)"
    ZONA ||--o{ ALERTA : "genera (1:N)"

    ZONA {
        bigint id PK
        varchar nombre UK
        varchar cuerpo_agua
        varchar comunidades
        varchar codigo_estacion
    }

    LECTURA {
        bigint id PK
        bigint zona_id FK
        double precipitacion_mm
        datetime fecha_hora
        varchar fuente
    }

    ALERTA {
        bigint id PK
        bigint zona_id FK
        varchar nivel
        varchar mensaje
        datetime fecha_hora
        boolean vigente
    }
```

### Reglas de Integridad en Base de Datos:
1. **Claves foráneas con eliminación en cascada (`ON DELETE CASCADE`):** Si se llegase a eliminar una zona, se limpian ordenadamente sus lecturas y alertas asociadas.
2. **Histórico preservado:** Las lecturas nunca se sobreescriben; se insertan como nuevos registros con marca de tiempo.
3. **Control de vigencia:** La tabla `alerta` guarda todas las alertas que ha tenido una cuenca a lo largo del tiempo, pero solo una tiene `vigente = true` a la vez.

---

## 5. Flujo de Ejecución de Extremo a Extremo (Secuencia)

Cuando un usuario autenticado presiona **«Sincronizar lecturas»** en la pantalla del tablero, se ejecuta el siguiente ciclo:

```mermaid
sequenceDiagram
    actor Usuario as Ciudadano / Admin
    participant UI as EstacionesPage (React)
    participant Api as alertasApi.js
    participant Ctrl as LecturaController
    participant Srv as SincronizacionService
    participant Ext as ClienteOpenMeteo / IDEAM
    participant Rule as ReglaDeUmbral
    participant DB as PostgreSQL (sisalert)

    Usuario->>UI: Clic en «Sincronizar lecturas»
    UI->>Api: sincronizar()
    Api->>Ctrl: POST /alertas-api/api/lecturas/sincronizar (Bearer JWT)
    Ctrl->>Srv: sincronizar()
    
    loop Para cada una de las 3 zonas
        Srv->>Ext: precipitacionAcumuladaMm(codigoEstacion)
        Ext-->>Srv: mm reales de lluvia (ej. 45.2 mm)
        Srv->>DB: INSERT INTO lectura (zona_id, mm, fecha, fuente)
        Srv->>DB: UPDATE alerta SET vigente = false WHERE zona_id = ...
        Srv->>Rule: evaluar(45.2 mm)
        Rule-->>Srv: Nivel PRECAUCION + Mensaje
        Srv->>DB: INSERT INTO alerta (zona_id, PRECAUCION, ..., vigente = true)
    end

    Srv-->>Ctrl: Lista de AlertaDto actualizados
    Ctrl-->>Api: HTTP 200 OK [ { zona: "Guatiquía Norte", nivel: "PRECAUCION", ... } ]
    Api-->>UI: Datos normalizados
    UI-->>Usuario: Tarjetas cambian de color (Semáforo Amarillo)
```

---

## 6. Resumen de Puertos y Servicios

| Servicio | Tecnología | Puerto | Rol |
|---|---|---|---|
| **Frontend** | React 19 + Vite | **5173** | Interfaz de usuario y proxy inverso |
| **Microservicio Alertas** | Spring Boot 3.5 | **8081** | Gestión de cuencas, lectura meteorológica y semáforos |
| **API Autenticación** | ASP.NET Core (.NET 10) | **5132** | Emisión de tokens JWT y registro de usuarios |
| **Base de Datos** | PostgreSQL 16 | **5432** | Almacenamiento persistente (`sisalert`, esquemas `alertas` e `identidad`) |
| **Fuente Meteorológica** | Open-Meteo / IDEAM | **443 (HTTPS)** | Proveedor de mediciones pluviométricas en tiempo real |

---

## 7. Evolución a Sistema de Alertas Tempranas (SAT) — Las 4 Grandes Mejoras

Para superar el alcance de un CRUD tradicional y convertir a SisAlert en un **Sistema de Alertas Tempranas (SAT) de nivel operativo real**, se implementaron cuatro mejoras de alto impacto ingenieril:

### 1. Monitoreo Autónomo 24/7 en Segundo Plano (`@Scheduled`)
- **Problema previo:** El sistema solo consultaba el clima si un usuario iniciaba sesión y presionaba manualmente un botón en el navegador. En una creciente súbita nocturna, la comunidad no recibiría alerta si nadie estaba despierto mirando la pantalla.
- **Implementación técnica:** Se activó `@EnableScheduling` en `AlertasServiceApplication` y se creó `MonitoreoAutonomoTask` que corre de forma continua cada 60 segundos (configurable por `alertas.monitoreo-automatico.intervalo-ms`).
- **Comportamiento:** Cada minuto, el microservicio consulta satélites/radares de Open-Meteo para las cuencas de Villavicencio, evalúa los umbrales de 40/80 mm, almacena la lectura histórica en PostgreSQL y actualiza el semáforo sin requerir presencia humana.

### 2. Banner de Emergencia Crítica y Protocolo de Evacuación (`BannerEmergencia.jsx`)
- **Problema previo:** Si el semáforo cambiaba a rojo, solo se pintaba una pequeña tarjeta de color sin instrucciones claras para el ciudadano.
- **Implementación técnica:** Componente React reactivo en el `Layout` principal que monitorea el estado global de alertas. Si detecta al menos una cuenca en nivel `PELIGRO` (≥ 80 mm), despliega una franja superior roja con animación pulsante de alarma.
- **Contenido operativo:** Despliega de inmediato los números telefónicos oficiales de emergencias de Villavicencio (Bomberos 119, Cruz Roja 132, Defensa Civil 144, Policía 123) y las zonas seguras de evacuación en altura documentadas en el POMCA (Polideportivo 7 de Agosto, Coliseo La Grama, etc.).

### 3. Georreferenciación en Tiempo Real y Red Hidrográfica (`MapaVillavicencio.jsx`)
- **Problema previo:** Los usuarios veían listas de texto abstractas sin entender la ubicación espacial de las cuencas respecto a sus barrios.
- **Implementación técnica:** Se integró un mapa cartográfico interactivo con **Leaflet y OpenStreetMap** centrado en el casco urbano de Villavicencio (`4.118, -73.638`, Zoom 13). Los marcadores cuentan con anillos de radar pulsante cuyo color refleja el semáforo hidrológico en tiempo real.
- **Respaldo Vectorial Offline:** Pensando en contingencias donde se corte la conectividad externa o caigan los mapas de satélite, se desarrolló un conmutador a **Red Hidrográfica Vectorial (SVG interactivo)** con las siluetas de la Cordillera Oriental, el río Guatiquía, el río Guayuriba y el caño Maizaro.

### 4. Análisis de Tendencia Hidrológica e Histórico 24h (`ModalHistoricoZona.jsx`)
- **Problema previo:** El sistema solo mostraba la última lectura puntual, impidiendo saber si el nivel de lluvia venía aumentando rápidamente (riesgo inminente de avalancha) o si venía disminuyendo (drenaje paulatino).
- **Implementación técnica:**
  - **Backend:** Se creó el DTO inmutable `LecturaDto`, la consulta JPA `findTop20ByZonaIdOrderByFechaHoraDesc(zonaId)` y el endpoint REST `GET /api/lecturas/zonas/{zonaId}/historico`.
  - **Frontend:** Se diseñó una ventana modal accesible desde cada tarjeta de cuenca en `EstacionesPage`.
  - **Indicador de Pendiente:** Analiza las últimas dos lecturas para clasificar la tendencia en `En aumento ↗` (rojo/alerta), `En descenso ↘` (verde/recuperación) o `Estable →`.
  - **Histograma Dinámico:** Gráfico de barras escalado proporcionalmente a 100 mm con líneas guía en los umbrales de advertencia (40 mm) y peligro (80 mm), acompañado de la tabla detallada de auditoría con fecha, hora, mm y proveedor de datos.

