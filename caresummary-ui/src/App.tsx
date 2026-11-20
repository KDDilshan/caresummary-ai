import { useCallback, useEffect, useState } from 'react'
import { api, loadSession, saveSession } from './api'
import { finishLogin, isLoginCallback, logoutOfKeycloak } from './oidc'
import type { AuthConfig, Session } from './types'
import Login from './components/Login'
import PatientList from './components/PatientList'
import PatientView from './components/PatientView'
import AuditLog from './components/AuditLog'

export default function App() {
  const [session, setSession] = useState<Session | null>(loadSession)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [config, setConfig] = useState<AuthConfig | null>(null)
  const [finishing, setFinishing] = useState(isLoginCallback)
  const [authError, setAuthError] = useState('')

  function onLogin(s: Session) {
    saveSession(s)
    setSession(s)
  }

  // 1. Ask the API which login to use (Keycloak or the local demo form)
  useEffect(() => {
    api
      .authConfig()
      .then(setConfig)
      .catch(() => setAuthError('Cannot reach the API. Is it running on port 8080?'))
  }, [])

  // 2. Back from Keycloak's login page with ?code=...: swap it for tokens, then ask the API who we are
  useEffect(() => {
    if (config?.mode !== 'keycloak' || !isLoginCallback()) return
    finishLogin(config)
      .then(async (t) => {
        const me = await api.me(t.accessToken)
        onLogin({ token: t.accessToken, idToken: t.idToken, expiresAt: t.expiresAt, ...me })
      })
      .catch((e: unknown) => setAuthError(e instanceof Error ? e.message : 'Login failed'))
      .finally(() => setFinishing(false))
  }, [config])

  // Token expired or rejected (401): forget it here. Keycloak may still remember the user,
  // so "Log in with Keycloak" then signs them straight back in without a password (single sign-on).
  // useCallback keeps the same function between renders, so child effects don't re-run for no reason
  const clearSession = useCallback(() => {
    saveSession(null)
    setSession(null)
    setSelectedId(null)
  }, [])

  // "Log out" button: forget the token here AND end the Keycloak session
  function logout() {
    const idToken = session?.idToken
    clearSession()
    if (config?.mode === 'keycloak') {
      logoutOfKeycloak(config, idToken).catch(() => {
        /* Keycloak down: we are still logged out of this app */
      })
    }
  }

  if (!session) return <Login config={config} busy={finishing} error={authError} onLogin={onLogin} />

  const isDoctor = session.roles.includes('DOCTOR')
  const isAdmin = session.roles.includes('ADMIN')

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          CareSummary <span>AI</span>
        </div>
        <div className="who">
          {session.username} · <b>{session.roles.length ? session.roles.join(', ') : 'no role'}</b>
          <button className="link" onClick={logout}>
            Log out
          </button>
        </div>
      </header>

      <div className="notice">
        Synthetic (fake) patients only. AI output is a draft for clinician review — not medical advice.
      </div>

      {isDoctor ? (
        <main className="layout">
          <PatientList token={session.token} selectedId={selectedId} onSelect={setSelectedId} onAuthError={clearSession} />
          {selectedId ? (
            <PatientView key={selectedId} token={session.token} id={selectedId} onAuthError={clearSession} />
          ) : (
            <section className="panel empty">Choose a patient on the left.</section>
          )}
        </main>
      ) : isAdmin ? (
        <main className="single">
          <AuditLog token={session.token} onAuthError={clearSession} />
        </main>
      ) : (
        <main className="single">
          <section className="panel empty">
            You are logged in, but your account has no role in this app (DOCTOR or ADMIN). Ask an administrator
            to give you one in Keycloak.
          </section>
        </main>
      )}
    </div>
  )
}
