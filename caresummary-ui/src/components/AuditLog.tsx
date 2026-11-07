import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import type { AuditEvent } from '../types'

/** ADMIN page: who did what, when. The admin sees this log but cannot open patient records. */
export default function AuditLog({ token, onAuthError }: { token: string; onAuthError: () => void }) {
  const [events, setEvents] = useState<AuditEvent[]>([])
  const [patientId, setPatientId] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setEvents(await api.audit(token, patientId.trim()))
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return onAuthError()
      setError(e instanceof Error ? e.message : 'Could not load audit log')
    } finally {
      setLoading(false)
    }
  }, [token, patientId, onAuthError])

  useEffect(() => {
    load()
    // load once on open; the filter is applied with the button
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <section className="panel">
      <div className="ai-head">
        <h2>Audit log</h2>
        <div className="ai-actions">
          <input
            className="search small-input"
            placeholder="Filter by patient id"
            value={patientId}
            onChange={(e) => setPatientId(e.target.value)}
          />
          <button onClick={load} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>
      </div>
      <p className="muted small">Latest 200 events. Every login, patient view and AI request is recorded.</p>
      {error && <div className="error">{error}</div>}
      <div className="table-wrap tall">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>User</th>
              <th>Action</th>
              <th>Patient</th>
              <th>Detail</th>
            </tr>
          </thead>
          <tbody>
            {events.map((e) => (
              <tr key={e.id}>
                <td className="nowrap">{new Date(e.at).toLocaleString()}</td>
                <td>{e.username}</td>
                <td>
                  <span className={`act act-${e.action}`}>{e.action}</span>
                </td>
                <td>{e.patientId ?? '—'}</td>
                <td>{e.detail ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
