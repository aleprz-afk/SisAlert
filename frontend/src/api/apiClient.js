// Cliente HTTP común para todos los servicios.
// Adjunta el JWT automáticamente y normaliza los errores.

function extraerMensaje(data, status) {
  if (typeof data === 'string' && data.trim()) return data
  if (data?.errors) return Object.values(data.errors).flat().join(' ')
  if (data?.message) return data.message
  if (data?.title) return data.title
  if (status >= 500) return 'No se pudo conectar con el servidor. ¿Está corriendo el servicio?'
  return `Error ${status}`
}

export async function apiFetch(url, options = {}) {
  const token = sessionStorage.getItem('token')

  let res
  try {
    res = await fetch(url, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(token && { Authorization: `Bearer ${token}` }),
        ...options.headers,
      },
    })
  } catch {
    throw new Error('No se pudo conectar con el servidor. Revisa tu conexión.')
  }

  // Token vencido o inválido: se cierra la sesión y se vuelve al login
  if (res.status === 401 && token) {
    sessionStorage.removeItem('token')
    window.location.href = '/login'
    throw new Error('La sesión expiró. Inicia sesión de nuevo.')
  }

  const text = await res.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }

  if (!res.ok) {
    const err = new Error(extraerMensaje(data, res.status))
    err.status = res.status
    throw err
  }

  return data
}