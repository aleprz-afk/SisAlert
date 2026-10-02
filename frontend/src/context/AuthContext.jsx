import { createContext, useContext, useEffect, useState } from 'react'
import * as authApi from '../api/authApi'

const AuthContext = createContext(null)

// .NET puede guardar el nombre del usuario en cualquiera de estos claims
const CLAIMS_NOMBRE = [
  'unique_name',
  'name',
  'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name',
  'username',
  'email',
  'sub',
]

function leerToken(token) {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    return JSON.parse(atob(base64))
  } catch {
    return null
  }
}

function tokenVigente(token) {
  const payload = leerToken(token)
  return !!payload && (!payload.exp || payload.exp * 1000 > Date.now())
}

function nombreDesdeToken(token) {
  const payload = leerToken(token)
  for (const claim of CLAIMS_NOMBRE) {
    if (payload?.[claim]) return payload[claim]
  }
  return 'Usuario'
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => {
    const guardado = sessionStorage.getItem('token')
    return guardado && tokenVigente(guardado) ? guardado : null
  })

  async function login(usuario, password) {
    const nuevoToken = await authApi.login(usuario, password)
    sessionStorage.setItem('token', nuevoToken)
    setToken(nuevoToken)
  }

  function logout() {
    sessionStorage.removeItem('token')
    setToken(null)
  }

  // Cierra la sesión justo cuando el token expira
  useEffect(() => {
    if (!token) return
    const exp = leerToken(token)?.exp
    if (!exp) return
    const id = setTimeout(logout, exp * 1000 - Date.now())
    return () => clearTimeout(id)
  }, [token])

  const value = {
    token,
    usuario: token ? nombreDesdeToken(token) : null,
    isAuthenticated: !!token,
    login,
    register: authApi.register,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  return useContext(AuthContext)
}