import { Navigate, Route, Routes } from 'react-router-dom'
import RutaProtegida from './components/RutaProtegida'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import RegistroPage from './pages/RegistroPage'
import EstacionesPage from './pages/EstacionesPage'
import AlertasPage from './pages/AlertasPage'

export default function App() {
  return (
    <Routes>
      {/* Rutas públicas */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/registro" element={<RegistroPage />} />

      {/* Rutas protegidas: requieren token */}
      <Route element={<RutaProtegida><Layout /></RutaProtegida>}>
        <Route path="/estaciones" element={<EstacionesPage />} />
        <Route path="/alertas" element={<AlertasPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/estaciones" replace />} />
    </Routes>
  )
}