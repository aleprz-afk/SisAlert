<#
.SYNOPSIS
    Despliega SisAlert completo en Azure (o lo actualiza si ya existe).

.DESCRIPTION
    1. Crea o actualiza la infraestructura con Bicep (main.bicep, fase 1).
    2. Construye las 4 imágenes Docker y las sube al Container Registry.
    3. Crea en PostgreSQL los usuarios por servicio (infra/db/usuarios-por-servicio.sql).
    4. Crea o actualiza los 4 Container Apps (main.bicep, fase 2).
    5. Comprueba que todo responda y muestra la URL pública.

    Los secretos (contraseñas de la base, clave JWT, contraseña de admin, clave de la IA) se
    generan al azar la primera vez y se guardan en Key Vault. Al volver a ejecutar el script
    se leen de ahí, así que se puede repetir sin romper nada (por ejemplo, para publicar
    cambios del código).

    Requisitos: Azure CLI (az login hecho), Docker Desktop abierto y ejecutar desde el
    repositorio clonado.

.EXAMPLE
    .\infra\azure\desplegar.ps1

.EXAMPLE
    .\infra\azure\desplegar.ps1 -Region eastus -GeminiApiKey "AIza..."
#>
[CmdletBinding()]
param(
    [string]$Grupo = "rg-sisalert",
    [string]$Region = "eastus2",
    # Opcional. Si no se da, se conserva la que ya esté en Key Vault (o los boletines usan plantilla).
    [string]$GeminiApiKey = "",
    # 0 = frontend, identidad e IA se apagan sin tráfico (más barato). 1 = siempre encendidos.
    [ValidateSet(0, 1)]
    [int]$ReplicasMinimas = 0,
    # PostgreSQL en otra región (vacío = la misma que -Region) y con otro tamaño, si el
    # predeterminado (Standard_B1ms, el más barato) no está disponible para la suscripción.
    [string]$PostgresRegion = "",
    # Otro nombre para el servidor PostgreSQL (vacío = pg-sisalert-<sufijo>), por si un intento
    # fallido dejó el nombre bloqueado en otra región (error InvalidResourceLocation).
    [string]$PostgresNombre = "",
    [string]$PostgresSku = "Standard_B1ms",
    [ValidateSet("Burstable", "GeneralPurpose", "MemoryOptimized")]
    [string]$PostgresTier = "Burstable"
)

# "Continue" a propósito: en Windows PowerShell 5.1, con "Stop", cualquier aviso que az o docker
# escriban en stderr (aunque terminen bien) detendría el script. Cada comando externo se
# revisa por su código de salida ($LASTEXITCODE) y los errores reales se lanzan con throw.
$ErrorActionPreference = "Continue"
$raiz = (Resolve-Path (Join-Path $PSScriptRoot "..\..") -ErrorAction Stop).Path
$plantilla = Join-Path $PSScriptRoot "main.bicep"

function Paso([string]$texto) { Write-Host "`n==> $texto" -ForegroundColor Cyan }
function Ok([string]$texto) { Write-Host "    $texto" -ForegroundColor Green }

# Ejecuta un comando externo (az, docker) y se detiene si falla.
function Ejecutar([string]$descripcion, [scriptblock]$comando) {
    $salida = & $comando
    if ($LASTEXITCODE -ne 0) { throw "Falló: $descripcion" }
    return $salida
}

function Nuevo-Secreto([int]$bytes = 24) {
    $datos = New-Object byte[] $bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($datos)
    # Sin + / = para que sirva en cadenas de conexión y URLs.
    return ([Convert]::ToBase64String($datos) -replace "[+/=]", "")
}

# ---------------------------------------------------------------------------
Paso "Comprobando requisitos"
if (-not (Get-Command az -ErrorAction SilentlyContinue)) { throw "No se encuentra Azure CLI (az). Instálelo y abra una ventana nueva de PowerShell." }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "No se encuentra Docker. Instale Docker Desktop." }
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker no está corriendo: abra Docker Desktop y espere a que diga 'Engine running'." }
$suscripcion = az account show --query name -o tsv 2>$null
if ($LASTEXITCODE -ne 0 -or -not $suscripcion) { throw "No hay sesión de Azure: ejecute 'az login'." }
Ok "Suscripción: $suscripcion"
# Id (objectId) de quien despliega, para darle permiso de leer los secretos en Key Vault.
# En cuentas de estudiante 'az ad signed-in-user show' a veces falla; el token de la sesión
# siempre trae el id en el campo "oid".
$usuarioId = az ad signed-in-user show --query id -o tsv 2>$null
if ($LASTEXITCODE -ne 0 -or -not $usuarioId) {
    $usuarioId = ""
    $token = az account get-access-token --query accessToken -o tsv 2>$null
    if ($LASTEXITCODE -eq 0 -and $token) {
        $carga = $token.Split(".")[1].Replace("-", "+").Replace("_", "/")
        while ($carga.Length % 4) { $carga += "=" }
        $usuarioId = ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($carga)) | ConvertFrom-Json).oid
    }
}
if ($usuarioId) { Ok "Usuario que despliega: $usuarioId" } else { Write-Host "    No se pudo obtener el id del usuario; no podrá releer los secretos en próximas ejecuciones." -ForegroundColor Yellow }

$etiqueta = (git -C $raiz rev-parse --short HEAD 2>$null)
if ($LASTEXITCODE -ne 0 -or -not $etiqueta) { $etiqueta = Get-Date -Format "yyyyMMddHHmm" }
Ok "Versión a desplegar: $etiqueta"

# ---------------------------------------------------------------------------
Paso "Grupo de recursos $Grupo"
# Si ya existe (por ejemplo, de un intento anterior en otra región) se reutiliza: la región del
# grupo no limita la de sus recursos, que se crean en -Region.
$grupoExiste = az group exists --name $Grupo
if ("$grupoExiste".Trim() -eq "true") {
    Ok "Ya existe; los recursos se crearán en $Region"
} else {
    Ejecutar "crear grupo de recursos" { az group create --name $Grupo --location $Region --tags proyecto=sisalert ambiente=demo -o none }
    Ok "Creado en $Region"
}

# ---------------------------------------------------------------------------
Paso "Secretos"
$secretos = @{}
$nombres = "db-admin-password", "alertas-db-password", "identidad-db-password", "jwt-key", "admin-password", "ai-api-key"
$kvExistente = az keyvault list --resource-group $Grupo --query "[0].name" -o tsv 2>$null
$completos = $false
if ($kvExistente) {
    $completos = $true
    foreach ($n in $nombres) {
        $valor = az keyvault secret show --vault-name $kvExistente --name $n --query value -o tsv 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $valor) { $completos = $false; break }
        $secretos[$n] = $valor
    }
}
if ($completos) {
    if (-not $GeminiApiKey) {
        $g = az keyvault secret show --vault-name $kvExistente --name "gemini-api-key" --query value -o tsv 2>$null
        if ($LASTEXITCODE -eq 0 -and $g) { $GeminiApiKey = $g }
    }
    Ok "Reutilizando los secretos de $kvExistente"
} else {
    if ($kvExistente) {
        # Pasa si un intento anterior se cortó a mitad o si aún no había permiso de lectura.
        # Se generan nuevos: Bicep los guarda en Key Vault y el paso de la base sincroniza las
        # contraseñas de alertas_app e identidad_app, así que nada queda desalineado.
        Write-Host "    No se pudieron leer los secretos de $kvExistente (intento anterior incompleto o sin permiso); se generan nuevos." -ForegroundColor Yellow
    }
    $secretos = @{}
    $secretos["db-admin-password"] = "Pg-" + (Nuevo-Secreto)
    $secretos["alertas-db-password"] = Nuevo-Secreto
    $secretos["identidad-db-password"] = Nuevo-Secreto
    $secretos["jwt-key"] = Nuevo-Secreto 48
    $secretos["admin-password"] = "Adm-" + (Nuevo-Secreto 9)
    $secretos["ai-api-key"] = Nuevo-Secreto
    Ok "Generados al azar (se guardarán en Key Vault)"
}

# Parámetros en un archivo temporal (no en la línea de comandos) que se borra al terminar.
$archivoParametros = Join-Path $env:TEMP ("sisalert-param-" + [guid]::NewGuid().ToString() + ".json")
$parametros = @{
    '$schema'      = "https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#"
    contentVersion = "1.0.0.0"
    parameters     = @{
        location            = @{ value = $Region }
        imageTag            = @{ value = $etiqueta }
        deployerPrincipalId = @{ value = $usuarioId }
        replicasMinimas     = @{ value = $ReplicasMinimas }
        postgresLocation    = @{ value = $PostgresRegion }
        postgresNombre      = @{ value = $PostgresNombre }
        postgresSku         = @{ value = $PostgresSku }
        postgresTier        = @{ value = $PostgresTier }
        dbAdminPassword     = @{ value = $secretos["db-admin-password"] }
        alertasDbPassword   = @{ value = $secretos["alertas-db-password"] }
        identidadDbPassword = @{ value = $secretos["identidad-db-password"] }
        jwtKey              = @{ value = $secretos["jwt-key"] }
        adminPassword       = @{ value = $secretos["admin-password"] }
        aiApiKey            = @{ value = $secretos["ai-api-key"] }
        geminiApiKey        = @{ value = $GeminiApiKey }
    }
}
# UTF-8 sin BOM: az no acepta el BOM que agrega Set-Content en Windows PowerShell 5.1.
[IO.File]::WriteAllText($archivoParametros, ($parametros | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding $false))

function Desplegar-Bicep([string]$fase, [bool]$conApps) {
    $resultado = az deployment group create --resource-group $Grupo --name "sisalert-$fase" `
        --template-file $plantilla --parameters "@$archivoParametros" --parameters desplegarApps=$($conApps.ToString().ToLower()) `
        --query properties.outputs -o json
    if ($LASTEXITCODE -ne 0) {
        # Error más común en suscripciones de estudiante: la región no está permitida.
        $permitidas = az policy assignment list --query "[].parameters.listOfAllowedLocations.value" -o tsv 2>$null
        if ($permitidas) {
            $lista = (($permitidas -split "\s+") | Where-Object { $_ } | Sort-Object -Unique) -join ", "
            Write-Host "`n    Si el error dice 'RequestDisallowedByAzure' o 'RequestDisallowedByPolicy', la región $Region" -ForegroundColor Yellow
            Write-Host "    no está permitida. Regiones permitidas en esta suscripción: $lista" -ForegroundColor Yellow
            Write-Host "    Vuelva a ejecutar con una de ellas, por ejemplo: .\infra\azure\desplegar.ps1 -Region <region>" -ForegroundColor Yellow
        }
        Write-Host "    Si el error dice 'SkuNotAvailable' en PostgreSQL, ese tamaño no está disponible en la región." -ForegroundColor Yellow
        Write-Host "    Ver los disponibles:  az postgres flexible-server list-skus --location <region> -o table" -ForegroundColor Yellow
        Write-Host "    Y repetir con:        -PostgresRegion <region> o -PostgresSku <tamaño>" -ForegroundColor Yellow
        throw "Falló el despliegue de Bicep ($fase). Revise el mensaje de Azure arriba."
    }
    return ($resultado | Out-String | ConvertFrom-Json)
}

$reglaTemporal = $null
try {
try {
    # -----------------------------------------------------------------------
    Paso "Fase 1: infraestructura (la primera vez tarda 5 a 10 minutos, sobre todo PostgreSQL)"
    $salidas = Desplegar-Bicep "infraestructura" $false
    $acr = $salidas.acrNombre.value
    $servidorAcr = $salidas.acrServidor.value
    $pgHost = $salidas.postgresServidor.value
    $pgNombre = $salidas.postgresNombre.value
    Ok "Registro: $servidorAcr"
    Ok "PostgreSQL: $pgHost"
    Ok "Key Vault: $($salidas.keyVaultNombre.value)"

    # -----------------------------------------------------------------------
    Paso "Imágenes Docker (la primera vez tarda varios minutos)"
    Ejecutar "iniciar sesión en el registro" { az acr login --name $acr }
    $imagenes = [ordered]@{
        "sisalert-auth"     = "auth-service"
        "sisalert-ai"       = "ai-service"
        "sisalert-alertas"  = "alertas-service"
        "sisalert-frontend" = "frontend"
    }
    foreach ($imagen in $imagenes.Keys) {
        $destino = "$servidorAcr/${imagen}:$etiqueta"
        Write-Host "    construyendo $destino"
        Ejecutar "construir $imagen" { docker build --quiet -t $destino (Join-Path $raiz $imagenes[$imagen]) }
        Ejecutar "subir $imagen" { docker push --quiet $destino }
        Ok "$imagen subida"
    }

    # -----------------------------------------------------------------------
    Paso "Usuarios de la base por servicio"
    # Se abre la base solo para la IP de este equipo mientras se crean los usuarios.
    $miIp = (Invoke-RestMethod -Uri "https://api.ipify.org" -TimeoutSec 15 -ErrorAction Stop).ToString().Trim()
    $reglaTemporal = "despliegue-temporal"
    Ejecutar "abrir firewall temporal" {
        # Sintaxis actual de Azure CLI: --server-name = servidor, --name = regla.
        az postgres flexible-server firewall-rule create --resource-group $Grupo --server-name $pgNombre `
            --name $reglaTemporal --start-ip-address $miIp --end-ip-address $miIp -o none
    }
    $conexion = "host=$pgHost port=5432 dbname=sisalert user=sisadmin sslmode=require"
    $existentes = $null
    for ($intento = 1; $intento -le 6; $intento++) {
        $existentes = docker run --rm -e "PGPASSWORD=$($secretos['db-admin-password'])" postgres:16-alpine `
            psql $conexion -tAc "select count(*) from pg_roles where rolname in ('alertas_app','identidad_app')" 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        Write-Host "    esperando a que la base acepte la conexión ($intento/6)..."
        Start-Sleep -Seconds 10
    }
    if ($LASTEXITCODE -ne 0) { throw "No se pudo conectar a PostgreSQL desde este equipo." }
    if ("$existentes".Trim() -eq "2") {
        # Ya existen: se les vuelve a poner la contraseña de Key Vault, por si los secretos se
        # regeneraron en esta ejecución (la base y los servicios quedan siempre alineados).
        $sincronizar = "ALTER ROLE alertas_app PASSWORD :'alertas_pass'; ALTER ROLE identidad_app PASSWORD :'identidad_pass';"
        $sincronizar | docker run -i --rm -e "PGPASSWORD=$($secretos['db-admin-password'])" postgres:16-alpine `
            psql $conexion -v ON_ERROR_STOP=1 `
            -v "alertas_pass=$($secretos['alertas-db-password'])" `
            -v "identidad_pass=$($secretos['identidad-db-password'])" `
            -q -f -
        if ($LASTEXITCODE -ne 0) { throw "No se pudieron sincronizar las contraseñas de alertas_app e identidad_app." }
        Ok "Los usuarios alertas_app e identidad_app ya existen; contraseñas sincronizadas con Key Vault"
    } else {
        Ejecutar "crear usuarios de la base" {
            docker run --rm -e "PGPASSWORD=$($secretos['db-admin-password'])" -v "$(Join-Path $raiz 'infra\db'):/sql:ro" postgres:16-alpine `
                psql $conexion -v ON_ERROR_STOP=1 `
                -v "alertas_pass=$($secretos['alertas-db-password'])" `
                -v "identidad_pass=$($secretos['identidad-db-password'])" `
                -f /sql/usuarios-por-servicio.sql
        }
        Ok "Usuarios creados (cada uno con permisos solo sobre su esquema)"
    }
} finally {
    if ($reglaTemporal) {
        $existe = az postgres flexible-server firewall-rule show --resource-group $Grupo --server-name $pgNombre `
            --name $reglaTemporal --query name -o tsv 2>$null
        if ($LASTEXITCODE -eq 0 -and $existe) {
            az postgres flexible-server firewall-rule delete --resource-group $Grupo --server-name $pgNombre `
                --name $reglaTemporal --yes -o none 2>$null
            if ($LASTEXITCODE -eq 0) {
                Ok "Firewall temporal cerrado"
            } else {
                Write-Host "    No se pudo cerrar la regla '$reglaTemporal'. Bórrela en el portal: servidor PostgreSQL > Redes." -ForegroundColor Yellow
            }
        }
    }
}

    # -----------------------------------------------------------------------
    Paso "Fase 2: Container Apps (frontend público; identidad, alertas e IA internos)"
    $salidas = Desplegar-Bicep "aplicaciones" $true
    $url = $salidas.urlPublica.value
    Ok "Desplegado"
} finally {
    # El archivo de parámetros tiene secretos: se borra pase lo que pase.
    Remove-Item $archivoParametros -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------------------
Paso "Comprobando que todo responda (los servicios tardan 1 a 3 minutos en arrancar)"
$comprobaciones = [ordered]@{
    "frontend"        = "$url/health"
    "identidad"       = "$url/auth-api/health/ready"
    "alertas"         = "$url/alertas-api/actuator/health/readiness"
}
foreach ($servicio in $comprobaciones.Keys) {
    $listo = $false
    for ($i = 0; $i -lt 30 -and -not $listo; $i++) {
        try {
            $r = Invoke-WebRequest -Uri $comprobaciones[$servicio] -UseBasicParsing -TimeoutSec 15
            if ($r.StatusCode -eq 200) { $listo = $true }
        } catch { Start-Sleep -Seconds 10 }
    }
    if ($listo) { Ok "$servicio responde" } else { Write-Host "    $servicio todavía no responde; revise los logs en el portal (Container App > Supervisión > Secuencia de registro)." -ForegroundColor Yellow }
}

Write-Host ""
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host " SisAlert desplegado: $url" -ForegroundColor Green
Write-Host "   Usuario: admin" -ForegroundColor Green
Write-Host "   Contraseña: está en Key Vault, secreto 'admin-password'. Para verla:" -ForegroundColor Green
Write-Host "   az keyvault secret show --vault-name $($salidas.keyVaultNombre.value) --name admin-password --query value -o tsv" -ForegroundColor Green
Write-Host ""
Write-Host " Para ahorrar crédito cuando no se use:  .\infra\azure\pausar.ps1" -ForegroundColor Green
Write-Host " Para encenderlo de nuevo:               .\infra\azure\pausar.ps1 -Encender" -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
