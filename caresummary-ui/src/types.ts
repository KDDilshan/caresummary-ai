// Same shapes as the Java records in the API.

export interface PatientSummary {
  id: string
  name: string
  gender: string | null
  birthDate: string | null
  age: number | null
  deceased: boolean
}

export interface ClinicalItem {
  source: string // e.g. "Condition/2266"
  name: string
  detail: string | null
  date: string | null
}

export interface PatientDetail {
  patient: PatientSummary
  conditions: ClinicalItem[]
  medications: ClinicalItem[]
  recentObservations: ClinicalItem[]
}

export interface CheckedLine {
  text: string
  sources: string[]
  verified: boolean
}

export interface AiResponse {
  patientId: string
  kind: 'summary' | 'answer'
  question: string | null
  lines: CheckedLine[]
  unverified: number
  model: string
  generatedAt: string
  disclaimer: string
}

export interface AuditEvent {
  id: number
  at: string
  username: string
  action: string
  patientId: string | null
  detail: string | null
}

export interface Session {
  token: string // access token, sent to the API as "Authorization: Bearer <token>"
  username: string
  roles: string[]
  expiresAt: string
  idToken?: string // keycloak mode only: used to log out of Keycloak too
}

// From GET /api/auth/config: which login to show
export type AuthConfig = { mode: 'local' } | { mode: 'keycloak'; issuer: string; clientId: string }
