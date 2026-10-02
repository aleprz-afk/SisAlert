import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { mensajeDeLogin } from '../api/authApi'

export default function LoginPage() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [usuario, setUsuario] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  if (isAuthenticated) return <Navigate to="/estaciones" replace />

  const mensaje = location.state?.mensaje

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setCargando(true)
    try {
      await login(usuario, password)
      navigate('/estaciones', { replace: true })
    } catch (err) {
      setError(mensajeDeLogin(err))
    } finally {
      setCargando(false)
    }
  }

  return (
    <main className="pantalla-centrada">
      <form onSubmit={handleSubmit} className="tarjeta">
        <h1>🌊 Alertas Villavicencio</h1>
        <p className="subtitulo">Sistema de alerta temprana de crecientes</p>

        {mensaje && <p className="exito">{mensaje}</p>}

        <label>
          Usuario o correo
          <input value={usuario} onChange={(e) => setUsuario(e.target.value)} required autoFocus />
        </label>
        <label>
          Contraseña
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>

        {error && <p className="error">{error}</p>}

        <button className="btn-primario" disabled={cargando}>
          {cargando ? 'Ingresando...' : 'Ingresar'}
        </button>

        <p className="pie">
          ¿No tienes cuenta? <Link to="/registro">Regístrate</Link>
        </p>
      </form>
    </main>
  )
}