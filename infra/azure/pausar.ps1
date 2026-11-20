<#
.SYNOPSIS
    Pausa o enciende SisAlert en Azure para ahorrar crédito.

.DESCRIPTION
    Pausar: todos los Container Apps a 0 réplicas y el servidor PostgreSQL detenido.
            (No se borra nada: datos, imágenes y secretos se conservan. Azure vuelve a
            encender PostgreSQL solo después de 7 días detenido.)
    Encender: arranca PostgreSQL y deja alertas-service con 1 réplica (el monitoreo).
              Con -Demo, además mantiene encendidos frontend, identidad e IA (sin espera
              al primer acceso): úselo el día de la sustentación.

.EXAMPLE
    .\infra\azure\pausar.ps1
.EXAMPLE
    .\infra\azure\pausar.ps1 -Encender -Demo
#>
[CmdletBinding()]
param(
    [string]$Grupo = "rg-sisalert",
    [switch]$Encender,
    [switch]$Demo
)

# "Continue": con "Stop", los avisos de az en stderr detendrían el script en PowerShell 5.1.
$ErrorActionPreference = "Continue"
$pg = az postgres flexible-server list --resource-group $Grupo --query "[0].name" -o tsv
if ($LASTEXITCODE -ne 0 -or -not $pg) { throw "No se encontró el servidor PostgreSQL en $Grupo." }
$apps = "ca-frontend", "ca-auth", "ca-ai", "ca-alertas"

function Replicas([string]$app, [int]$minimo, [int]$maximo) {
    az containerapp update --resource-group $Grupo --name $app --min-replicas $minimo --max-replicas $maximo -o none
    if ($LASTEXITCODE -ne 0) { throw "No se pudo cambiar las réplicas de $app." }
    Write-Host "    $app -> mínimo $minimo réplica(s)"
}

if ($Encender) {
    Write-Host "==> Encendiendo PostgreSQL (tarda 2 a 5 minutos)" -ForegroundColor Cyan
    az postgres flexible-server start --resource-group $Grupo --name $pg -o none
    Write-Host "==> Encendiendo los servicios" -ForegroundColor Cyan
    $minimo = 0
    if ($Demo) { $minimo = 1 }
    Replicas "ca-alertas" 1 1
    Replicas "ca-auth" $minimo 3
    Replicas "ca-ai" $minimo 2
    Replicas "ca-frontend" $minimo 3
    Write-Host "Listo. Espere 1 a 3 minutos antes de abrir la URL." -ForegroundColor Green
} else {
    Write-Host "==> Apagando los servicios" -ForegroundColor Cyan
    foreach ($app in $apps) { Replicas $app 0 1 }
    Write-Host "==> Deteniendo PostgreSQL" -ForegroundColor Cyan
    az postgres flexible-server stop --resource-group $Grupo --name $pg -o none
    Write-Host "Pausado. Para volver a usarlo: .\infra\azure\pausar.ps1 -Encender" -ForegroundColor Green
}
