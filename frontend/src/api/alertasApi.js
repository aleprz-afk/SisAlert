// Consumo del Servicio de Alertas (según la pestaña "Contrato de mensajes").
//
// Todas las llamadas pasan por apiFetch, que agrega el Authorization: Bearer <token>.
//
// Mientras el alertas-service no esté corriendo se usan los datos de demostración de
// alertasMock.js: si el servidor no responde (o devuelve 5xx) la llamada cae en el mock
// y se avisa en consola y en pantalla. Se puede controlar con la variable de entorno
// VITE_MOCK_ALERTAS:
//   - 'true'  → siempre datos de demostración
//   - 'false' → siempre el servicio real (si no responde, falla a la vista)
//   - sin definir (por defecto) → automático, el modo que se usa en la demo

import { apiFetch } from './apiClient'
import * as mock from './alertasMock'

const BASE = '/alertas-api/api'

export const ZONA_DEFECTO = 'cano-buque-bajo'

const CONFIGURADO = import.meta.env.VITE_MOCK_ALERTAS

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

async function ejecutar(llamarServicio, llamadaDemo) {
  if (usandoMock) return llamadaDemo()
  try {
    return await llamarServicio()
  } catch (e) {
    const sinServicio = e.status === undefined || e.status >= 500
    if (CONFIGURADO === 'false' || !sinServicio) throw e
    usandoMock = true
    avisar('El alertas-service no responde')
    return llamadaDemo()
  }
}

export const getEstadoZona = (zonaId = ZONA_DEFECTO) =>
  ejecutar(
    () => apiFetch(`${BASE}/zonas/${encodeURIComponent(zonaId)}/estado`),
    () => mock.obtenerEstadoZona(zonaId),
  )

export const getAlertas = (zonaId = ZONA_DEFECTO) =>
  ejecutar(
    () => apiFetch(`${BASE}/alertas?zonaId=${encodeURIComponent(zonaId)}`),
    () => mock.listarAlertas(zonaId),
  )

// La funcionalidad de negocio del MVP: sin cuerpo, el servicio lee el usuario del token.
export const reconocerAlerta = (alertaId) =>
  ejecutar(
    () => apiFetch(`${BASE}/alertas/${encodeURIComponent(alertaId)}/reconocer`, { method: 'POST' }),
    () => mock.reconocer(alertaId),
  )

export const enModoDemostracion = () => usandoMock

// Vuelve a intentar contra el servicio real (no hace nada si el mock está forzado).
export function reconectarServidor() {
  usandoMock = mockForzado
  avisoMostrado = false
}
