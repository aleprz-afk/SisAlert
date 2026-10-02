import { apiFetch } from './apiClient'

const BASE = '/auth-api/api/auth'

export async function login(usernameOrEmail, password) {
  const data = await apiFetch(`${BASE}/login`, {
    method: 'POST',
    body: JSON.stringify({ usernameOrEmail, password }),
  })
  const token = data?.token ?? data?.accessToken 
  if (!token) throw new Error('La respuesta del login no trae token')
  return token
}

export const register = (username, email, password) =>
  apiFetch(`${BASE}/register`, {
    method: 'POST',
    body: JSON.stringify({ username, email, password }),
  })

export const getProfile = () => apiFetch('/auth-api/api/demo/profile')

// La API del profesor responde 500 con un stack trace cuando las credenciales son
// incorrectas, en lugar de un 401: esto lo traduce a un mensaje apto para pantalla.
export function mensajeDeLogin(err) {
  if ([400, 401].includes(err.status)) return 'Usuario o contraseña incorrectos'
  if (err.status === 500 && /invalid credentials/i.test(err.cuerpo ?? '')) {
    return 'Usuario o contraseña incorrectos'
  }
  if (err.status >= 500) {
    return 'No se pudo iniciar sesión. El servidor de autenticación no respondió correctamente.'
  }
  return err.message
}