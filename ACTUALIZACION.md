# SisAlert — Guía de actualizaciones (Paso 4)

**Fecha:** 18 de noviembre de 2026  
**Responsable de esta actualización:** Eduar Murcia  
**Rama sugerida:** `feature/cliente-ideam-y-zonas`

---

## Resumen de lo que se hizo

Se completó el **Paso 4** del proyecto: fuente de datos real, zonas con sus comunidades y umbrales justificados. Los cambios afectan tres archivos del microservicio de alertas (`alertas-service`) y no tocan el frontend ni la API de autenticación.

---

## Archivo 1 — `ClienteIdeam.java`

**Ruta en el repositorio:**
```
alertas-service/src/main/java/co/edu/unillanos/sisalert/alertas/client/ClienteIdeam.java
```

**Qué es:** implementación real de la interfaz `ClienteDatosExternos` que consulta la precipitación del IDEAM a través del portal de datos abiertos de Colombia (datos.gov.co, conjunto `s54a-sgyg`).

**Cómo funciona paso a paso:**

1. **Se activa** solo cuando `alertas.fuente=ideam` en `application.properties`. Si la propiedad vale `simulada` (el valor por defecto anterior), este cliente se ignora y el `ClienteSimulado` sigue activo. Esto es el mecanismo `@ConditionalOnProperty` de Spring Boot.

2. **Paso 1 — Busca la fecha más reciente.** El portal del IDEAM puede estar atrasado varios días (en las pruebas, el último dato disponible era del 24 de julio de 2026). Por eso el cliente no pide "los datos de hoy": primero pregunta cuál es la fecha más reciente disponible para esa estación con una consulta `$select=max(fechaobservacion)`. Esto evita que la consulta del día devuelva cero registros y se lance un error innecesario.

3. **Paso 2 — Suma la precipitación del día.** Con la fecha encontrada, consulta todos los registros de ese día para la estación (hasta 200 intervalos) y los suma. El portal guarda la precipitación por intervalos de aproximadamente 10 minutos; la suma de todos los intervalos del día es la lluvia acumulada.

4. **Conversión de tipos.** El portal devuelve `valorobservado` como texto (por ejemplo `"12.5"`), no como número. El cliente lo convierte a `double`. Si el valor no es numérico o es negativo, lo ignora con un aviso en el log y continúa.

5. **Manejo de fallos.** Si el portal no responde, responde con un código HTTP distinto de 200, o devuelve datos que no se pueden parsear, el cliente lanza `FuenteExternaException`. El `SincronizacionService` la captura, no guarda nada en la base de datos, y el controlador responde `502 Bad Gateway` al frontend. Las alertas anteriores se conservan intactas.

6. **Token de aplicación (opcional pero recomendado).** El portal de datos.gov.co limita las peticiones sin token. Se puede registrar una cuenta gratuita, obtener un App Token y ponerlo en la variable de entorno `ALERTAS_IDEAM_APP_TOKEN`. El cliente lo incluye automáticamente en el encabezado `X-App-Token`. Si la variable no existe, el cliente funciona igual pero con límite de peticiones más bajo.

**Cómo configurarlo:**

```properties
# En application.properties: activar la fuente real
alertas.fuente=ideam

# Token (opcional): definir como variable de entorno, NO poner el valor real aquí
alertas.ideam.app-token=${ALERTAS_IDEAM_APP_TOKEN:}
```

Para definir la variable de entorno en Windows (PowerShell):
```powershell
$env:ALERTAS_IDEAM_APP_TOKEN = "tu_token_aqui"
```

En Linux/Mac:
```bash
export ALERTAS_IDEAM_APP_TOKEN="tu_token_aqui"
```

**Dependencias necesarias:** ninguna nueva. Usa `java.net.http.HttpClient` (nativo desde Java 11) y `com.fasterxml.jackson.databind.ObjectMapper` (ya incluido por Spring Boot Web). No hay que tocar el `pom.xml`.

**Si la fuente falla en la demo:**
Cambiar a `alertas.fuente=simulada` en `application.properties` y reiniciar el servicio. Las mismas tres zonas reales aparecerán con lluvia aleatoria. En la sustentación se explica: "La fuente real está disponible pero puede tener retraso; el sistema tiene un modo de demostración que usa los mismos umbrales y zonas reales."

---

## Archivo 2 — `CargaInicial.java`

**Ruta en el repositorio:**
```
alertas-service/src/main/java/co/edu/unillanos/sisalert/alertas/config/CargaInicial.java
```

**Qué reemplaza:** la versión anterior tenía `EST-001`, `EST-002`, `EST-003`, `EST-004` como códigos de estación y "Por confirmar" como comunidades. Esos eran datos provisionales. Esta versión los reemplaza con datos reales.

**Las tres zonas y su trazabilidad:**

### Zona 1 — Guatiquía Norte
- **Estación IDEAM:** ICA Villavicencio (`0035035100`)
- **Coordenadas:** lat 4.137225, lng -73.624998
- **Cuerpos de agua cubiertos:** Río Guatiquía, Caño Parrado, Caño Gramalote, Quebrada Grande, Quebrada Honda, Caño Buque
- **Por qué esta estación:** está ubicada al norte de la ciudad, aguas arriba del punto donde el río Guatiquía atraviesa los barrios urbanos. Es la estación de referencia para toda la cuenca alta del Guatiquía dentro del municipio. El Caño Parrado, el Gramalote, la Quebrada Grande, la Quebrada Honda y el Caño Buque drenan todos hacia el Guatiquía, por lo que la lluvia que registra esta estación es representativa del riesgo en todos ellos.
- **Comunidades:** Barzal Alto, Barzal Bajo, Siete de Agosto, Villa Lorena, Mancera, La Nohora, El Triunfo

### Zona 2 — Guayuriba Sur
- **Estación IDEAM:** Guayuriba (`3502700090`)
- **Coordenadas:** lat 3.958454, lng -73.270980
- **Cuerpos de agua cubiertos:** Río Guayuriba, Caño Rosablanca, Caño La Cuerera
- **Por qué esta estación:** ubicada en la cuenca del río Guayuriba, al sur del municipio. El Guayuriba es afluente del Guatiquía y en episodios de lluvia intensa su creciente afecta los barrios del sur de Villavicencio. El Caño Rosablanca y el Caño La Cuerera drenan hacia el Guayuriba.
- **Comunidades:** Apiay, Catumare, Playa Rica, La Esperanza, La Nohora Rural, Vanguardia

### Zona 3 — Caño Grande Centro
- **Estación IDEAM:** La Libertad (`0035025110`)
- **Coordenadas:** lat 4.057415, lng -73.467865
- **Cuerpos de agua cubiertos:** Caño Grande, Caño Maizaro, Quebrada La Unión
- **Por qué esta estación:** ubicada en la zona centro-sur del municipio, entre las dos anteriores. El Caño Maizaro recorre el centro-oriente de la ciudad y desemboca directamente en el río Meta. La Quebrada La Unión es tributaria del Maizaro. Esta estación es la más representativa para ese sistema de drenaje.
- **Comunidades:** Porvenir, Bello Horizonte, Villacentro, La Rosita, Marsella, Porfía

**Fuente de las comunidades:** barrios identificados por el POMCA del río Guatiquía (CORMACARENA, 2018) como zonas de amenaza alta por inundación.

---

## Archivo 3 — `application.properties`

**Ruta en el repositorio:**
```
alertas-service/src/main/resources/application.properties
```

**Cambios respecto a la versión anterior:**

| Propiedad | Antes | Ahora | Por qué |
|---|---|---|---|
| `server.port` | 8080 | **8081** | Evita choques con otros servicios en el computador; coincide con el README, los documentos de diseño y el diagrama de arquitectura |
| `alertas.umbral.precaucion-mm` | 30 | **40** | El IDEAM clasifica como "fuerte" la lluvia mayor a 50 mm/día en la Orinoquía. Se baja a 40 mm para dar margen de reacción temprana en Villavicencio, que tiene pluviosidad muy alta |
| `alertas.umbral.peligro-mm` | 60 | **80** | Las crecientes históricas más graves del Guatiquía han ocurrido con lluvias de 60-80 mm en pocas horas. Se fija en 80 mm para activar la alerta antes del desborde |
| `alertas.fuente` | simulada | **ideam** | Activa el cliente real del IDEAM |
| `alertas.ideam.app-token` | (no existía) | `${ALERTAS_IDEAM_APP_TOKEN:}` | Lee el token del entorno sin exponerlo en el repositorio |

**Nota sobre el puerto:** Nicolás también debe cambiar el `target` del proxy en `frontend/vite.config.js` de `8080` a `8081`. Eso lo hace él en su rama según la guía de cierre.

---

## Cómo aplicar estos cambios al repositorio

### Si partes del ZIP entregado

El ZIP ya incluye los tres archivos en sus rutas correctas. Solo hay que subirlos:

```bash
git switch main
git pull
git switch -c feature/cliente-ideam-y-zonas

# Los archivos ya están en sus rutas dentro del ZIP.
# Si clonaste desde GitHub, copia manualmente los tres archivos:
#   ClienteIdeam.java  →  alertas-service/src/.../client/
#   CargaInicial.java  →  alertas-service/src/.../config/
#   application.properties  →  alertas-service/src/main/resources/

git add alertas-service/src/main/java/co/edu/unillanos/sisalert/alertas/client/ClienteIdeam.java
git add alertas-service/src/main/java/co/edu/unillanos/sisalert/alertas/config/CargaInicial.java
git add alertas-service/src/main/resources/application.properties
git commit -m "feat: cliente real IDEAM, zonas Villavicencio y umbrales 40/80 mm"
git push -u origin feature/cliente-ideam-y-zonas
```

Abrir Pull Request hacia `main`, revisor: **Nicolás Villarraga**.

### Verificación antes del PR

```bash
# Desde alertas-service/
./mvnw spring-boot:run
```

La consola debe mostrar:
```
Tomcat started on port 8081
```

Luego, en otra terminal:

```bash
# Las tres zonas reales
curl.exe http://localhost:8081/api/zonas

# Llama al IDEAM (puede tardar unos segundos)
curl.exe -X POST http://localhost:8081/api/lecturas/sincronizar
```

Si el IDEAM responde: se ven las tres zonas con su nivel de alerta y la fecha del dato.  
Si no responde: aparece un mensaje de error 502 y las alertas anteriores se conservan.  
Para cambiar a modo simulado: editar `alertas.fuente=simulada` y reiniciar.

---

## Pendiente para mañana miércoles 18

- [ ] Actualizar `docs/infraestructura.md`: reemplazar `[completar]` del puerto de la API de autenticación (es **5132**) y de la fuente externa (ya es datos.gov.co, conjunto s54a-sgyg).
- [ ] Actualizar `docs/aplicacion.md`: paquete base (`co.edu.unillanos.sisalert.alertas`), zonas reales en §4.3 y umbrales reales en §6.
- [ ] Actualizar `docs/modelo-datos.md` §6: llenar la tabla de zonas con los datos reales.
- [ ] PR de documentos hacia `main`, revisor: Alex.
- [ ] Revisar el PR de Nicolás (puerto y tests).

---

## Recomendaciones generales para la entrega

1. **No subir el token del IDEAM al repositorio.** Si por error queda en `application.properties`, borrarlo del archivo, hacer commit y luego ir a datos.gov.co a revocar el token y crear uno nuevo.

2. **Probar el flujo completo sin internet antes del jueves.** Poner `alertas.fuente=simulada`, levantar los tres servicios y hacer la demo entera: login → alertas en gris → sincronizar → colores. Si algo falla sin internet, falla también en la sustentación.

3. **El modo simulado es una decisión de diseño, no una trampa.** El frontend tiene `ClienteSimulado` como respaldo explícito con `@ConditionalOnProperty`. Explicarlo así en la sustentación muestra que el equipo entendió el patrón de interfaz/implementación.

4. **Preparar la respuesta a "¿por qué los datos del IDEAM llegan atrasados?"** La respuesta correcta es: el portal datos.gov.co actualiza su telemetría con un retardo variable. El sistema lo maneja consultando la fecha más reciente disponible (no "los datos de hoy") y mostrando esa fecha en pantalla. Es una limitación conocida, documentada y resuelta.

5. **Cada integrante debe poder explicar su parte.** Eduar explica las zonas (por qué esas tres estaciones, por qué esos umbrales, cómo funciona el `ClienteIdeam`). Nicolás explica las capas del microservicio y los tests. Brayan explica el mock y las correcciones del frontend. Alex explica el login y el README.
