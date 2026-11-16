import { createContext, useContext, useState } from 'react'
import { api } from '../api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(() => {
    try {
      const saved = localStorage.getItem('resolveai_auth')
      return saved ? JSON.parse(saved) : null
    } catch {
      return null
    }
  })

  const persist = (value) => {
    setAuth(value)
    if (value) localStorage.setItem('resolveai_auth', JSON.stringify(value))
    else localStorage.removeItem('resolveai_auth')
  }

  const login = async (email, password) => {
    const data = await api('/api/auth/login', { method: 'POST', body: { email, password } })
    persist(data)
    return data
  }

  const register = async (name, email, password) => {
    const data = await api('/api/auth/register', { method: 'POST', body: { name, email, password } })
    persist(data)
    return data
  }

  const logout = () => persist(null)

  return (
    <AuthContext.Provider value={{ auth, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}