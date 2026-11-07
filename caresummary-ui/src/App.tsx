import { useCallback, useState } from 'react'
import { loadSession, saveSession } from './api'
import type { Session } from './types'
import Login from './components/Login'
import PatientList from './components/PatientList'
import PatientView from './components/PatientView'
import AuditLog from './components/AuditLog'

export default function App() {
  const [session, setSession] = useState<Session | null>(loadSession)
  const [selectedId, setSelectedId] = useState<string | null>(null)

  function onLogin(s: Session) {
    saveSession(s)
    setSession(s)
  }

  // useCallback keeps the same function between renders, so child effects don't re-run for no reason
  const logout = useCallback(() => {
    saveSession(null)
    setSession(null)
    setSelectedId(null)
  }, [])

  if (!session) return <Login onLogin={onLogin} />

  const isDoctor = session.roles.includes('DOCTOR')

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          CareSummary <span>AI</span>
        </div>
        <div className="who">
          {session.username} · <b>{session.roles.join(', ')}</b>
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
          <PatientList token={session.token} selectedId={selectedId} onSelect={setSelectedId} onAuthError={logout} />
          {selectedId ? (
            <PatientView key={selectedId} token={session.token} id={selectedId} onAuthError={logout} />
          ) : (
            <section className="panel empty">Choose a patient on the left.</section>
          )}
        </main>
      ) : (
        <main className="single">
          <AuditLog token={session.token} onAuthError={logout} />
        </main>
      )}
    </div>
  )
}
