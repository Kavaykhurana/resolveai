import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../context/AuthContext'
import Header from '../components/Header'
import ActionCard from '../components/ActionCard'

const PRIORITY_STYLES = {
  HIGH: 'bg-red-100 text-red-700',
  MEDIUM: 'bg-amber-100 text-amber-700',
  LOW: 'bg-slate-100 text-slate-600',
}

const STATUS_STYLES = {
  OPEN: 'bg-indigo-100 text-indigo-700',
  IN_REVIEW: 'bg-amber-100 text-amber-700',
  RESOLVED: 'bg-emerald-100 text-emerald-700',
}

function Badge({ value, styles }) {
  return (
    <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${styles[value] || 'bg-slate-100 text-slate-600'}`}>
      {value}
    </span>
  )
}

export default function Dashboard() {
  const { auth, logout } = useAuth()
  const navigate = useNavigate()
  const token = auth.token

  const [tickets, setTickets] = useState([])
  const [selected, setSelected] = useState(null)
  const [transcript, setTranscript] = useState(null)
  const [reply, setReply] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const refreshTickets = useCallback(async () => {
    const list = await api('/api/agent/tickets', { token })
    setTickets(list)
    return list
  }, [token])

  const loadTranscript = useCallback(async (conversationId) => {
    const data = await api(`/api/conversations/${conversationId}/messages`, { token })
    setTranscript(data)
  }, [token])

  useEffect(() => {
    refreshTickets().catch((err) => setError(err.message))
    const timer = setInterval(() => {
      refreshTickets().catch(() => {})
    }, 5000)
    return () => clearInterval(timer)
  }, [refreshTickets])

  useEffect(() => {
    if (!selected) return
    loadTranscript(selected.conversationId).catch(() => {})
    const timer = setInterval(() => {
      loadTranscript(selected.conversationId).catch(() => {})
    }, 5000)
    return () => clearInterval(timer)
  }, [selected, loadTranscript])

  const selectTicket = async (ticket) => {
    setSelected(ticket)
    setError('')
    try { await loadTranscript(ticket.conversationId) } catch (err) { setError(err.message) }
  }

  const act = async (fn) => {
    setBusy(true)
    setError('')
    try {
      await fn()
      const list = await refreshTickets()
      if (selected) {
        const updated = list.find((t) => t.id === selected.id)
        if (updated) setSelected(updated)
        await loadTranscript(selected.conversationId)
      }
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const sendReply = (e) => {
    e.preventDefault()
    if (!reply.trim() || !selected) return
    const content = reply.trim()
    act(async () => {
      await api(`/api/agent/tickets/${selected.id}/reply`, { method: 'POST', body: { content }, token })
      setReply('')
    })
  }

  const decide = (decision) =>
    act(() => api(`/api/agent/refunds/${selected.refund.id}/decide`, { method: 'POST', body: { decision }, token }))

  const resolve = () =>
    act(() => api(`/api/agent/tickets/${selected.id}/resolve`, { method: 'POST', token }))

  const current = selected ? tickets.find((t) => t.id === selected.id) || selected : null
  const timeline = transcript
    ? [
        ...transcript.messages.map((m) => ({ kind: 'message', at: m.createdAt, data: m })),
        ...transcript.actions.map((a) => ({ kind: 'action', at: a.createdAt, data: a })),
      ].sort((x, y) => new Date(x.at) - new Date(y.at))
    : []

  return (
    <div className="flex h-screen flex-col bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
        <Header subtitle="Support agent dashboard" />
        <div className="flex items-center gap-4 text-sm">
          <span className="text-slate-600">{auth.name}</span>
          <button
            onClick={() => { logout(); navigate('/login') }}
            className="font-medium text-red-600 hover:underline"
          >
            Sign out
          </button>
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        <section className="w-96 overflow-y-auto border-r border-slate-200 bg-white">
          <div className="border-b border-slate-100 px-4 py-3">
            <h2 className="text-sm font-semibold text-slate-900">Ticket queue</h2>
            <p className="text-xs text-slate-500">{tickets.length} tickets · refreshes every 5s</p>
          </div>
          {tickets.length === 0 && (
            <p className="px-4 py-6 text-sm text-slate-500">No tickets yet. They appear here when the AI agent creates them.</p>
          )}
          {tickets.map((t) => (
            <button
              key={t.id}
              onClick={() => selectTicket(t)}
              className={`block w-full border-b border-slate-100 px-4 py-3 text-left hover:bg-slate-50 ${
                current?.id === t.id ? 'bg-indigo-50' : ''
              }`}
            >
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-slate-800">#{t.id}</span>
                <Badge value={t.priority} styles={PRIORITY_STYLES} />
                <Badge value={t.status} styles={STATUS_STYLES} />
              </div>
              <p className="mt-1 truncate text-sm text-slate-700">{t.subject}</p>
              <p className="text-xs text-slate-500">
                {t.customerName} · {new Date(t.createdAt).toLocaleString()}
                {t.refund && ` · refund Rs.${t.refund.amount} (${t.refund.status})`}
              </p>
            </button>
          ))}
        </section>

        <section className="flex flex-1 flex-col overflow-hidden">
          {!current ? (
            <div className="flex flex-1 items-center justify-center text-sm text-slate-500">
              Select a ticket to review the conversation and take action.
            </div>
          ) : (
            <>
              <div className="border-b border-slate-200 bg-white px-4 py-3">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-sm font-semibold text-slate-900">{current.subject}</h2>
                  <Badge value={current.priority} styles={PRIORITY_STYLES} />
                  <Badge value={current.status} styles={STATUS_STYLES} />
                  {current.status !== 'RESOLVED' && (
                    <button
                      onClick={resolve} disabled={busy}
                      className="ml-auto rounded-lg border border-emerald-300 px-3 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-50 disabled:opacity-50"
                    >
                      Mark resolved
                    </button>
                  )}
                </div>
                <p className="mt-1 text-xs text-slate-500">{current.customerName} — {current.description}</p>
                {current.refund && (
                  <div className="mt-3 flex flex-wrap items-center gap-3 rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-sm">
                    <span className="font-medium text-slate-800">
                      💸 Refund Rs.{current.refund.amount} for {current.refund.orderNumber}
                    </span>
                    <span className="text-xs text-slate-500">{current.refund.reason}</span>
                    {current.refund.status === 'PENDING' ? (
                      <span className="ml-auto flex gap-2">
                        <button
                          onClick={() => decide('APPROVED')} disabled={busy}
                          className="rounded-lg bg-emerald-600 px-3 py-1 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-50"
                        >
                          Approve
                        </button>
                        <button
                          onClick={() => decide('REJECTED')} disabled={busy}
                          className="rounded-lg bg-red-600 px-3 py-1 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
                        >
                          Reject
                        </button>
                      </span>
                    ) : (
                      <Badge value={current.refund.status} styles={{ APPROVED: 'bg-emerald-100 text-emerald-700', REJECTED: 'bg-red-100 text-red-700' }} />
                    )}
                  </div>
                )}
              </div>

              <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
                {timeline.map((item, i) =>
                  item.kind === 'action' ? (
                    <ActionCard key={item.data.id ?? `a-${i}`} action={item.data} />
                  ) : (
                    <div key={item.data.id ?? `m-${i}`} className={`max-w-2xl rounded-xl border px-4 py-2 text-sm ${
                      item.data.sender === 'USER'
                        ? 'border-slate-200 bg-white'
                        : item.data.sender === 'HUMAN_AGENT'
                          ? 'border-emerald-200 bg-emerald-50'
                          : 'border-indigo-100 bg-indigo-50'
                    }`}>
                      <p className="mb-1 text-xs font-semibold text-slate-500">
                        {item.data.sender === 'USER' ? '🛍️ Customer' : item.data.sender === 'HUMAN_AGENT' ? '🧑‍💼 You (human agent)' : '🤖 ResolveAI'}
                        <span className="ml-2 font-normal">{new Date(item.data.createdAt).toLocaleTimeString()}</span>
                      </p>
                      <p className="whitespace-pre-wrap text-slate-800">{item.data.content}</p>
                    </div>
                  )
                )}
              </div>

              {error && <p className="px-4 pb-2 text-sm text-red-600">{error}</p>}
              <form onSubmit={sendReply} className="flex items-center gap-2 border-t border-slate-200 bg-white px-4 py-3">
                <input
                  value={reply}
                  onChange={(e) => setReply(e.target.value)}
                  placeholder="Reply to the customer as a human agent…"
                  className="flex-1 rounded-xl border border-slate-300 px-4 py-2 text-sm focus:border-indigo-500 focus:outline-none"
                />
                <button
                  type="submit" disabled={busy || !reply.trim()}
                  className="rounded-xl bg-indigo-600 px-5 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
                >
                  Reply
                </button>
              </form>
            </>
          )}
        </section>
      </div>
    </div>
  )
}