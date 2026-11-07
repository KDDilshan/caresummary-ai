import type { AiResponse, AuditEvent, PatientDetail, PatientSummary, Session } from './types'

// All calls to the Spring Boot API. The JWT token is sent in the Authorization header.

const SESSION_KEY = 'caresummary.session'

export function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as Session
    return new Date(s.expiresAt) > new Date() ? s : null // expired token = logged out
  } catch {
    return null
  }
}

export function saveSession(s: Session | null) {
  try {
    if (s) sessionStorage.setItem(SESSION_KEY, JSON.stringify(s))
    else sessionStorage.removeItem(SESSION_KEY)
  } catch {
    /* storage blocked: session lives only in memory */
  }
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function call<T>(path: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(path, { ...init, headers })
  if (!res.ok) {
    let msg = res.statusText
    try {
      const body = await res.json()
      msg = body.detail ?? msg // Spring "problem details" error message
    } catch {
      /* no JSON body */
    }
    if (res.status === 401 && !path.endsWith('/login')) msg = 'Your login has expired. Please log in again.'
    if (res.status === 403) msg = 'Your role is not allowed to do this.'
    throw new ApiError(res.status, msg)
  }
  return res.json() as Promise<T>
}

export async function login(username: string, password: string): Promise<Session> {
  const t = await call<{ value: string; roles: string[]; expiresAt: string }>('/api/auth/login', null, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  return { token: t.value, roles: t.roles, expiresAt: t.expiresAt, username }
}

export const api = {
  patients: (token: string, name: string) =>
    call<PatientSummary[]>(`/api/patients${name ? `?name=${encodeURIComponent(name)}` : ''}`, token),
  patient: (token: string, id: string) => call<PatientDetail>(`/api/patients/${encodeURIComponent(id)}`, token),
  aiStatus: (token: string) => call<{ enabled: boolean }>('/api/ai/status', token),
  summary: (token: string, id: string, refresh: boolean) =>
    call<AiResponse>(`/api/patients/${encodeURIComponent(id)}/summary?refresh=${refresh}`, token, { method: 'POST' }),
  ask: (token: string, id: string, question: string) =>
    call<AiResponse>(`/api/patients/${encodeURIComponent(id)}/ask`, token, {
      method: 'POST',
      body: JSON.stringify({ question }),
    }),
  audit: (token: string, patientId: string) =>
    call<AuditEvent[]>(`/api/audit${patientId ? `?patientId=${encodeURIComponent(patientId)}` : ''}`, token),
}
