// =============================================================================
// SisAlert · Infraestructura en Azure (infraestructura como código, Bicep)
// =============================================================================
// Crea todo lo de docs/arquitectura-nube.md en un grupo de recursos:
//   Log Analytics · Container Registry · identidad administrada · Key Vault (secretos)
//   PostgreSQL Flexible Server (SSL) · Container Apps Environment · 4 Container Apps
//
// Se despliega en dos fases (lo hace infra/azure/desplegar.ps1):
//   1. desplegarApps=false -> infraestructura base (aún no hay imágenes en el registro)
//   2. desplegarApps=true  -> los 4 Container Apps con las imágenes ya subidas
// =============================================================================

@description('Región de Azure. Las suscripciones de estudiante solo admiten algunas.')
param location string = resourceGroup().location

@description('Etiqueta de las imágenes en el registro (el script usa el commit de Git).')
param imageTag string = 'latest'

@description('false = solo infraestructura base; true = también los Container Apps.')
param desplegarApps bool = false

@description('Id del usuario que despliega: recibe permiso para leer los secretos (reejecuciones del script).')
param deployerPrincipalId string = ''

@description('Réplicas mínimas de frontend, identidad e IA. 0 = se apagan sin tráfico (más barato; el primer acceso tarda unos segundos). 1 = siempre encendidos (día de la sustentación).')
@minValue(0)
@maxValue(1)
param replicasMinimas int = 0

@description('Región de PostgreSQL si debe ser distinta (vacío = la misma que el resto). Útil cuando el tamaño elegido no está disponible en la región principal.')
param postgresLocation string = ''

@description('Nombre del servidor PostgreSQL (vacío = pg-sisalert-<sufijo>). Sirve para esquivar un nombre bloqueado por un intento fallido en otra región.')
@maxLength(63)
param postgresNombre string = ''

@description('Tamaño de PostgreSQL. Standard_B1ms es el más barato; en algunas suscripciones de estudiante no está disponible en todas las regiones (ver: az postgres flexible-server list-skus --location <region> -o table).')
param postgresSku string = 'Standard_B1ms'

@description('Nivel del tamaño anterior: Burstable (B1ms, B2s...), GeneralPurpose (D...) o MemoryOptimized (E...).')
@allowed([ 'Burstable', 'GeneralPurpose', 'MemoryOptimized' ])
param postgresTier string = 'Burstable'

@secure()
param dbAdminPassword string
@secure()
param alertasDbPassword string
@secure()
param identidadDbPassword string
@secure()
@minLength(32)
param jwtKey string
@secure()
param adminPassword string
@secure()
param aiApiKey string
@secure()
@description('Opcional: sin clave, los boletines salen de la plantilla.')
param geminiApiKey string = ''

var sufijo = take(uniqueString(resourceGroup().id), 8)
var etiquetas = {
  proyecto: 'sisalert'
  ambiente: 'demo'
}
var dbAdmin = 'sisadmin'
var conGemini = !empty(geminiApiKey)

// IDs de roles integrados de Azure
var rolAcrPull = '7f951dda-4ed3-4680-a7ca-43fe172d538d'
var rolKvSecretsUser = '4633458b-17de-408a-b874-0445c86b69e6'
var rolKvSecretsOfficer = 'b86a8fe4-44ce-4948-aee5-eccb2c155cd7'

// ---------------------------------------------------------------------------
// Observabilidad
// ---------------------------------------------------------------------------
resource logs 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: 'log-sisalert-${sufijo}'
  location: location
  tags: etiquetas
  properties: {
    sku: { name: 'PerGB2018' }
    retentionInDays: 30
  }
}

// ---------------------------------------------------------------------------
// Registro de imágenes e identidad con la que los contenedores lo leen
// ---------------------------------------------------------------------------
resource acr 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: 'acrsisalert${sufijo}'
  location: location
  tags: etiquetas
  sku: { name: 'Basic' }
  properties: {
    adminUserEnabled: false // los contenedores entran con la identidad administrada, sin contraseña
  }
}

resource identidad 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: 'id-sisalert-${sufijo}'
  location: location
  tags: etiquetas
}

resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(acr.id, identidad.id, rolAcrPull)
  scope: acr
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', rolAcrPull)
    principalId: identidad.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

// ---------------------------------------------------------------------------
// Base de datos: PostgreSQL Flexible Server (SSL obligatorio por defecto en Azure)
// ---------------------------------------------------------------------------
resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: empty(postgresNombre) ? 'pg-sisalert-${sufijo}' : postgresNombre
  location: empty(postgresLocation) ? location : postgresLocation
  tags: etiquetas
  sku: {
    name: postgresSku
    tier: postgresTier
  }
  properties: {
    version: '16'
    administratorLogin: dbAdmin
    administratorLoginPassword: dbAdminPassword
    storage: { storageSizeGB: 32 }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: { mode: 'Disabled' }
    network: { publicNetworkAccess: 'Enabled' }
  }
}

resource baseSisalert 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
  parent: postgres
  name: 'sisalert'
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}

// Solo servicios de Azure (los Container Apps). El script abre temporalmente la IP de quien
// despliega para crear los usuarios de la base, y la cierra al terminar.
resource firewallAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: postgres
  name: 'PermitirServiciosDeAzure'
  properties: {
    startIpAddress: '0.0.0.0'
    endIpAddress: '0.0.0.0'
  }
}

// ---------------------------------------------------------------------------
// Secretos: Key Vault (autorización por roles)
// ---------------------------------------------------------------------------
resource kv 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: 'kv-sisalert-${sufijo}'
  location: location
  tags: etiquetas
  properties: {
    tenantId: subscription().tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
  }
}

resource kvLectura 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(kv.id, identidad.id, rolKvSecretsUser)
  scope: kv
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', rolKvSecretsUser)
    principalId: identidad.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

resource kvDesplegador 'Microsoft.Authorization/roleAssignments@2022-04-01' = if (!empty(deployerPrincipalId)) {
  name: guid(kv.id, deployerPrincipalId, rolKvSecretsOfficer)
  scope: kv
  properties: {
    roleDefinitionId: subscriptionResourceId('Microsoft.Authorization/roleDefinitions', rolKvSecretsOfficer)
    principalId: deployerPrincipalId
    principalType: 'User'
  }
}

var cadenaIdentidad = 'Host=${postgres.properties.fullyQualifiedDomainName};Port=5432;Database=sisalert;Username=identidad_app;Password=${identidadDbPassword};SSL Mode=Require;Maximum Pool Size=10'

// El bucle recorre los NOMBRES (conocidos al compilar) y toma cada valor del objeto.
var valoresSecretos = {
  'db-admin-password': dbAdminPassword
  'alertas-db-password': alertasDbPassword
  'identidad-db-password': identidadDbPassword
  'identidad-connection': cadenaIdentidad
  'jwt-key': jwtKey
  'admin-password': adminPassword
  'ai-api-key': aiApiKey
  'gemini-api-key': geminiApiKey
}
var nombresBase = [
  'db-admin-password'
  'alertas-db-password'
  'identidad-db-password'
  'identidad-connection'
  'jwt-key'
  'admin-password'
  'ai-api-key'
]
var nombresSecretos = conGemini ? concat(nombresBase, [ 'gemini-api-key' ]) : nombresBase

resource kvSecretos 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = [for nombre in nombresSecretos: {
  parent: kv
  name: nombre
  properties: { value: valoresSecretos[nombre] }
}]

// ---------------------------------------------------------------------------
// Container Apps Environment
// ---------------------------------------------------------------------------
resource entorno 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: 'cae-sisalert-${sufijo}'
  location: location
  tags: etiquetas
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logs.properties.customerId
        sharedKey: logs.listKeys().primarySharedKey
      }
    }
  }
}

// Nombres y direcciones predecibles (evitan dependencias circulares entre apps).
var dominio = entorno.properties.defaultDomain
var urlFrontend = 'https://ca-frontend.${dominio}'
var urlAuthInterna = 'https://ca-auth.internal.${dominio}'
var urlAlertasInterna = 'https://ca-alertas.internal.${dominio}'
var urlIaInterna = 'https://ca-ai.internal.${dominio}'

var registro = [
  {
    server: acr.properties.loginServer
    identity: identidad.id
  }
]
var identidadApps = {
  type: 'UserAssigned'
  userAssignedIdentities: { '${identidad.id}': {} }
}

// Referencia a un secreto de Key Vault (el contenedor lo lee con la identidad administrada).
func secretoKv(nombreApp string, vaultUri string, idIdentidad string) object => {
  name: nombreApp
  keyVaultUrl: '${vaultUri}secrets/${nombreApp}'
  identity: idIdentidad
}

// Sondas HTTP para Container Apps.
func sondas(puerto int, rutaVida string, rutaListo string) array => [
  {
    type: 'Startup'
    httpGet: { path: rutaVida, port: puerto }
    initialDelaySeconds: 15
    periodSeconds: 10
    failureThreshold: 10 // hasta unos 2 minutos para arrancar (migraciones, carga del modelo)
  }
  {
    type: 'Liveness'
    httpGet: { path: rutaVida, port: puerto }
    periodSeconds: 30
    failureThreshold: 3
  }
  {
    type: 'Readiness'
    httpGet: { path: rutaListo, port: puerto }
    periodSeconds: 15
    timeoutSeconds: 5
    failureThreshold: 3
  }
]

// ---------------------------------------------------------------------------
// API de identidad (.NET) · interna
// ---------------------------------------------------------------------------
resource appAuth 'Microsoft.App/containerApps@2024-03-01' = if (desplegarApps) {
  name: 'ca-auth'
  location: location
  tags: etiquetas
  identity: identidadApps
  dependsOn: [ acrPull, kvLectura, kvSecretos, baseSisalert ]
  properties: {
    managedEnvironmentId: entorno.id
    configuration: {
      ingress: {
        external: false
        targetPort: 8080
        transport: 'auto'
      }
      registries: registro
      secrets: [
        secretoKv('identidad-connection', kv.properties.vaultUri, identidad.id)
        secretoKv('jwt-key', kv.properties.vaultUri, identidad.id)
        secretoKv('admin-password', kv.properties.vaultUri, identidad.id)
      ]
    }
    template: {
      containers: [
        {
          name: 'auth'
          image: '${acr.properties.loginServer}/sisalert-auth:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          env: [
            { name: 'ASPNETCORE_ENVIRONMENT', value: 'Production' }
            { name: 'ConnectionStrings__Identity', secretRef: 'identidad-connection' }
            { name: 'Jwt__Key', secretRef: 'jwt-key' }
            { name: 'Seed__AdminPassword', secretRef: 'admin-password' }
            { name: 'Persistencia__EsquemaHistorial', value: 'identidad' }
            { name: 'Proxy__SaltosConfiables', value: '2' }
          ]
          probes: sondas(8080, '/health', '/health/ready')
        }
      ]
      scale: { minReplicas: replicasMinimas, maxReplicas: 3 }
    }
  }
}

// ---------------------------------------------------------------------------
// Servicio de IA (FastAPI) · interno
// ---------------------------------------------------------------------------
var envIa = concat([
  { name: 'AI_API_KEY', secretRef: 'ai-api-key' }
], conGemini ? [ { name: 'GEMINI_API_KEY', secretRef: 'gemini-api-key' } ] : [])
var secretosIa = concat([
  secretoKv('ai-api-key', kv.properties.vaultUri, identidad.id)
], conGemini ? [ secretoKv('gemini-api-key', kv.properties.vaultUri, identidad.id) ] : [])

resource appIa 'Microsoft.App/containerApps@2024-03-01' = if (desplegarApps) {
  name: 'ca-ai'
  location: location
  tags: etiquetas
  identity: identidadApps
  dependsOn: [ acrPull, kvLectura, kvSecretos ]
  properties: {
    managedEnvironmentId: entorno.id
    configuration: {
      ingress: {
        external: false
        targetPort: 8000
        transport: 'auto'
      }
      registries: registro
      secrets: secretosIa
    }
    template: {
      containers: [
        {
          name: 'ai'
          image: '${acr.properties.loginServer}/sisalert-ai:${imageTag}'
          resources: { cpu: json('0.5'), memory: '1Gi' }
          env: envIa
          probes: sondas(8000, '/health', '/health')
        }
      ]
      scale: { minReplicas: replicasMinimas, maxReplicas: 2 }
    }
  }
}

// ---------------------------------------------------------------------------
// Microservicio de alertas (Spring Boot) · interno · 1 réplica fija (monitoreo programado)
// ---------------------------------------------------------------------------
resource appAlertas 'Microsoft.App/containerApps@2024-03-01' = if (desplegarApps) {
  name: 'ca-alertas'
  location: location
  tags: etiquetas
  identity: identidadApps
  dependsOn: [ acrPull, kvLectura, kvSecretos, baseSisalert ]
  properties: {
    managedEnvironmentId: entorno.id
    configuration: {
      ingress: {
        external: false
        targetPort: 8081
        transport: 'auto'
      }
      registries: registro
      secrets: [
        secretoKv('alertas-db-password', kv.properties.vaultUri, identidad.id)
        secretoKv('jwt-key', kv.properties.vaultUri, identidad.id)
        secretoKv('ai-api-key', kv.properties.vaultUri, identidad.id)
      ]
    }
    template: {
      containers: [
        {
          name: 'alertas'
          image: '${acr.properties.loginServer}/sisalert-alertas:${imageTag}'
          resources: { cpu: json('0.75'), memory: '1.5Gi' }
          env: [
            { name: 'SPRING_PROFILES_ACTIVE', value: 'prod' }
            { name: 'DB_URL', value: 'jdbc:postgresql://${postgres.properties.fullyQualifiedDomainName}:5432/sisalert?sslmode=require' }
            { name: 'DB_USER', value: 'alertas_app' }
            { name: 'DB_PASSWORD', secretRef: 'alertas-db-password' }
            { name: 'Jwt__Key', secretRef: 'jwt-key' }
            { name: 'ALERTAS_CORS_ORIGENES', value: urlFrontend }
            { name: 'AI_SERVICE_URL', value: urlIaInterna }
            { name: 'AI_API_KEY', secretRef: 'ai-api-key' }
          ]
          probes: sondas(8081, '/actuator/health/liveness', '/actuator/health/readiness')
        }
      ]
      // Siempre 1: el monitoreo automático vive en el proceso; con 2 réplicas sincronizaría doble.
      scale: { minReplicas: 1, maxReplicas: 1 }
    }
  }
}

// ---------------------------------------------------------------------------
// Frontend (nginx) · ÚNICO servicio público
// ---------------------------------------------------------------------------
resource appFrontend 'Microsoft.App/containerApps@2024-03-01' = if (desplegarApps) {
  name: 'ca-frontend'
  location: location
  tags: etiquetas
  identity: identidadApps
  dependsOn: [ acrPull ]
  properties: {
    managedEnvironmentId: entorno.id
    configuration: {
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false // HTTP se redirige a HTTPS
      }
      registries: registro
    }
    template: {
      containers: [
        {
          name: 'frontend'
          image: '${acr.properties.loginServer}/sisalert-frontend:${imageTag}'
          resources: { cpu: json('0.25'), memory: '0.5Gi' }
          env: [
            { name: 'PORT', value: '8080' }
            { name: 'AUTH_API_URL', value: urlAuthInterna }
            { name: 'ALERTAS_API_URL', value: urlAlertasInterna }
          ]
          probes: sondas(8080, '/health', '/health')
        }
      ]
      scale: { minReplicas: replicasMinimas, maxReplicas: 3 }
    }
  }
}

output acrNombre string = acr.name
output acrServidor string = acr.properties.loginServer
output postgresServidor string = postgres.properties.fullyQualifiedDomainName
output postgresNombre string = postgres.name
output keyVaultNombre string = kv.name
output urlPublica string = urlFrontend
