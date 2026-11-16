const TOOL_LABELS = {
  checkOrderStatus: 'Checked order status',
  createTicket: 'Created support ticket',
  recommendRefund: 'Recommended refund',
  escalateToHuman: 'Escalated to human agent',
}

const TOOL_ICONS = {
  checkOrderStatus: '📦',
  createTicket: '🎫',
  recommendRefund: '💸',
  escalateToHuman: '🧑‍💼',
}

export default function ActionCard({ action }) {
  const ok = action.success
  return (
    <div
      className={`my-2 max-w-xl rounded-lg border px-3 py-2 text-sm ${
        ok ? 'border-indigo-200 bg-indigo-50' : 'border-amber-200 bg-amber-50'
      }`}
    >
      <div className="flex items-center gap-2 font-medium text-slate-800">
        <span>{TOOL_ICONS[action.toolName] || '⚙️'}</span>
        <span>{TOOL_LABELS[action.toolName] || action.toolName}</span>
        <span
          className={`ml-auto rounded-full px-2 py-0.5 text-xs font-semibold ${
            ok ? 'bg-indigo-100 text-indigo-700' : 'bg-amber-100 text-amber-700'
          }`}
        >
          {ok ? 'done' : 'blocked'}
        </span>
      </div>
      <p className="mt-1 line-clamp-3 text-xs text-slate-600">{action.summary || action.resultJson}</p>
    </div>
  )
}