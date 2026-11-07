import { useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import type { PatientDetail } from '../types'
import AiPanel from './AiPanel'
import RecordTable from './RecordTable'

interface Props {
  token: string
  id: string
  onAuthError: () => void
}

export default function PatientView({ token, id, onAuthError }: Props) {
  const [detail, setDetail] = useState<PatientDetail | null>(null)
  const [error, setError] = useState('')
  const [highlight, setHighlight] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    api
      .patient(token, id)
      .then((d) => !cancelled && setDetail(d))
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) return onAuthError()
        if (!cancelled) setError(e instanceof Error ? e.message : 'Could not load patient')
      })
    return () => {
      cancelled = true
    }
  }, [token, id, onAuthError])

  // Clicking a source chip in the AI panel jumps to that record and highlights it
  function showSource(source: string) {
    setHighlight(source)
    document.getElementById(`src-${source}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  if (error) return <section className="panel"><div className="error">{error}</div></section>
  if (!detail) return <section className="panel muted">Loading patient…</section>

  const p = detail.patient
  return (
    <section className="panel detail">
      <div className="phead">
        <h2>{p.name}</h2>
        <div className="muted">
          {p.gender ?? 'unknown'} · {p.age ?? '?'} years · born {p.birthDate ?? '?'}
          {p.deceased && <span className="tag">deceased</span>} · FHIR id {p.id}
        </div>
      </div>

      <AiPanel token={token} patientId={p.id} onSourceClick={showSource} onAuthError={onAuthError} />

      <RecordTable title="Conditions" items={detail.conditions} highlight={highlight} detailLabel="Status" activeFilter />
      <RecordTable title="Medications" items={detail.medications} highlight={highlight} detailLabel="Status" activeFilter />
      <RecordTable
        title="Recent vital signs & lab results"
        items={detail.recentObservations}
        highlight={highlight}
        detailLabel="Value"
      />
    </section>
  )
}
