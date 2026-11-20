// Gestión de zonas de riesgo: crear, editar y eliminar.
// A diferencia del tablero, esta pantalla siempre trabaja contra el alertas-service
// real, porque el modo demostración no puede guardar cambios.

import { useEffect, useState } from 'react'
import { actualizarZona, crearZona, eliminarZona, getZonas } from '../api/alertasApi'

const FORM_VACIO = {
  nombre: '',
  cuerpoAgua: '',
  comunidades: '',
  codigoEstacion: '',
}

export default function ZonasPage() {
  const [zonas, setZonas] = useState([])
  const [form, setForm] = useState(FORM_VACIO)
  const [editandoId, setEditandoId] = useState(null)
  const [error, setError] = useState(null)
  const [aviso, setAviso] = useState(null)
  const [cargando, setCargando] = useState(true)
  const [guardando, setGuardando] = useState(false)

  async function cargar() {
    try {
      setZonas(await getZonas())
      setError(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setCargando(false)
    }
  }

  useEffect(() => {
    let cancelado = false

    async function cargarInicial() {
      try {
        const datos = await getZonas()
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

    cargarInicial()
    return () => {
      cancelado = true
    }
  }, [])

  function cambiarCampo(evento) {
    const { name, value } = evento.target
    setForm((actual) => ({ ...actual, [name]: value }))
  }

  function editar(zona) {
    setEditandoId(zona.id)
    setForm({
      nombre: zona.nombre,
      cuerpoAgua: zona.cuerpoAgua,
      comunidades: zona.comunidades,
      codigoEstacion: zona.codigoEstacion ?? '',
    })
    setAviso(null)
    setError(null)
  }

  function cancelar() {
    setEditandoId(null)
    setForm(FORM_VACIO)
  }

  async function guardar(evento) {
    evento.preventDefault()
    setGuardando(true)
    setAviso(null)
    setError(null)

    try {
      if (editandoId) {
        await actualizarZona(editandoId, form)
        setAviso('Zona actualizada correctamente.')
      } else {
        await crearZona(form)
        setAviso('Zona creada correctamente.')
      }
      cancelar()
      await cargar()
    } catch (e) {
      setError(e.message)
    } finally {
      setGuardando(false)
    }
  }

  async function borrar(zona) {
    const confirmado = window.confirm(
      `¿Eliminar la zona "${zona.nombre}"? También se eliminarán sus lecturas y alertas.`,
    )
    if (!confirmado) return

    setAviso(null)
    setError(null)
    try {
      await eliminarZona(zona.id)
      if (editandoId === zona.id) cancelar()
      setAviso('Zona eliminada correctamente.')
      await cargar()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <section className="tablero">
      <header className="barra-superior">
        <div>
          <h2>Zonas de riesgo</h2>
          <p className="vacio">
            Crear, editar y eliminar zonas. Los cambios se guardan en PostgreSQL.
          </p>
        </div>
      </header>

      {aviso && <p className="exito">{aviso}</p>}
      {error && <p className="error">{error}</p>}

      <form className="panel formulario-zona" onSubmit={guardar}>
        <h3>{editandoId ? 'Editar zona' : 'Nueva zona'}</h3>

        <div className="form-grid">
          <label>
            Nombre
            <input
              name="nombre"
              value={form.nombre}
              onChange={cambiarCampo}
              maxLength={100}
              required
              placeholder="Zona Guatiquía Norte"
            />
          </label>

          <label>
            Código de estación
            <input
              name="codigoEstacion"
              value={form.codigoEstacion}
              onChange={cambiarCampo}
              maxLength={50}
              required
              placeholder="0035035100"
            />
          </label>

          <label className="form-ancho">
            Cuerpo de agua
            <input
              name="cuerpoAgua"
              value={form.cuerpoAgua}
              onChange={cambiarCampo}
              maxLength={255}
              required
              placeholder="Río Guatiquía · Caño Parrado"
            />
          </label>

          <label className="form-ancho">
            Comunidades
            <input
              name="comunidades"
              value={form.comunidades}
              onChange={cambiarCampo}
              maxLength={255}
              required
              placeholder="Barzal Alto, Siete de Agosto, La Nohora"
            />
          </label>
        </div>

        <div className="form-acciones">
          <button type="submit" className="btn-primario" disabled={guardando}>
            {guardando ? 'Guardando…' : editandoId ? 'Guardar cambios' : 'Crear zona'}
          </button>
          {editandoId && (
            <button type="button" className="btn-neutro" onClick={cancelar} disabled={guardando}>
              Cancelar
            </button>
          )}
        </div>
      </form>

      {cargando && <p className="vacio">Cargando zonas…</p>}

      {!cargando && zonas.length === 0 && !error && (
        <p className="vacio">No hay zonas registradas. Crea la primera con el formulario.</p>
      )}

      {zonas.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla-zonas">
            <thead>
              <tr>
                <th>Zona</th>
                <th>Cuerpo de agua</th>
                <th>Comunidades</th>
                <th>Estación</th>
                <th aria-label="Acciones" />
              </tr>
            </thead>
            <tbody>
              {zonas.map((zona) => (
                <tr key={zona.id}>
                  <td><strong>{zona.nombre}</strong></td>
                  <td>{zona.cuerpoAgua}</td>
                  <td>{zona.comunidades}</td>
                  <td className="mono">{zona.codigoEstacion}</td>
                  <td className="acciones-fila">
                    <button type="button" className="btn-mini" onClick={() => editar(zona)}>
                      Editar
                    </button>
                    <button type="button" className="btn-mini btn-peligro" onClick={() => borrar(zona)}>
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
