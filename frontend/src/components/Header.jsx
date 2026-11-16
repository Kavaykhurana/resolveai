export default function Header({ subtitle }) {
  return (
    <div className="flex items-center gap-3">
      <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 text-lg font-bold text-white">
        R
      </div>
      <div>
        <h1 className="text-lg font-semibold text-slate-900">ResolveAI</h1>
        <p className="text-xs text-slate-500">{subtitle || 'Autonomous support for ShopEase'}</p>
      </div>
    </div>
  )
}