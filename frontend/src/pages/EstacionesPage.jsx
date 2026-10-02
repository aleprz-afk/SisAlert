// Tablero: estado actual de la zona (semáforo, nivel y tendencia).
// Se consulta el alertas-service cada 5 segundos, como indica el contrato.

import { useEffect, useState } from 'react'
import ChipModoDemo from '../components/ChipModoDemo'
import { getEstadoZona } from '../api/alertasApi'

const INTERVALO_MS = 5000

const DESCRIPCION_NIVEL = {
  verde: 'Nivel normal, sin aviso',
  amarillo: 'Supera el umbral de vigilancia',
  naranja: 'Supera el umbral de alerta',
  rojo: 'Desbordamiento inminente',
}

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

export default function EstacionesPage() {
  const [estado, setEstado] = useState(null)
  const [error, setError] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [intento, setIntento] = useState(0)

  useEffect(() => {
    let cancelado = false

    async function traer() {
      try {
        const datos = await getEstadoZona()
        if (!cancelado) {
          setEstado(datos)
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

  const nivel = estado?.nivelAlerta ?? 'verde'
  const tendencia = Number(estado?.tendenciaMetrosHora ?? 0)
  const umbrales = Object.entries(estado?.umbrales ?? {})

  return (
    <section className="tablero">
      <header className="barra-superior">
        <div>
          <h2>Tablero de la zona</h2>
          <p className="vacio">Se actualiza cada 5 segundos</p>
        </div>
        <ChipModoDemo onReintentar={() => setIntento((i) => i + 1)} />
      </header>

      {cargando && !estado && <p className="vacio">Cargando el estado de la zona…</p>}

      {!cargando && !estado && error && (
        <div className="aviso-error">
          <p className="error">{error}</p>
          <button
            type="button"
            className="btn-primario"
            onClick={() => {
              setCargando(true)
              setIntento((i) => i + 1)
            }}
          >
            Reintentar
          </button>
        </div>
      )}

      {estado && (
        <article className="tarjeta-zona">
          <div className="zona-nombre">
            <span className={`semaforo semaforo--${nivel}`} aria-hidden="true" />
            <div>
              <h3>{estado.zonaNombre}</h3>
              <span className="zona-id">{estado.zonaId}</span>
            </div>
            <span className={`badge badge--${nivel}`}>{nivel}</span>
          </div>

          <p className="nivel-descripcion">{DESCRIPCION_NIVEL[nivel] ?? ''}</p>

          <div className="zona-metricas">
            <div className="metrica">
              <span className="metrica-valor">{metros(estado.nivelMetros)} m</span>
              <span className="metrica-etiqueta">Nivel actual</span>
            </div>
            <div className="metrica">
              <span className={`metrica-valor ${tendencia >= 0 ? 'sube' : 'baja'}`}>
                {tendencia >= 0 ? '↑' : '↓'} {metros(Math.abs(tendencia))} m/h
              </span>
              <span className="metrica-etiqueta">Tendencia de subida</span>
            </div>
            <div className="metrica">
              <span className="metrica-valor">{fechaLocal(estado.actualizadoEn)}</span>
              <span className="metrica-etiqueta">Última lectura</span>
            </div>
          </div>

          {umbrales.length > 0 && (
            <div className="umbrales">
              <h4>Umbrales de la zona</h4>
              <ul>
                {umbrales.map(([nombre, valor]) => (
                  <li key={nombre}>
                    <span className={`punto punto--${nombre}`} aria-hidden="true" />
                    <span className="umbral-nombre">{nombre}</span>
                    <span className="umbral-valor">{metros(valor)} m</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {error && <p className="error">No se pudo actualizar: {error}</p>}
        </article>
      )}
    </section>
  )
}
