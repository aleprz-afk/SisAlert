export async function apiFetch(url, options = {}) {
  const token = sessionStorage.getItem('token')
  const res = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    },
  })

  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch { data = text }

  if (!res.ok) {
    const msg = typeof data === 'string' ? data : data?.message || data?.title
    const err = new Error(msg || `Error ${res.status}`)
    err.status = res.status
    throw err
  }
  return data
}