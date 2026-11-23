import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'

// soloAdmin: la ruta solo se muestra a administradores; el resto vuelve al tablero.
export default function RutaProtegida({ children, soloAdmin = false }) {
  const { isAuthenticated, esAdmin } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (soloAdmin && !esAdmin) return <Navigate to="/estaciones" replace />
  return children
}