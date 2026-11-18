import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'

export default function RutaProtegida({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" replace />
}