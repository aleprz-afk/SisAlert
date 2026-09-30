import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { getProfile } from '../api/authApi'

export default function InicioPage() {
  const { logout } = useAuth()
  const [perfil, setPerfil] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getProfile().then(setPerfil).catch((e) => setError(e.message))
  }, [])

  return (
    <main>
      <h1>Sesión iniciada</h1>
      {perfil && <pre>{JSON.stringify(perfil, null, 2)}</pre>}
      {error && <p className="error">{error}</p>}
      <button onClick={logout}>Cerrar sesión</button>
    </main>
  )
}