import { createContext, useContext, useState } from 'react'
import * as authApi from '../api/authApi'

const AuthContext = createContext(null)

function tokenVigente(token) {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const { exp } = JSON.parse(atob(payload))
    return !exp || exp * 1000 > Date.now()
  } catch {
    return false
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => {
    const t = sessionStorage.getItem('token')
    return t && tokenVigente(t) ? t : null
  })

  async function login(usuario, password) {
    const t = await authApi.login(usuario, password)
    sessionStorage.setItem('token', t)
    setToken(t)
  }

  function logout() {
    sessionStorage.removeItem('token')
    setToken(null)
  }

  return (
    <AuthContext.Provider value={{ token, isAuthenticated: !!token, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)