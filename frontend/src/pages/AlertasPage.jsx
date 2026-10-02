// Lista de alertas de la zona y botón de reconocer.
// Es la funcionalidad de negocio que exige el enunciado del parcial: una escritura real
// contra el alertas-service que queda registrada con el usuario y la hora.

import { useEffect, useState } from 'react'
import ChipModoDemo from '../components/ChipModoDemo'
import { getAlertas, reconocerAlerta } from '../api/alertasApi'

const INTERVALO_MS = 5000

function metros(valor) {
  return Number(valor ?? 0).toFixed(2)
}

function fechaLocal(iso) {
  if (!iso) return '—'
  const fecha = new Date(iso)
  if (Number.isNaN(fecha.getTime())) return '—'
  return fecha.toLocaleString('es-CO', {
    timeZone: 'America/Bogota',
    dateStyle: 'short',
    timeStyle: 'short',
  })
}

export default function AlertasPage() {
  const [alertas, setAlertas] = useState([])
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [aviso, setAviso] = useState(null)
  const [enviando, setEnviando] = useState(null)
  const [intento, setIntento] = useState(0)

  const refrescar = () => setIntento((i) => i + 1)

  useEffect(() => {
    let cancelado = false

    async function traer() {
      try {
        const datos = await getAlertas()
        if (!cancelado) {
          setAlertas(datos)
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

  async function reconocer(alertaId) {
    setEnviando(alertaId)
    setAviso(null)
    try {
      await reconocerAlerta(alertaId)
    } catch (e) {
      // 409: ya la reconoció otro usuario; el refresco de abajo muestra quién
      setAviso(e.message)
    } finally {
      setEnviando(null)
      refrescar()
    }
  }

  return (
    <section className="tablero">
      <header className="barra-superior">
        <div>
          <h2>Alertas activas</h2>
          <p className="vacio">Más reciente primero · se actualiza cada 5 segundos</p>
        </div>
        <ChipModoDemo onReintentar={refrescar} />
      </header>

      {aviso && <p className="error">{aviso}</p>}
      {error && !alertas.length && <p className="error">{error}</p>}

      {cargando && !alertas.length && <p className="vacio">Cargando alertas…</p>}

      {!cargando && !alertas.length && !error && (
        <p className="vacio">
          Todavía no hay alertas para esta zona. Cuando el nivel cruce un umbral aparecerán aquí.
        </p>
      )}

      {alertas.length > 0 && (
        <ul className="alertas-lista">
          {alertas.map((alerta) => (
            <li key={alerta.alertaId} className="alerta-item">
              <div className="alerta-cabecera">
                <span className={`badge badge--${alerta.nivel}`}>{alerta.nivel}</span>
                <span className="alerta-id">{alerta.alertaId}</span>
                <span className="alerta-hora">{fechaLocal(alerta.emitidaEn)}</span>
              </div>

              <p className="alerta-mensaje">{alerta.mensaje}</p>
              <p className="alerta-meta">
                Nivel {metros(alerta.nivelMetros)} m · umbral {metros(alerta.umbralMetros)} m
              </p>

              <div className="alerta-accion">
                {alerta.reconocidaPor ? (
                  <p className="reconocida">
                    ✓ Reconocida por <strong>{alerta.reconocidaPor}</strong>
                    {alerta.reconocidaEn ? ` · ${fechaLocal(alerta.reconocidaEn)}` : ''}
                  </p>
                ) : (
                  <button
                    type="button"
                    className="btn-primario"
                    disabled={enviando === alerta.alertaId}
                    onClick={() => reconocer(alerta.alertaId)}
                  >
                    {enviando === alerta.alertaId ? 'Registrando…' : 'Reconocer alerta'}
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
