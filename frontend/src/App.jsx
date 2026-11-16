import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './context/AuthContext'
import Login from './pages/Login'
import Register from './pages/Register'
import Chat from './pages/Chat'
import Dashboard from './pages/Dashboard'

function Protected({ role, children }) {
  const { auth } = useAuth()
  if (!auth) return <Navigate to="/login" replace />
  if (role && auth.role !== role) {
    return <Navigate to={auth.role === 'AGENT' ? '/dashboard' : '/chat'} replace />
  }
  return children
}

export default function App() {
  const { auth } = useAuth()
  const home = auth ? (auth.role === 'AGENT' ? '/dashboard' : '/chat') : '/login'
  return (
    <Routes>
      <Route path="/" element={<Navigate to={home} replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/chat" element={<Protected role="CUSTOMER"><Chat /></Protected>} />
      <Route path="/dashboard" element={<Protected role="AGENT"><Dashboard /></Protected>} />
      <Route path="*" element={<Navigate to={home} replace />} />
    </Routes>
  )
}