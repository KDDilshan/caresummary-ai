import { useState, type FormEvent } from 'react'
import { login } from '../api'
import type { Session } from '../types'

export default function Login({ onLogin }: { onLogin: (s: Session) => void }) {
  const [username, setUsername] = useState('doctor')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      onLogin(await login(username.trim(), password))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <h1>
          CareSummary <span>AI</span>
        </h1>
        <p className="muted">Source-linked patient summaries for clinicians</p>

        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {error && <div className="error">{error}</div>}

        <button className="primary" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>

        <p className="hint">
          Demo users: <code>doctor</code> (patients + AI) and <code>admin</code> (audit log only).
        </p>
      </form>
    </div>
  )
}
