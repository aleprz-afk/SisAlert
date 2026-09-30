import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [usuario, setUsuario] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setCargando(true)
    try {
      await login(usuario, password)
      navigate('/')
    } catch (err) {
      setError([400, 401].includes(err.status)
        ? 'Usuario o contraseña incorrectos'
        : err.message)
    } finally {
      setCargando(false)
    }
  }

  return (
    <main className="login">
      <form onSubmit={handleSubmit} className="login-card">
        <h1>Alertas Villavicencio</h1>
        <p>Sistema de alerta temprana de crecientes</p>
        <label>Usuario o correo
          <input value={usuario} onChange={(e) => setUsuario(e.target.value)} required autoFocus />
        </label>
        <label>Contraseña
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error">{error}</p>}
        <button disabled={cargando}>{cargando ? 'Ingresando...' : 'Ingresar'}</button>
      </form>
    </main>
  )
}