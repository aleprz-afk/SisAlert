import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function RegistroPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ username: '', email: '', password: '', confirmar: '' })
  const [error, setError] = useState('')
  const [cargando, setCargando] = useState(false)

  function cambiar(e) {
    setForm({ ...form, [e.target.name]: e.target.value })
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    if (form.password !== form.confirmar) {
      setError('Las contraseñas no coinciden')
      return
    }
    setCargando(true)
    try {
      await register(form.username, form.email, form.password)
      navigate('/login', { state: { mensaje: 'Cuenta creada. Ya puedes iniciar sesión.' } })
    } catch (err) {
      setError(err.message)
    } finally {
      setCargando(false)
    }
  }

  return (
    <main className="pantalla-centrada">
      <form onSubmit={handleSubmit} className="tarjeta">
        <h1>Crear cuenta</h1>
        <p className="subtitulo">Alertas Villavicencio</p>

        <label>
          Usuario
          <input name="username" value={form.username} onChange={cambiar} required autoFocus />
        </label>
        <label>
          Correo
          <input name="email" type="email" value={form.email} onChange={cambiar} required />
        </label>
        <label>
          Contraseña
          <input name="password" type="password" value={form.password} onChange={cambiar} required />
        </label>
        <label>
          Confirmar contraseña
          <input name="confirmar" type="password" value={form.confirmar} onChange={cambiar} required />
        </label>

        {error && <p className="error">{error}</p>}

        <button className="btn-primario" disabled={cargando}>
          {cargando ? 'Creando...' : 'Registrarme'}
        </button>

        <p className="pie">
          ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
        </p>
      </form>
    </main>
  )
}