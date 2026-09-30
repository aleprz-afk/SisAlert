// App.jsx
import { Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import InicioPage from './pages/InicioPage'
import RutaProtegida from './components/RutaProtegida'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<RutaProtegida><InicioPage /></RutaProtegida>} />
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  )
}