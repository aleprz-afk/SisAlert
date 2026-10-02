// Alertas activas: las zonas en precaución o peligro, la más grave primero.
// Usa el mismo GET /api/alertas del tablero, filtrado en el cliente.

import { useEffect, useState } from 'react'
import ChipModoDemo from '../components/ChipModoDemo'
import { getAlertas } from '../api/alertasApi'
import { fechaLocal, milimetros } from '../utils/formato'

const INTERVALO_MS = 5000

export default function AlertasPage() {
  const [zonas, setZonas] = useState([])
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [intento, setIntento] = useState(0)

  const refrescar = () => setIntento((i) => i + 1)

  useEffect(() => {
    let cancelado = false

    async function traer() {
      try {
        const datos = await getAlertas()
        if (!cancelado) {
          setZonas(datos)
          setError(null)
        }
      } catch (e) {
        if (!cancelado) setError(e.message)
      } finally {
        if (!cancelado) setCargando(false)
      }
    }

    traer()
    const id = setInterval(traer, INTERVALO_MS)
    return () => {
      cancelado = true
      clearInterval(id)
    }
  }, [intento])

  const activas = zonas
    .filter((z) => z.nivel === 'PRECAUCION' || z.nivel === 'PELIGRO')
    .sort((a, b) => b.orden - a.orden)

  const sinLecturas = zonas.length > 0 && zonas.every((z) => z.nivel == null)

  return (
    <section className="tablero">
      <header className="barra-superior">
        <div>
          <h2>Alertas activas</h2>
          <p className="vacio">Más grave primero · se actualiza cada 5 segundos</p>
        </div>
        <ChipModoDemo onReintentar={refrescar} />
      </header>

      {error && <p className="error">{error}</p>}

      {cargando && !zonas.length && <p className="vacio">Cargando alertas…</p>}

      {!cargando && !error && sinLecturas && (
        <p className="vacio">Todavía no hay lecturas. Sincroniza desde el tablero.</p>
      )}

      {!cargando && !error && !sinLecturas && zonas.length > 0 && !activas.length && (
        <p className="vacio">Ninguna zona está en precaución ni en peligro.</p>
      )}

      {activas.length > 0 && (
        <ul className="alertas-lista">
          {activas.map((z) => (
            <li key={z.zonaId} className="alerta-item">
              <div className="alerta-cabecera">
                <span className={`badge badge--${z.color}`}>{z.etiqueta}</span>
                <strong>{z.zona}</strong>
                <span className="alerta-hora">{fechaLocal(z.fechaHora)}</span>
              </div>
              <p className="alerta-mensaje">{z.mensaje}</p>
              <p className="alerta-meta">
                Lluvia acumulada {milimetros(z.precipitacionMm)} · {z.cuerpoAgua} · Comunidades:{' '}
                {z.comunidades}
              </p>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}