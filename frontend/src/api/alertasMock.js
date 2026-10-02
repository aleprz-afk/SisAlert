// Datos de demostración del Servicio de Alertas.
//
// Reproduce la "API REST del servicio de Alertas" del contrato de mensajes para que el
// frontend se pueda desarrollar y ensayar cuando alertas-service todavía no corre.
// Quién decide usar esto es alertasApi.js: en cuanto el servicio real responda, este
// archivo deja de usarse sin tocar ninguna pantalla.
//
// Lo que va acelerado es el nivel, no el reloj: una creciente completa se ve en poco
// más de un minuto mientras las marcas de tiempo son las reales del equipo.

const NIVEL_INICIAL = 2.45
const NIVEL_TOPE = 3.62
const CONSULTAS_EN_ROJO = 6 // cuántas consultas se sostiene el rojo antes de reiniciar

const ZONA = {
  zonaId: 'cano-buque-bajo',
  zonaNombre: 'Caño Buque, sector bajo',
  umbrales: { amarillo: 2.80, naranja: 3.20, rojo: 3.60 },
}

// Mismos claims que lee AuthContext para obtener el nombre del usuario.
const CLAIMS_USUARIO = [
  'unique_name',
  'name',
  'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name',
  'username',
  'email',
  'sub',
]

let simulacion = null

function iso(ms) {
  return new Date(ms).toISOString().replace(/\.\d{3}Z$/, 'Z')
}

function dosDecimales(valor) {
  return Math.round(valor * 100) / 100
}

function fechaId(ms) {
  const d = new Date(ms)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getUTCFullYear()}${p(d.getUTCMonth() + 1)}${p(d.getUTCDate())}`
}

function errorServicio(status, mensaje) {
  return Object.assign(new Error(mensaje), { status })
}

function nivelAlertaDe(nivel) {
  if (nivel >= ZONA.umbrales.rojo) return 'rojo'
  if (nivel >= ZONA.umbrales.naranja) return 'naranja'
  if (nivel >= ZONA.umbrales.amarillo) return 'amarillo'
  return 'verde'
}

// El servicio saca el usuario del token, nunca de lo que manda el cliente.
function usuarioDesdeToken() {
  try {
    const token = sessionStorage.getItem('token')
    if (!token) return 'desconocido'
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(atob(base64))
    for (const claim of CLAIMS_USUARIO) {
      if (payload?.[claim]) return payload[claim]
    }
    return 'desconocido'
  } catch {
    return 'desconocido'
  }
}

function alertaHistorica(ahora) {
  const emitidaEn = ahora - 45 * 60000
  return {
    alertaId: `ALT-${fechaId(emitidaEn)}-0001`,
    zonaId: ZONA.zonaId,
    nivel: 'amarillo',
    emitidaEn: iso(emitidaEn),
    nivelMetros: 2.83,
    umbralMetros: ZONA.umbrales.amarillo,
    mensaje: 'El nivel alcanzó 2.83 m y supera el umbral amarillo.',
    reconocidaPor: 'operador1',
    reconocidaEn: iso(emitidaEn + 60000),
  }
}

function nuevaSimulacion() {
  const ahora = Date.now()
  return {
    nivel: NIVEL_INICIAL,
    tendencia: 3.5, // m/h: el escenario arranca ya con la creciente en curso
    alertas: [alertaHistorica(ahora)],
    consecutivo: 2,
    esperandoReinicio: 0,
  }
}

function emitir(nivelAlerta, nivel, umbral) {
  const s = simulacion
  const ahora = Date.now()
  s.alertas.unshift({
    alertaId: `ALT-${fechaId(ahora)}-${String(s.consecutivo).padStart(4, '0')}`,
    zonaId: ZONA.zonaId,
    nivel: nivelAlerta,
    emitidaEn: iso(ahora),
    nivelMetros: dosDecimales(nivel),
    umbralMetros: umbral,
    mensaje: `El nivel alcanzó ${dosDecimales(nivel).toFixed(2)} m y supera el umbral ${nivelAlerta}.`,
    reconocidaPor: null,
    reconocidaEn: null,
  })
  s.consecutivo += 1
}

// Un paso de la simulación por consulta recibida (cada GET mueve la simulación).
function avanzar() {
  if (!simulacion) simulacion = nuevaSimulacion()
  const s = simulacion

  if (s.esperandoReinicio > 0) {
    s.esperandoReinicio -= 1
    if (s.esperandoReinicio === 0) s.nivel = NIVEL_INICIAL // vuelve a verde y el ciclo se repite
    return
  }

  const antes = s.nivel
  s.tendencia = dosDecimales(3.2 + Math.random() * 0.7)
  s.nivel = Math.min(antes + s.tendencia / 60, NIVEL_TOPE)

  for (const [nombre, umbral] of Object.entries(ZONA.umbrales)) {
    if (antes < umbral && s.nivel >= umbral) emitir(nombre, s.nivel, umbral)
  }

  if (nivelAlertaDe(s.nivel) === 'rojo') s.esperandoReinicio = CONSULTAS_EN_ROJO
}

function validarZona(zonaId) {
  if (zonaId !== ZONA.zonaId) throw errorServicio(404, 'Zona no encontrada')
}

export function obtenerEstadoZona(zonaId = ZONA.zonaId) {
  validarZona(zonaId)
  avanzar()
  const s = simulacion
  return {
    zonaId: ZONA.zonaId,
    zonaNombre: ZONA.zonaNombre,
    nivelMetros: dosDecimales(s.nivel),
    tendenciaMetrosHora: dosDecimales(s.tendencia),
    nivelAlerta: nivelAlertaDe(s.nivel),
    umbrales: { ...ZONA.umbrales },
    actualizadoEn: iso(Date.now()),
  }
}

export function listarAlertas(zonaId = ZONA.zonaId) {
  validarZona(zonaId)
  avanzar()
  return simulacion.alertas.map((alerta) => ({ ...alerta }))
}

export function reconocer(alertaId) {
  if (!simulacion) simulacion = nuevaSimulacion()
  const alerta = simulacion.alertas.find((a) => a.alertaId === alertaId)
  if (!alerta) throw errorServicio(404, 'Alerta no encontrada')
  if (alerta.reconocidaPor) {
    throw errorServicio(409, `La alerta ya fue reconocida por ${alerta.reconocidaPor}`)
  }
  alerta.reconocidaPor = usuarioDesdeToken()
  alerta.reconocidaEn = iso(Date.now())
  return {
    alertaId: alerta.alertaId,
    reconocidaPor: alerta.reconocidaPor,
    reconocidaEn: alerta.reconocidaEn,
  }
}
