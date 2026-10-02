// Tablero: semáforo por zona con la alerta vigente que calcula el alertas-service.
// Se consulta GET /api/alertas cada 5 segundos; el botón "Sincronizar" hace la escritura
// real (POST /api/lecturas/sincronizar): el servicio trae lluvia, guarda las lecturas y
// recalcula las alertas, registrando el usuario que viene en el token.

import { useEffect, useState } from 'react'
import ChipModoDemo from '../components/ChipModoDemo'
import { getAlertas, sincronizar } from '../api/alertasApi'
import { fechaLocal, milimetros } from '../utils/formato'

const INTERVALO_MS = 5000

export default function EstacionesPage() {
  const [zonas, setZonas] = useState([])
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [sincronizando, setSincronizando] = useState(false)
  const [aviso, setAviso] = useState(null)
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

  async function onSincronizar() {
    setSincronizando(true)
    setAviso(null)
    try {
      setZonas(await sincronizar())
      setAviso({ tipo: 'exito', texto: 'Lecturas sincronizadas y alertas recalculadas.' })
    } catch (e) {
      // 502: el servicio respondió pero la fuente de datos externa falló
      setAviso({ tipo: 'error', texto: e.message })
    } finally {
      setSincronizando(false)
    }
  }

  const sinDatos = zonas.length > 0 && zonas.every((z) => z.nivel == null)

  return (
    <section className="tablero">
      <header className="barra-superior">
        <div>
          <h2>Tablero de zonas</h2>
          <p className="vacio">Se actualiza cada 5 segundos</p>
        </div>
        <div className="acciones-tablero">
          <ChipModoDemo onReintentar={refrescar} />
          <button
            type="button"
            className="btn-primario"
            disabled={sincronizando || (cargando && !zonas.length)}
            onClick={onSincronizar}
          >
            {sincronizando ? 'Sincronizando…' : 'Sincronizar lecturas'}
          </button>
        </div>
      </header>

      {aviso && <p className={aviso.tipo}>{aviso.texto}</p>}

      {cargando && !zonas.length && <p className="vacio">Cargando zonas…</p>}

      {!cargando && !zonas.length && error && (
        <div className="aviso-error">
          <p className="error">{error}</p>
          <button
            type="button"
            className="btn-primario"
            onClick={() => {
              setCargando(true)
              refrescar()
            }}
          >
            Reintentar
          </button>
        </div>
      )}

      {sinDatos && (
        <p className="vacio">
          Todavía no hay lecturas. Pulsa «Sincronizar lecturas» para consultar la fuente de datos.
        </p>
      )}

      {zonas.length > 0 && (
        <div className="zonas-grid">
          {zonas.map((z) => (
            <article key={z.zonaId} className="tarjeta-zona">
              <div className="zona-nombre">
                <span className={`semaforo semaforo--${z.color}`} aria-hidden="true" />
                <div>
                  <h3>{z.zona}</h3>
                  <span className="zona-id">{z.cuerpoAgua}</span>
                </div>
                <span className={`badge badge--${z.color}`}>{z.etiqueta}</span>
              </div>

              <p className="nivel-descripcion">{z.mensaje ?? 'Sin lecturas todavía.'}</p>

              <div className="zona-metricas">
                <div className="metrica">
                  <span className="metrica-valor">{milimetros(z.precipitacionMm)}</span>
                  <span className="metrica-etiqueta">Lluvia acumulada del día</span>
                </div>
                <div className="metrica">
                  <span className="metrica-valor metrica-valor--chica">{fechaLocal(z.fechaHora)}</span>
                  <span className="metrica-etiqueta">Última lectura</span>
                </div>
              </div>

              <p className="alerta-meta">Comunidades: {z.comunidades}</p>
            </article>
          ))}
        </div>
      )}

      {error && zonas.length > 0 && <p className="error">No se pudo actualizar: {error}</p>}
    </section>
  )
}