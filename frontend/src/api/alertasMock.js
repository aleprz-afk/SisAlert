// Datos de demostración con el MISMO contrato que alertas-service (ver alertasApi.js),
// para ensayar el frontend cuando el servicio no está corriendo.
// Replica las zonas de CargaInicial.java, el ClienteSimulado (lluvia aleatoria 0–90 mm)
// y la ReglaDeUmbral con los umbrales de application.properties (30 y 60 mm).

const UMBRAL_PRECAUCION_MM = 30
const UMBRAL_PELIGRO_MM = 60

const MENSAJES = {
  NORMAL: 'Sin riesgo por lluvias en este momento.',
  PRECAUCION: 'Lluvia acumulada relevante. Manténgase atento a la evolución del río.',
  PELIGRO: 'Lluvia intensa. Prepárese para una posible evacuación.',
}

const ZONAS = [
  { id: 1, nombre: 'Zona Guatiquía', cuerpoAgua: 'Río Guatiquía', comunidades: 'Por confirmar' },
  { id: 2, nombre: 'Zona Caño Parrado', cuerpoAgua: 'Caño Parrado', comunidades: 'Por confirmar' },
  { id: 3, nombre: 'Zona Caño Maizaro', cuerpoAgua: 'Caño Maizaro', comunidades: 'Por confirmar' },
  { id: 4, nombre: 'Zona Río Ocoa', cuerpoAgua: 'Río Ocoa', comunidades: 'Por confirmar' },
]

// zonaId → { nivel, mensaje, precipitacionMm, fechaHora }
const vigentes = new Map()

function evaluar(mm) {
  if (mm >= UMBRAL_PELIGRO_MM) return 'PELIGRO'
  if (mm >= UMBRAL_PRECAUCION_MM) return 'PRECAUCION'
  return 'NORMAL'
}

// Igual que un LocalDateTime de Java serializado por Jackson (sin zona horaria).
function ahoraLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const copia = (valor) => JSON.parse(JSON.stringify(valor))

export function listarZonas() {
  return Promise.resolve(copia(ZONAS))
}

export function listarAlertas() {
  const lista = ZONAS.map((z) => {
    const a = vigentes.get(z.id)
    return {
      zonaId: z.id,
      zona: z.nombre,
      cuerpoAgua: z.cuerpoAgua,
      comunidades: z.comunidades,
      nivel: a?.nivel ?? null,
      mensaje: a?.mensaje ?? null,
      precipitacionMm: a?.precipitacionMm ?? null,
      fechaHora: a?.fechaHora ?? null,
    }
  })
  return Promise.resolve(lista)
}

export function sincronizar() {
  const fechaHora = ahoraLocal()
  for (const z of ZONAS) {
    const mm = Math.round(Math.random() * 900) / 10
    const nivel = evaluar(mm)
    vigentes.set(z.id, { nivel, mensaje: MENSAJES[nivel], precipitacionMm: mm, fechaHora })
  }
  return listarAlertas()
}