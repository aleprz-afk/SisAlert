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

import { apiFetch } from './apiClient'
import * as mock from './alertasMock'

const BASE = '/alertas-api/api'

const CONFIGURADO = import.meta.env?.VITE_MOCK_ALERTAS

export const mockForzado = CONFIGURADO === 'true'

let usandoMock = mockForzado
let avisoMostrado = false

function avisar(motivo) {
  if (avisoMostrado) return
  avisoMostrado = true
  console.warn(
    `[alertasApi] ${motivo}. Se usan datos de demostración; cuando el servicio esté corriendo, pulsa "Reintentar" o recarga la página.`,
  )
}

function servicioCaido(e) {
  if (e.status === undefined) return true
  if (e.status === 502) return !String(e.cuerpo ?? '').includes('FUENTE_EXTERNA')
  return [500, 503, 504].includes(e.status)
}

async function ejecutar(llamarServicio, llamadaDemo) {
  if (usandoMock) return llamadaDemo()
  try {
    return await llamarServicio()
  } catch (e) {
    if (CONFIGURADO === 'false' || !servicioCaido(e)) throw e
    usandoMock = true
    avisar('El alertas-service no responde')
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

export const getZonas = () =>
  ejecutar(() => apiFetch(`${BASE}/zonas`), () => mock.listarZonas())

export const getAlertas = async () =>
  normalizar(
    await ejecutar(() => apiFetch(`${BASE}/alertas`), () => mock.listarAlertas()),
  )

// La operación de escritura del MVP: el servicio registra lecturas y recalcula alertas.
export const sincronizar = async () =>
  normalizar(
    await ejecutar(
      () => apiFetch(`${BASE}/lecturas/sincronizar`, { method: 'POST' }),
      () => mock.sincronizar(),
    ),
  )

export const enModoDemostracion = () => usandoMock

// Vuelve a intentar contra el servicio real (no hace nada si el mock está forzado).
export function reconectarServidor() {
  usandoMock = mockForzado
  avisoMostrado = false
}