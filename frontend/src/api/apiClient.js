// Cliente HTTP común para todos los servicios.
// Adjunta el JWT automáticamente y normaliza los errores.

// Cuerpos que jamás deben mostrarse en pantalla: HTML o stack trace del servidor.
function cuerpoLegible(data) {
  if (typeof data !== 'string') return null
  const texto = data.trim()
  if (!texto) return null
  if (texto.startsWith('<')) return null // página HTML de error
  if (/(^|\n)\s+at\s/.test(texto)) return null // stack trace de .NET o Node
  if (texto.length > 300) return null // demasiado largo para una persona
  return texto
}

function extraerMensaje(data, status) {
  if (data && typeof data === 'object') {
    if (data.errors) return Object.values(data.errors).flat().join(' ')
    if (data.message) return data.message
    // Formato de error del contrato: { "error": "...", "codigo": "..." }
    if (data.error) return data.error
    if (data.title) return data.title
  }
  const texto = cuerpoLegible(data)
  if (texto) return texto
  if (status >= 500) return 'No se pudo conectar con el servidor. ¿Está corriendo el servicio?'
  if (status === 401) return 'No autorizado: la sesión es inválida o ha vencido.'
  if (status === 404) return 'No se encontró el recurso solicitado.'
  if (status === 409) return 'La operación entró en conflicto con el estado actual.'
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
  let data
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }

  if (!res.ok) {
    const err = new Error(extraerMensaje(data, res.status))
    err.status = res.status
    // Cuerpo crudo, por si el llamador necesita inspeccionarlo: el login lo usa para
    // reconocer las credenciales inválidas que la API devuelve como stack trace.
    err.cuerpo = typeof data === 'string' ? data : JSON.stringify(data ?? '')
    throw err
  }

  return data
}