// Consumo del alertas-service (Spring Boot) con el contrato que expone hoy:
//
//   GET  /api/zonas                → [{ id, nombre, cuerpoAgua, comunidades }]
//   GET  /api/alertas              → una entrada por zona con su alerta vigente
//   POST /api/lecturas/sincronizar → consulta la fuente, guarda lecturas, recalcula
//                                    las alertas y devuelve la misma lista de /api/alertas
//
// Cada entrada de /api/alertas (AlertaDto):
//   { zonaId, zona, cuerpoAgua, comunidades,
//     nivel: 'NORMAL' | 'PRECAUCION' | 'PELIGRO' | null,
//     mensaje, precipitacionMm, fechaHora }
// nivel/mensaje/precipitacionMm/fechaHora van en null mientras no se haya sincronizado.
//
// Todas las llamadas pasan por apiFetch, que agrega Authorization: Bearer <token>
// (el servicio lee el usuario del token para registrar quién sincronizó).
//
// Modo demostración (alertasMock.js), controlado con VITE_MOCK_ALERTAS:
//   - 'true'  → siempre datos de demostración
//   - 'false' → siempre el servicio real (si no responde, el error se muestra)
//   - sin definir → automático: solo cae al mock si el servicio NO está corriendo.
//     Si el servicio responde un 502 propio (falló la fuente externa), se muestra el
//     error en pantalla en lugar de taparlo con datos de demostración.
//   - Mientras esté en el mock se reintenta el servicio cada 15 segundos y se vuelve
//     a datos reales solo, sin necesidad de pulsar "Reintentar".

import { apiFetch } from './apiClient'
import * as mock from './alertasMock'

const BASE = '/alertas-api/api'

const CONFIGURADO = import.meta.env?.VITE_MOCK_ALERTAS

export const mockForzado = CONFIGURADO === 'true'

let usandoMock = mockForzado
let avisoMostrado = false
let reintentoTimer = null

const RETRASO_REINTENTO_MS = 15000

function avisar(motivo) {
  if (avisoMostrado) return
  avisoMostrado = true
  console.warn(
    `[alertasApi] ${motivo}. Se usan datos de demostración; se reintenta solo cada ${RETRASO_REINTENTO_MS / 1000} s, o pulsa "Reintentar" para forzarlo.`,
  )
}

// Mientras se sirven datos de demostración se reintenta el servicio real en segundo
// plano. Si contesta con éxito, el sistema vuelve solo a datos reales: el chip se
// oculta en el siguiente refresco (5 s) de las pantallas, sin pulsar "Reintentar".
function programarReintentoAutomatico() {
  if (reintentoTimer || mockForzado || CONFIGURADO === 'false') return
  reintentoTimer = setTimeout(reintentarServicioReal, RETRASO_REINTENTO_MS)
}

async function reintentarServicioReal() {
  reintentoTimer = null
  if (!usandoMock || mockForzado) return
  try {
    await apiFetch(`${BASE}/alertas`)
    usandoMock = false
    avisoMostrado = false
    console.info('[alertasApi] alertas-service disponible de nuevo: se usan datos reales.')
  } catch {
    programarReintentoAutomatico()
  }
}

function servicioCaido(e) {
  if (e.status === undefined) return true
  if (e.status === 502) return !String(e.cuerpo ?? '').includes('FUENTE_EXTERNA')
  if (e.status === 500) {
    return !e.cuerpo || !String(e.cuerpo).includes('"status":500')
  }
  return [503, 504].includes(e.status)
}

async function ejecutar(llamarServicio, llamadaDemo) {
  if (usandoMock) return llamadaDemo()
  try {
    return await llamarServicio()
  } catch (e) {
    if (CONFIGURADO === 'false' || !servicioCaido(e)) throw e
    usandoMock = true
    avisar('El alertas-service no responde')
    programarReintentoAutomatico()
    return llamadaDemo()
  }
}

// Cómo se pinta cada nivel del backend en el semáforo.
export const NIVELES = {
  NORMAL: { color: 'verde', etiqueta: 'Normal', orden: 1 },
  PRECAUCION: { color: 'amarillo', etiqueta: 'Precaución', orden: 2 },
  PELIGRO: { color: 'rojo', etiqueta: 'Peligro', orden: 3 },
}
const SIN_DATOS = { color: 'sin-datos', etiqueta: 'Sin datos', orden: 0 }

// Agrega color/etiqueta/orden a cada entrada para que las pantallas no repitan la lógica.
export function normalizar(lista) {
  return (Array.isArray(lista) ? lista : []).map((a) => ({
    ...a,
    ...(NIVELES[a.nivel] ?? SIN_DATOS),
  }))
}

export const getAlertas = async () =>
  normalizar(
    await ejecutar(() => apiFetch(`${BASE}/alertas`), () => mock.listarAlertas()),
  )

// La operación de escritura del MVP: el servicio registra lecturas y recalcula alertas.
// Si el backend falla, no se enmascara con mock a menos que ya esté en modo demostración.
export const sincronizar = async () => {
  if (usandoMock) {
    return normalizar(await mock.sincronizar())
  }
  const datos = await apiFetch(`${BASE}/lecturas/sincronizar`, { method: 'POST' })
  return normalizar(datos)
}

export const enModoDemostracion = () => usandoMock

// Vuelve a intentar contra el servicio real (no hace nada si el mock está forzado).
export function reconectarServidor() {
  if (reintentoTimer) {
    clearTimeout(reintentoTimer)
    reintentoTimer = null
  }
  usandoMock = mockForzado
  avisoMostrado = false
}

// ---------------------------------------------------------------------------
// CRUD de zonas. Siempre va contra el alertas-service real: el modo demostración
// no gestiona zonas (sus datos viven solo en memoria en el navegador).
// ---------------------------------------------------------------------------

export const getZonas = () => apiFetch(`${BASE}/zonas`)

export const crearZona = (zona) =>
  apiFetch(`${BASE}/zonas`, {
    method: 'POST',
    body: JSON.stringify(zona),
  })

export const actualizarZona = (id, zona) =>
  apiFetch(`${BASE}/zonas/${id}`, {
    method: 'PUT',
    body: JSON.stringify(zona),
  })

export const eliminarZona = (id) =>
  apiFetch(`${BASE}/zonas/${id}`, { method: 'DELETE' })
