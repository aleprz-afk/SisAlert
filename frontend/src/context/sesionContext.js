import { createContext } from 'react'

// Contexto de sesión. Vive aparte para que AuthContext.jsx solo exporte componentes
// (lo exige react-refresh para que el recargado en caliente funcione).
export const AuthContext = createContext(null)
