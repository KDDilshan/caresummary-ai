import { useState, type FormEvent } from 'react'
import { login } from '../api'
import { startLogin } from '../oidc'
import type { AuthConfig, Session } from '../types'

interface Props {
  config: AuthConfig | null // null = still asking the API
  busy: boolean // finishing a Keycloak login
  error: string
  onLogin: (s: Session) => void
}

export default function Login({ config, busy, error, onLogin }: Props) {
  return (
    <div className="login-page">
      <div className="login-card">
        <h1>
          CareSummary <span>AI</span>
        </h1>
        <p className="muted">Source-linked patient summaries for clinicians</p>

        {!config && !error && <p className="muted">Loading…</p>}
        {config?.mode === 'keycloak' && <KeycloakLogin config={config} busy={busy} error={error} />}
        {config?.mode === 'local' && <LocalLogin onLogin={onLogin} outerError={error} />}
        {!config && error && <div className="error">{error}</div>}
      </div>
    </div>
  )
}

/** Keycloak mode: one button. The password is typed on Keycloak's page, never in our app. */
function KeycloakLogin({ config, busy, error }: { config: Extract<AuthConfig, { mode: 'keycloak' }>; busy: boolean; error: string }) {
  const [redirecting, setRedirecting] = useState(false)
  const [startError, setStartError] = useState('')

  async function go() {
    setRedirecting(true)
    setStartError('')
    try {
      await startLogin(config)
    } catch (e) {
      setStartError(e instanceof Error ? e.message : 'Could not start login')
      setRedirecting(false)
    }
  }

  const shownError = startError || error
  return (
    <>
      {shownError && <div className="error">{shownError}</div>}
      <button className="primary" onClick={go} disabled={busy || redirecting}>
        {busy ? 'Finishing login…' : redirecting ? 'Opening login page…' : 'Log in with Keycloak'}
      </button>
      <p className="hint">
        Single sign-on with OpenID Connect (Authorization Code + PKCE). Demo users: <code>doctor</code> /{' '}
        <code>doctor123</code> (patients + AI), <code>admin</code> / <code>admin123</code> (audit log only),{' '}
        <code>nurse</code> / <code>nurse123</code> (no role: sees nothing).
      </p>
    </>
  )
}

/** Local mode: the old built-in demo login form (app.security.mode=local). */
function LocalLogin({ onLogin, outerError }: { onLogin: (s: Session) => void; outerError: string }) {
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

  const shownError = error || outerError
  return (
    <form onSubmit={submit} className="login-form">
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

      {shownError && <div className="error">{shownError}</div>}

      <button className="primary" disabled={busy}>
        {busy ? 'Logging in…' : 'Log in'}
      </button>

      <p className="hint">
        Local demo login. Users: <code>doctor</code> (patients + AI) and <code>admin</code> (audit log only).
      </p>
    </form>
  )
}
