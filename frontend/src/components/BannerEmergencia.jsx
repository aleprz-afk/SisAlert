import { useEffect, useState } from 'react'
import { getAlertas } from '../api/alertasApi'

const LINEAS_EMERGENCIA = [
  { nombre: 'Bomberos Villavicencio', tel: '119', tipo: 'Rescate e Inundación' },
  { nombre: 'Cruz Roja Seccional Meta', tel: '132', tipo: 'Atención Médica' },
  { nombre: 'Defensa Civil Meta', tel: '144', tipo: 'Evacuación y Albergues' },
  { nombre: 'Línea Única de Emergencia', tel: '123', tipo: 'Policía Nacional' },
]

export default function BannerEmergencia() {
  const [alertasPeligro, setAlertasPeligro] = useState([])
  const [expandido, setExpandido] = useState(false)

  useEffect(() => {
    let cancelado = false

    async function chequear() {
      try {
        const datos = await getAlertas()
        if (!cancelado) {
          const peligrosas = datos.filter((z) => z.nivel === 'PELIGRO')
          setAlertasPeligro(peligrosas)
        }
      } catch {
        // En caso de fallo de red, se mantiene el estado previo
      }
    }

    chequear()
    const id = setInterval(chequear, 5000)
    return () => {
      cancelado = true
      clearInterval(id)
    }
  }, [])

  if (alertasPeligro.length === 0) {
    return null
  }

  const nombresCuencas = alertasPeligro.map((a) => a.zona).join(', ')

  return (
    <aside className="banner-emergencia" role="alert" aria-live="assertive">
      <div className="banner-emergencia-contenido">
        <div className="banner-emergencia-alerta">
          <span className="icono-sirena">🚨</span>
          <div>
            <strong>ALERTA MÁXIMA DE CRECIENTE SÚBITA EN:</strong>{' '}
            <span className="cuencas-afectadas">{nombresCuencas}</span>
            <p className="banner-instruccion">
              Lluvias torrenciales superaron el umbral de peligro (≥ 80 mm). Inicie evacuación
              preventiva hacia puntos altos y aléjese de las orillas.
            </p>
          </div>
        </div>
        <button
          type="button"
          className="btn-emergencia-toggle"
          onClick={() => setExpandido(!expandido)}
        >
          {expandido ? '▲ Ocultar ayuda' : '▼ Protocolo de Evacuación y Teléfonos'}
        </button>
      </div>

      {expandido && (
        <div className="banner-emergencia-detalle">
          <div className="bloque-contactos">
            <h4>📞 Teléfonos de Emergencia en Villavicencio</h4>
            <div className="contactos-grid">
              {LINEAS_EMERGENCIA.map((c) => (
                <div key={c.tel} className="contacto-item">
                  <strong>{c.nombre}</strong>
                  <span className="contacto-tipo">{c.tipo}</span>
                  <a href={`tel:${c.tel}`} className="btn-tel">
                    Llamar al {c.tel}
                  </a>
                </div>
              ))}
            </div>
          </div>

          <div className="bloque-puntos-seguros">
            <h4>🛡️ Puntos de Encuentro y Sectores Seguros (POMCA)</h4>
            <ul>
              <li>
                <strong>Cuenca Río Guatiquía:</strong> Barzal Alto, Colina del Triunfo y canchas del
                Siete de Agosto (sectores altos).
              </li>
              <li>
                <strong>Cuenca Río Guayuriba:</strong> Zonas altas de Apiay y terraplén vía Acacías.
              </li>
              <li>
                <strong>Cuenca Caño Maizaro / Caño Grande:</strong> Altos de Villacentro y parte alta
                del Porvenir.
              </li>
            </ul>
          </div>
        </div>
      )}
    </aside>
  )
}
