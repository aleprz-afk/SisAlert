import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import { ESCENARIOS } from '../context/estadoSimulacion'
import { useSimulacion } from '../context/useSimulacion'
import BannerEmergencia from './BannerEmergencia'

export default function Layout() {
  const { usuario, esAdmin, logout } = useAuth()
  const { escenario, setEscenario } = useSimulacion()
  const nombreEscenario = ESCENARIOS.find((e) => e.valor === escenario)?.texto

  return (
    <div className="app">
      <header className="topbar">
        <span className="marca">🌊 Alertas Villavicencio</span>
        <nav className="menu">
          <NavLink to="/estaciones">Tablero</NavLink>
          <NavLink to="/alertas">Alertas</NavLink>
          {esAdmin && <NavLink to="/zonas">Zonas</NavLink>}
        </nav>
        <div className="sesion">
          {esAdmin && (
            <label className="selector-datos">
              <span className="selector-datos-etiqueta">Datos</span>
              <select value={escenario ?? ''} onChange={(e) => setEscenario(e.target.value || null)}>
                <option value="">Reales (Open-Meteo + IA)</option>
                {ESCENARIOS.map((e) => (
                  <option key={e.valor} value={e.valor}>{e.texto}</option>
                ))}
              </select>
            </label>
          )}
          <span>{usuario}</span>
          <button
            className="btn-secundario"
            onClick={() => {
              setEscenario(null)
              logout()
            }}
          >
            Salir
          </button>
        </div>
      </header>
      {escenario && (
        <div className="aviso-simulacion" role="status">
          <strong>MODO SIMULACIÓN · {nombreEscenario}</strong>
          <span>
            Solo lo ves tú como administrador. No se guarda nada y los demás usuarios siguen viendo
            los datos reales.
          </span>
          <button type="button" className="btn-secundario" onClick={() => setEscenario(null)}>
            Volver a datos reales
          </button>
        </div>
      )}
      <BannerEmergencia />
      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}