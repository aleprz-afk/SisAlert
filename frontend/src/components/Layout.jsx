import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function Layout() {
  const { usuario, logout } = useAuth()

  return (
    <div className="app">
      <header className="topbar">
        <span className="marca">🌊 Alertas Villavicencio</span>
        <nav className="menu">
          <NavLink to="/estaciones">Tablero</NavLink>
          <NavLink to="/alertas">Alertas</NavLink>
        </nav>
        <div className="sesion">
          <span>{usuario}</span>
          <button className="btn-secundario" onClick={logout}>Salir</button>
        </div>
      </header>
      <main className="contenido">
        <Outlet />
      </main>
    </div>
  )
}