# Desplegar SisAlert en Azure

Guía para quien tiene la suscripción **Azure for Students**. Se hace desde un PC con Windows y tarda unos **20 a 30 minutos** la primera vez, casi todo esperando a que Azure cree PostgreSQL y a que se construyan las imágenes.

La arquitectura que se crea está en [`docs/arquitectura-nube.md`](../../docs/arquitectura-nube.md).

## 1. Instalar (una sola vez)

| Programa | Cómo |
|---|---|
| **Azure CLI** | `winget install -e --id Microsoft.AzureCLI`. Luego **cerrar y abrir** PowerShell |
| **Docker Desktop** | docker.com → Download. Debe estar **abierto** al desplegar |
| **Git** | `winget install -e --id Git.Git` |

Comprobar que todo responde, en una ventana **nueva** de PowerShell:

```powershell
az version
```

```powershell
docker info
```

Si `az` "no se reconoce": cerrar todas las ventanas de PowerShell y abrir una nueva. Si sigue, reiniciar el PC.

## 2. Preparar la cuenta (una sola vez)

Iniciar sesión (abre el navegador):

```powershell
az login
```

Debe mostrar **Azure for Students**:

```powershell
az account show --query name -o tsv
```

Habilitar los servicios que usa SisAlert:

```powershell
az extension add --name containerapp --upgrade
```

```powershell
foreach ($p in "Microsoft.App","Microsoft.OperationalInsights","Microsoft.ContainerRegistry","Microsoft.DBforPostgreSQL","Microsoft.KeyVault","Microsoft.ManagedIdentity") { az provider register --namespace $p }
```

## 3. Desplegar

Desde la carpeta del repositorio, en la rama `main` o `develop` actualizada:

```powershell
git clone https://github.com/aleprz-afk/SisAlert.git
```

```powershell
cd SisAlert
```

```powershell
.\infra\azure\desplegar.ps1
```

Si PowerShell dice que *la ejecución de scripts está deshabilitada*, se permite solo para esa ventana y se vuelve a ejecutar:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

**Opciones del script:**

| Opción | Para qué |
|---|---|
| `-Region eastus` | Otra región. Las suscripciones de estudiante **solo permiten algunas**; para ver cuáles: `az policy assignment list --query "[].parameters.listOfAllowedLocations.value" -o tsv`. Por defecto es `eastus2` |
| `-GeminiApiKey "AIza..."` | Activa los boletines con Gemini. Sin ella se usa la plantilla |
| `-PostgresRegion westus` | Crea PostgreSQL en otra región (el resto queda en `-Region`). Sirve si Azure responde `SkuNotAvailable` para la base |
| `-PostgresSku Standard_B2s` | Otro tamaño de PostgreSQL si el más barato (`Standard_B1ms`) no está disponible. Para ver cuáles hay: `az postgres flexible-server list-skus --location <region> -o table` |
| `-PostgresNombre pg-sisalert-2` | Otro nombre para el servidor PostgreSQL, si un intento fallido dejó el nombre reservado (`InvalidResourceLocation`). **Una vez que funcione, usar siempre el mismo nombre** en las siguientes ejecuciones |
| `-ReplicasMinimas 1` | Frontend, identidad e IA siempre encendidos. Para el día de la sustentación |

Al final muestra la **URL pública** y cómo ver la contraseña de `admin`.

**Los secretos los genera el script al azar** y los guarda en Key Vault; nadie tiene que inventarlos. Para **publicar cambios del código** basta con volver a ejecutar `.\infra\azure\desplegar.ps1`: reutiliza los secretos y la base, y solo reconstruye las imágenes y actualiza las apps.

## 4. Cuidar el crédito

| Comando | Qué hace |
|---|---|
| `.\infra\azure\pausar.ps1` | Apaga los contenedores y detiene PostgreSQL. No borra nada |
| `.\infra\azure\pausar.ps1 -Encender` | Vuelve a encender todo (1 a 3 minutos) |
| `.\infra\azure\pausar.ps1 -Encender -Demo` | Enciende y deja todo siempre activo, sin espera en el primer acceso |
| `az group delete --name rg-sisalert` | **Borra todo** al terminar el semestre. No se puede deshacer |

Con todo encendido el costo estimado es de **USD 20 a 35 al mes**. Pausado, solo se paga el almacenamiento: centavos. Azure vuelve a encender PostgreSQL solo si pasa 7 días detenido.

## 5. Si algo falla

| Mensaje | Qué hacer |
|---|---|
| `RequestDisallowedByAzure`, `RequestDisallowedByPolicy` o `LocationNotAvailableForResourceType` | La región no está permitida para la suscripción. El script muestra la lista de regiones permitidas; volver a ejecutar con una de ellas: `-Region <region>`. No hace falta borrar nada |
| `SkuNotAvailable` en `pg-sisalert-...` | Ese tamaño de PostgreSQL no está disponible en la región para la suscripción. Revisar los disponibles con `az postgres flexible-server list-skus --location <region> -o table` y repetir con `-PostgresRegion` o `-PostgresSku` |
| `InvalidResourceLocation` (*already exists in location ...*) en `pg-sisalert-...` | Un intento fallido dejó ese nombre reservado en otra región. Repetir con otro nombre: `-PostgresNombre pg-sisalert-2` (o esperar a que Azure libere el nombre) |
| `MissingSubscriptionRegistration` | Falta habilitar un servicio: repetir el paso 2 y esperar unos minutos |
| `Docker no está corriendo` | Abrir Docker Desktop y esperar a que diga *Engine running* |
| `No se pudo leer el secreto ...` | La cuenta que ejecuta no es la que desplegó la primera vez. Debe ejecutarlo el dueño de la suscripción |
| La URL abre pero dice *Bad Gateway* | Los servicios internos siguen arrancando. Esperar 2 o 3 minutos. Si sigue, en el portal: Container App → *Supervisión* → *Secuencia de registro* |
| `az acr login` falla | Abrir Docker Desktop y repetir |

Los logs de cada servicio están en el portal: grupo `rg-sisalert` → el Container App → **Supervisión → Secuencia de registro**.

## 6. Si el frontend carga pero el login no funciona (entorno "Express")

Algunas suscripciones de estudiante crean el entorno de Container Apps en modo **Express**. Ahí las apps internas solo se pueden llamar **por su nombre corto** desde otra app del entorno (`http://ca-auth`); la dirección completa responde `404 {"error":"Not found"}`. Desde esta versión el script ya usa los nombres cortos.

**1. Probar desde dentro del frontend que el nombre corto funciona (30 segundos):**

```powershell
az containerapp exec --resource-group rg-sisalert --name ca-frontend --command "wget -S -O- http://ca-auth/health"
```

**2a. Si responde `Healthy`**, volver a desplegar con la versión actualizada del repositorio:

```powershell
git pull
```

```powershell
.\infra\azure\desplegar.ps1 -Region westus3 -PostgresSku Standard_B2ats_v2 -PostgresNombre pg-sisalert-2
```

**2b. Si no responde** (error de nombre o de conexión), usar el plan B: identidad, alertas e IA quedan **públicos**, protegidos por JWT con roles (alertas) y `X-Api-Key` (IA). Es el mismo comando con `-ServiciosPublicos`:

```powershell
.\infra\azure\desplegar.ps1 -Region westus3 -PostgresSku Standard_B2ats_v2 -PostgresNombre pg-sisalert-2 -ServiciosPublicos
```

**3. Probar:** abrir la URL que muestra el script, entrar como `admin` (la contraseña está en Key Vault: `az keyvault secret show --vault-name <kv> --name admin-password --query value -o tsv`) y pulsar *Sincronizar lecturas*.

## Qué crea el script

| Recurso | Nombre |
|---|---|
| Grupo de recursos | `rg-sisalert` |
| Container Apps | `ca-frontend` (público), `ca-auth`, `ca-alertas`, `ca-ai` (internos) |
| PostgreSQL Flexible Server | `pg-sisalert-<sufijo>` (B1ms, SSL, copias 7 días) |
| Key Vault | `kv-sisalert-<sufijo>` (todos los secretos) |
| Container Registry | `acrsisalert<sufijo>` |
| Identidad administrada | `id-sisalert-<sufijo>` (lee el registro y Key Vault, sin contraseñas) |
| Log Analytics | `log-sisalert-<sufijo>` |

La infraestructura está descrita como código en [`main.bicep`](main.bicep).
