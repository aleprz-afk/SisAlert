import { useEffect, useState } from 'react'
import { getHistoricoZona } from '../api/alertasApi'
import { fechaLocal, milimetros } from '../utils/formato'

export default function ModalHistoricoZona({ zona, onClose }) {
  const [lecturas, setLecturas] = useState([])
  const [cargando, setCargando] = useState(true)

  useEffect(() => {
    let cancelado = false
    async function cargar() {
      if (!zona) return
      setCargando(true)
      const data = await getHistoricoZona(zona.zonaId)
      if (!cancelado) {
        setLecturas(data || [])
        setCargando(false)
      }
    }
    cargar()
    return () => { cancelado = true }
  }, [zona])

  if (!zona) return null

  // Calcular tendencia hidrológica comparando las 2 mediciones más recientes
  let tendencia = { texto: 'Sin datos suficientes', icono: '➖', clase: 'estable' }
  if (lecturas.length >= 2) {
    const diff = (lecturas[0].precipitacionMm || 0) - (lecturas[1].precipitacionMm || 0)
    if (diff > 0.5) {
      tendencia = {
        texto: `En ascenso (+${diff.toFixed(1)} mm)`,
        icono: '📈',
        clase: 'ascenso',
      }
    } else if (diff < -0.5) {
      tendencia = {
        texto: `En descenso (${diff.toFixed(1)} mm)`,
        icono: '📉',
        clase: 'descenso',
      }
    } else {
      tendencia = { texto: 'Estable (sin variación brusca)', icono: '➡️', clase: 'estable' }
    }
  }

  const maxMm = Math.max(...lecturas.map((l) => l.precipitacionMm || 0), 90)

  return (
    <div className="modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="modal-contenido" onClick={(e) => e.stopPropagation()}>
        <header className="modal-cabecera">
          <div>
            <span className={`badge badge--${zona.color}`}>{zona.etiqueta}</span>
            <h3 style={{ margin: '0.4rem 0 0.1rem' }}>{zona.zona}</h3>
            <span className="zona-id">{zona.cuerpoAgua}</span>
          </div>
          <button type="button" className="btn-modal-cerrar" onClick={onClose} aria-label="Cerrar">
            ✕
          </button>
        </header>

        <div className="tarjeta-tendencia">
          <span className="icono-tendencia">{tendencia.icono}</span>
          <div>
            <strong>Comportamiento del Cauce:</strong>
            <p className={`tendencia-valor tendencia-valor--${tendencia.clase}`}>{tendencia.texto}</p>
          </div>
        </div>

        <h4>Evolución Temporal de Precipitaciones</h4>
        {cargando ? (
          <p className="vacio">Cargando registros históricos de la base de datos...</p>
        ) : lecturas.length === 0 ? (
          <p className="vacio">
            Aún no hay suficientes lecturas registradas para esta cuenca.
          </p>
        ) : (
          <>
            {/* Gráfica de barras / Histograma */}
            <div className="histograma-wrap">
              <div className="guia-umbral guia-peligro" style={{ bottom: `${(80 / maxMm) * 100}%` }}>
                <span>Peligro (80 mm)</span>
              </div>
              <div className="guia-umbral guia-precaucion" style={{ bottom: `${(40 / maxMm) * 100}%` }}>
                <span>Precaución (40 mm)</span>
              </div>

              <div className="barras-contenedor">
                {[...lecturas].reverse().map((l) => {
                  const mm = l.precipitacionMm || 0
                  const alturaPct = Math.min(100, Math.max(8, (mm / maxMm) * 100))
                  const esPeligro = mm >= 80
                  const esPrecaucion = mm >= 40 && mm < 80
                  const colorBarra = esPeligro ? '#c62828' : esPrecaucion ? '#f9a825' : '#2e7d32'

                  return (
                    <div key={l.id} className="barra-item">
                      <div
                        className="barra-col"
                        style={{ height: `${alturaPct}%`, backgroundColor: colorBarra }}
                        title={`${mm} mm a las ${fechaLocal(l.fechaHora)}`}
                      >
                        <span className="barra-valor">{mm.toFixed(0)}</span>
                      </div>
                      <span className="barra-hora">
                        {new Date(l.fechaHora).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </span>
                    </div>
                  )
                })}
              </div>
            </div>

            {/* Tabla de auditoría */}
            <div className="tabla-historico-wrap">
              <table className="tabla-historico">
                <thead>
                  <tr>
                    <th>Fecha y Hora</th>
                    <th>Precipitación</th>
                    <th>Fuente</th>
                  </tr>
                </thead>
                <tbody>
                  {lecturas.slice(0, 8).map((l) => (
                    <tr key={l.id}>
                      <td>{fechaLocal(l.fechaHora)}</td>
                      <td><strong>{milimetros(l.precipitacionMm)}</strong></td>
                      <td><span className="fuente-tag">{l.fuente}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        <footer className="modal-pie">
          <button type="button" className="btn-primario" onClick={onClose}>
            Entendido
          </button>
        </footer>
      </div>
    </div>
  )
}
