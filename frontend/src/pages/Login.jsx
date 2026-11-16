import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Header from '../components/Header'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const data = await login(email, password)
      navigate(data.role === 'AGENT' ? '/dashboard' : '/chat')
    } catch (err) {
      setError(err.status === 401 ? 'Invalid email or password.' : err.message)
    } finally {
      setBusy(false)
    }
  }

  const fillDemo = (demoEmail) => {
    setEmail(demoEmail)
    setPassword('demo1234')
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-md">
        <div className="mb-6 flex justify-center"><Header /></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
          <h2 className="mb-1 text-xl font-semibold text-slate-900">Sign in</h2>
          <p className="mb-6 text-sm text-slate-500">Customer chat or support agent dashboard</p>
          <form onSubmit={submit} className="space-y-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Email</label>
              <input
                type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
                placeholder="you@example.com"
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-700">Password</label>
              <input
                type="password" required value={password} onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none"
                placeholder="••••••••"
              />
            </div>
            {error && <p className="text-sm text-red-600">{error}</p>}
            <button
              type="submit" disabled={busy}
              className="w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white hover:bg-indigo-700 disabled:opacity-50"
            >
              {busy ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
          <p className="mt-4 text-center text-sm text-slate-500">
            New customer? <Link to="/register" className="font-medium text-indigo-600 hover:underline">Create an account</Link>
          </p>
        </div>
        <div className="mt-4 rounded-2xl border border-indigo-100 bg-indigo-50 p-4 text-sm">
          <p className="mb-2 font-medium text-slate-800">Demo accounts (password: demo1234)</p>
          <div className="flex flex-col gap-2 sm:flex-row">
            <button
              onClick={() => fillDemo('customer@demo.com')}
              className="flex-1 rounded-lg border border-indigo-200 bg-white px-3 py-2 text-left hover:border-indigo-400"
            >
              <span className="block font-medium text-slate-800">🛍️ Customer</span>
              <span className="text-xs text-slate-500">customer@demo.com</span>
            </button>
            <button
              onClick={() => fillDemo('agent@demo.com')}
              className="flex-1 rounded-lg border border-indigo-200 bg-white px-3 py-2 text-left hover:border-indigo-400"
            >
              <span className="block font-medium text-slate-800">🧑‍💼 Support agent</span>
              <span className="text-xs text-slate-500">agent@demo.com</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}