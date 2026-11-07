import { useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import type { PatientSummary } from '../types'

interface Props {
  token: string
  selectedId: string | null
  onSelect: (id: string) => void
  onAuthError: () => void
}

export default function PatientList({ token, selectedId, onSelect, onAuthError }: Props) {
  const [search, setSearch] = useState('')
  const [patients, setPatients] = useState<PatientSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // Wait 300 ms after typing stops, then search (fewer API calls)
  useEffect(() => {
    let cancelled = false
    const t = setTimeout(async () => {
      setLoading(true)
      setError('')
      try {
        const list = await api.patients(token, search.trim())
        if (!cancelled) setPatients(list)
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) return onAuthError()
        if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load patients')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }, 300)
    return () => {
      cancelled = true
      clearTimeout(t)
    }
  }, [search, token, onAuthError])

  return (
    <aside className="panel list-panel">
      <input
        className="search"
        placeholder="Search by first or last name…"
        value={search}
        maxLength={50}
        onChange={(e) => setSearch(e.target.value)}
      />
      <div className="count muted">{loading ? 'Loading…' : `${patients.length} patients`}</div>
      {error && <div className="error">{error}</div>}
      <ul className="patients">
        {patients.map((p) => (
          <li key={p.id}>
            <button className={p.id === selectedId ? 'patient active' : 'patient'} onClick={() => onSelect(p.id)}>
              <span className="pname">{p.name}</span>
              <span className="pmeta">
                {p.gender ?? '?'} · {p.age ?? '?'} yrs
                {p.deceased && <span className="tag">deceased</span>}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </aside>
  )
}
