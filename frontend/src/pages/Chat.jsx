import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, streamChat } from '../api'
import { useAuth } from '../context/AuthContext'
import Header from '../components/Header'
import ActionCard from '../components/ActionCard'

const SUGGESTIONS = [
  'Where is my order SE-1002?',
  'What is your return and refund policy?',
  'I want a refund for order SE-1003',
  'My order arrived damaged. I want to talk to a human.',
]

function Bubble({ sender, content }) {
  if (sender === 'USER') {
    return (
      <div className="flex justify-end">
        <div className="max-w-xl whitespace-pre-wrap rounded-2xl rounded-br-sm bg-indigo-600 px-4 py-2 text-sm text-white">
          {content}
        </div>
      </div>
    )
  }
  if (sender === 'HUMAN_AGENT') {
    return (
      <div className="flex justify-start">
        <div className="max-w-xl rounded-2xl rounded-bl-sm border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm text-slate-800">
          <p className="mb-1 text-xs font-semibold text-emerald-700">🧑‍💼 Human support agent</p>
          <p className="whitespace-pre-wrap">{content}</p>
        </div>
      </div>
    )
  }
  return (
    <div className="flex justify-start">
      <div className="max-w-xl whitespace-pre-wrap rounded-2xl rounded-bl-sm border border-slate-200 bg-white px-4 py-2 text-sm text-slate-800">
        {content}
      </div>
    </div>
  )
}

export default function Chat() {
  const { auth, logout } = useAuth()
  const navigate = useNavigate()
  const token = auth.token

  const [conversations, setConversations] = useState([])
  const [activeId, setActiveId] = useState(null)
  const [conversation, setConversation] = useState(null)
  const [messages, setMessages] = useState([])
  const [actions, setActions] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [streamText, setStreamText] = useState('')
  const [liveActions, setLiveActions] = useState([])
  const [error, setError] = useState('')
  const bottomRef = useRef(null)
  const busyRef = useRef(false)

  const loadTranscript = useCallback(async (id) => {
    const data = await api(`/api/conversations/${id}/messages`, { token })
    setConversation(data.conversation)
    setMessages(data.messages)
    setActions(data.actions)
  }, [token])

  const refreshConversations = useCallback(async () => {
    const list = await api('/api/conversations', { token })
    setConversations(list)
    return list
  }, [token])

  useEffect(() => {
    (async () => {
      try {
        let list = await refreshConversations()
        if (list.length === 0) {
          const created = await api('/api/conversations', { method: 'POST', token })
          list = [created]
          setConversations(list)
        }
        setActiveId(list[0].id)
        await loadTranscript(list[0].id)
      } catch (err) {
        setError(err.message)
      }
    })()
  }, [refreshConversations, loadTranscript])

  // Poll for human-agent replies and status changes while idle
  useEffect(() => {
    if (!activeId) return
    const timer = setInterval(async () => {
      if (busyRef.current) return
      try { await loadTranscript(activeId) } catch { /* transient */ }
    }, 5000)
    return () => clearInterval(timer)
  }, [activeId, loadTranscript])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, actions, streamText, liveActions])

  const selectConversation = async (id) => {
    if (busy || id === activeId) return
    setActiveId(id)
    setError('')
    await loadTranscript(id)
  }

  const newConversation = async () => {
    if (busy) return
    const created = await api('/api/conversations', { method: 'POST', token })
    setConversations((prev) => [created, ...prev])
    setActiveId(created.id)
    setConversation(created)
    setMessages([])
    setActions([])
  }

  const send = async (text) => {
    const message = text.trim()
    if (!message || busy || !activeId) return
    setInput('')
    setError('')
    setBusy(true)
    busyRef.current = true
    setMessages((prev) => [...prev, { id: `tmp-${Date.now()}`, sender: 'USER', content: message, createdAt: new Date().toISOString() }])
    setStreamText('')
    setLiveActions([])
    let finished = false
    const finish = async () => {
      if (finished) return
      finished = true
      setBusy(false)
      busyRef.current = false
      setStreamText('')
      setLiveActions([])
      try {
        await loadTranscript(activeId)
        await refreshConversations()
      } catch { /* transient */ }
    }
    try {
      await streamChat(activeId, message, token, {
        onToken: (t) => setStreamText((prev) => prev + t),
        onAction: (a) => setLiveActions((prev) => [...prev, a]),
        onDone: finish,
        onError: async (msg) => {
          setError(msg)
          await finish()
        },
      })
      await finish()
    } catch (err) {
      setError(err.message)
      await finish()
    }
  }

  const timeline = [
    ...messages.map((m) => ({ kind: 'message', at: m.createdAt, data: m })),
    ...actions.map((a) => ({ kind: 'action', at: a.createdAt, data: a })),
  ].sort((x, y) => new Date(x.at) - new Date(y.at))

  const status = conversation?.status

  return (
    <div className="flex h-screen bg-slate-50">
      <aside className="hidden w-72 flex-col border-r border-slate-200 bg-white md:flex">
        <div className="border-b border-slate-200 p-4"><Header /></div>
        <div className="p-3">
          <button
            onClick={newConversation}
            className="w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-700"
          >
            + New conversation
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-3 pb-3">
          {conversations.map((c) => (
            <button
              key={c.id}
              onClick={() => selectConversation(c.id)}
              className={`mb-1 w-full rounded-lg px-3 py-2 text-left text-sm ${
                c.id === activeId ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-50'
              }`}
            >
              <span className="block font-medium">Conversation #{c.id}</span>
              <span className="text-xs text-slate-400">
                {new Date(c.updatedAt).toLocaleString()} · {c.status}
              </span>
            </button>
          ))}
        </div>
        <div className="border-t border-slate-200 p-4 text-sm">
          <p className="font-medium text-slate-800">{auth.name}</p>
          <p className="text-xs text-slate-500">{auth.email}</p>
          <button
            onClick={() => { logout(); navigate('/login') }}
            className="mt-2 text-xs font-medium text-red-600 hover:underline"
          >
            Sign out
          </button>
        </div>
      </aside>

      <main className="flex flex-1 flex-col">
        <div className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
          <div>
            <h2 className="text-sm font-semibold text-slate-900">ShopEase Support</h2>
            <p className="text-xs text-slate-500">AI agent · grounded in store policies · acts on your orders</p>
          </div>
          {status && status !== 'ACTIVE' && (
            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
              status === 'ESCALATED' ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'
            }`}>
              {status === 'ESCALATED' ? 'Escalated to human' : 'Resolved'}
            </span>
          )}
        </div>

        {status === 'ESCALATED' && (
          <div className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-sm text-amber-800">
            🧑‍💼 A human support agent has taken over this conversation. Their replies appear here (9 AM–9 PM IST).
          </div>
        )}

        <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
          {timeline.length === 0 && !busy && (
            <div className="mx-auto mt-10 max-w-lg text-center">
              <p className="mb-1 text-lg font-semibold text-slate-800">Hi {auth.name.split(' ')[0]}! 👋</p>
              <p className="mb-6 text-sm text-slate-500">
                I can check orders, answer policy questions, create tickets and recommend refunds. Try:
              </p>
              <div className="grid gap-2 sm:grid-cols-2">
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    onClick={() => send(s)}
                    className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-left text-sm text-slate-700 shadow-sm hover:border-indigo-400"
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}
          {timeline.map((item, i) =>
            item.kind === 'message'
              ? <Bubble key={item.data.id ?? `m-${i}`} sender={item.data.sender} content={item.data.content} />
              : <ActionCard key={item.data.id ?? `a-${i}`} action={item.data} />
          )}
          {liveActions.map((a) => <ActionCard key={`live-${a.id}`} action={a} />)}
          {busy && (
            <div className="flex justify-start">
              <div className="max-w-xl rounded-2xl rounded-bl-sm border border-slate-200 bg-white px-4 py-2 text-sm text-slate-800">
                {streamText || <span className="text-slate-400">Thinking…</span>}
                <span className="ml-1 inline-block h-4 w-2 animate-pulse bg-indigo-400 align-middle" />
              </div>
            </div>
          )}
          {error && <p className="text-center text-sm text-red-600">{error}</p>}
          <div ref={bottomRef} />
        </div>

        <form
          onSubmit={(e) => { e.preventDefault(); send(input) }}
          className="flex items-center gap-2 border-t border-slate-200 bg-white px-4 py-3"
        >
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            disabled={busy}
            placeholder="Ask about an order, a refund, or a policy…"
            className="flex-1 rounded-xl border border-slate-300 px-4 py-2 text-sm focus:border-indigo-500 focus:outline-none disabled:bg-slate-50"
          />
          <button
            type="submit" disabled={busy || !input.trim()}
            className="rounded-xl bg-indigo-600 px-5 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
          >
            Send
          </button>
        </form>
      </main>
    </div>
  )
}