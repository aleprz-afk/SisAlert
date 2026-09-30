import { apiFetch } from './apiClient'

const BASE = '/auth-api/api/auth'

export async function login(usernameOrEmail, password) {
  const data = await apiFetch(`${BASE}/login`, {
    method: 'POST',
    body: JSON.stringify({ usernameOrEmail, password }),
  })
  const token = data?.token ?? data?.accessToken // ajusta según lo que viste en el Paso 1
  if (!token) throw new Error('La respuesta del login no trae token')
  return token
}

export const register = (username, email, password) =>
  apiFetch(`${BASE}/register`, {
    method: 'POST',
    body: JSON.stringify({ username, email, password }),
  })

export const getProfile = () => apiFetch('/auth-api/api/demo/profile')