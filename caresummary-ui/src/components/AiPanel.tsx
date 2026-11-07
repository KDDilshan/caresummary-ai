import { useEffect, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api'
import type { AiResponse } from '../types'

interface Props {
  token: string
  patientId: string
  onSourceClick: (source: string) => void
  onAuthError: () => void
}

export default function AiPanel({ token, patientId, onSourceClick, onAuthError }: Props) {
  const [enabled, setEnabled] = useState<boolean | null>(null)
  const [summary, setSummary] = useState<AiResponse | null>(null)
  const [answers, setAnswers] = useState<AiResponse[]>([])
  const [question, setQuestion] = useState('')
  const [busy, setBusy] = useState<'summary' | 'ask' | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.aiStatus(token).then((s) => setEnabled(s.enabled)).catch(() => setEnabled(false))
  }, [token])

  async function run<T>(kind: 'summary' | 'ask', fn: () => Promise<T>): Promise<T | null> {
    setBusy(kind)
    setError('')
    try {
      return await fn()
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) onAuthError()
      else setError(e instanceof Error ? e.message : 'AI request failed')
      return null
    } finally {
      setBusy(null)
    }
  }

  async function makeSummary(refresh: boolean) {
    const r = await run('summary', () => api.summary(token, patientId, refresh))
    if (r) setSummary(r)
  }

  async function ask(e: FormEvent) {
    e.preventDefault()
    const q = question.trim()
    if (!q) return
    const r = await run('ask', () => api.ask(token, patientId, q))
    if (r) {
      setAnswers((a) => [r, ...a])
      setQuestion('')
    }
  }

  if (enabled === false) {
    return (
      <div className="ai-card off">
        <b>AI is not set up.</b> Add <code>GEMINI_API_KEY</code> to the <code>.env</code> file and restart the API.
        Patient records below still work.
      </div>
    )
  }

  return (
    <div className="ai-card">
      <div className="ai-head">
        <h3>AI summary</h3>
        <div className="ai-actions">
          {!summary && (
            <button className="primary" disabled={busy !== null || enabled === null} onClick={() => makeSummary(false)}>
              {busy === 'summary' ? 'Summarising…' : 'Generate summary'}
            </button>
          )}
          {summary && (
            <button disabled={busy !== null} onClick={() => makeSummary(true)}>
              {busy === 'summary' ? 'Working…' : 'Regenerate'}
            </button>
          )}
        </div>
      </div>

      {error && <div className="error">{error}</div>}
      {summary && <Lines r={summary} onSourceClick={onSourceClick} />}

      <form className="ask" onSubmit={ask}>
        <input
          placeholder="Ask about this patient, e.g. “What medicines are active?”"
          value={question}
          maxLength={300}
          onChange={(e) => setQuestion(e.target.value)}
        />
        <button className="primary" disabled={busy !== null || !question.trim()}>
          {busy === 'ask' ? 'Thinking…' : 'Ask'}
        </button>
      </form>

      {answers.map((a) => (
        <div className="answer" key={a.generatedAt}>
          <div className="q">Q: {a.question}</div>
          <Lines r={a} onSourceClick={onSourceClick} />
        </div>
      ))}
    </div>
  )
}

/** AI lines with clickable source chips. Lines without a real source get a warning. */
function Lines({ r, onSourceClick }: { r: AiResponse; onSourceClick: (s: string) => void }) {
  return (
    <div className="lines">
      <ul>
        {r.lines.map((l, i) => (
          <li key={i} className={l.verified ? '' : 'unverified'}>
            <span>{l.text}</span>
            {l.sources.map((s) => (
              <button key={s} className="chip" title="Show this record" onClick={() => onSourceClick(s)}>
                {s}
              </button>
            ))}
            {!l.verified && <span className="warn">⚠ no source found — check manually</span>}
          </li>
        ))}
      </ul>
      <div className="meta">
        {r.unverified > 0 && <b>{r.unverified} unverified line(s). </b>}
        {r.disclaimer} · {r.model} · {new Date(r.generatedAt).toLocaleTimeString()}
      </div>
    </div>
  )
}
