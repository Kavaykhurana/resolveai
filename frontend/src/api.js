const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

export async function api(path, { method = 'GET', body, token } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(`${API_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    try {
      const data = await res.json()
      message = data.message || data.error || message
    } catch { /* non-JSON error body */ }
    const err = new Error(message)
    err.status = res.status
    throw err
  }
  if (res.status === 204) return null
  return res.json()
}

// Streams the agentic chat response over SSE.
// Callbacks: onToken(text), onAction(actionEvent), onDone(fullText), onError(message)
export async function streamChat(conversationId, message, token, callbacks) {
  const res = await fetch(`${API_URL}/api/chat/${conversationId}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ message }),
  })
  if (!res.ok || !res.body) {
    callbacks.onError?.(`Chat request failed (${res.status})`)
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const dispatch = (rawEvent) => {
    let name = 'message'
    const dataLines = []
    for (const line of rawEvent.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5))
    }
    if (dataLines.length === 0) return
    const data = dataLines.join('\n')
    if (name === 'token') callbacks.onToken?.(data)
    else if (name === 'action') {
      try { callbacks.onAction?.(JSON.parse(data)) } catch { /* ignore malformed action */ }
    } else if (name === 'done') callbacks.onDone?.(data)
    else if (name === 'error') callbacks.onError?.(data)
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buffer.indexOf('\n\n')) !== -1) {
      dispatch(buffer.slice(0, idx))
      buffer = buffer.slice(idx + 2)
    }
  }
  if (buffer.trim()) dispatch(buffer)
}