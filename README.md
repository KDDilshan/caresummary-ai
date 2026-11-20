# CareSummary AI

A full-stack app that gives clinicians a short, **source-linked** AI summary of a patient's health record.
It reads standard **FHIR R4** health data. Every line the AI writes must cite the record it came from,
and the API **checks those citations against the real records** before showing them — so a doctor can trust, or quickly check, each line.

> ⚠️ **Synthetic (fake) patients only**, generated with [Synthea](https://github.com/synthetichealth/synthea). Not a medical device and not medical advice.

## Features

- **Patient list and search** from a FHIR server (34 synthetic patients)
- **Patient record view**: conditions, medicines, latest vital signs and lab results, with an "active only" filter
- **AI summary** (Gemini Flash Lite via Spring AI, 60 s timeout, about 4–10 s per summary) where every line links to its source record. Click a source to jump to that row.
- **Ask a question** about one patient. The answer uses only that patient's records.
- **Hallucination check**: sources the AI invents are removed, and lines without a real source are flagged ⚠
- **Privacy**: the AI never receives the patient's name, ID or birth date, only age, gender and clinical facts
- **Single sign-on with Keycloak (OAuth 2.0 + OpenID Connect)**: the React app uses the Authorization Code flow with **PKCE**, `state` and `nonce`; the API is an OAuth 2.0 **resource server** that never sees a password and checks every token's RS256 signature (JWKS), issuer, **audience** and expiry. Logout ends the Keycloak session too.
- **Two roles** from Keycloak realm roles: `DOCTOR` (patients + AI) and `ADMIN` (audit log only, no patient data). A user with no role can log in but gets `403` everywhere.
- **Audit log** in PostgreSQL: every patient view and AI request (who, what, when). Logins and failed logins are in Keycloak's event log.
- **Parallel FHIR calls**: a patient's conditions, medicines and test results are fetched at the same time on a fixed thread pool (`ExecutorService.invokeAll`, 20 s timeout), so the wait is the slowest call, not all three added up
- **Monitoring**: Spring Boot Actuator health check (database + FHIR server, `503` when one is down), Micrometer metrics (patient load time, thread pool), and optional export to **AWS CloudWatch**
- **CI** with GitHub Actions: API tests and UI type-check/build on every push

## Architecture

```
React UI (Vite, :5173) ── login (OIDC Code + PKCE) ──► Keycloak (:8180, Docker)
      │  access token in "Authorization: Bearer ..."       users, roles, login page, RS256 tokens
      ▼                                                          ▲
Spring Boot API (:8080) ──── Spring Security resource server ────┘ public keys (JWKS): check signature, iss, aud, exp
      │        │        └── Audit log ──► PostgreSQL (:5432)
      │        └── Spring AI ──► Google Gemini   (facts only, no name/ID)
      ▼
HAPI FHIR server (:8090, Docker) ◄── Synthea fake patients
```

**AI flow:** load patient records from FHIR → build a fact list where each fact starts with its source (`[Condition/2040] Essential hypertension | active | 2024-07-04`) → send facts + strict rules to Gemini → get structured JSON (`lines[].text`, `lines[].sources`) → keep only sources that exist → mark lines with no real source as unverified → save an audit event → return.

## API

| Method | Path | Role | Returns |
|--------|------|------|---------|
| GET | `/api/auth/config` | anyone | login mode, Keycloak issuer and client id |
| GET | `/api/auth/me` | any logged-in user | username + roles, as the API sees them |
| POST | `/api/auth/login` | anyone, **local mode only** | JWT token + roles |
| GET | `/api/patients?name=` | DOCTOR | patient list (optional name search) |
| GET | `/api/patients/{id}` | DOCTOR | patient + conditions + medicines + latest vitals/labs |
| POST | `/api/patients/{id}/summary?refresh=false` | DOCTOR | AI summary with checked sources |
| POST | `/api/patients/{id}/ask` `{"question": "..."}` | DOCTOR | AI answer with checked sources |
| GET | `/api/ai/status` | DOCTOR | is the AI configured? |
| GET | `/api/audit?patientId=` | ADMIN | latest 200 audit events |
| GET | `/actuator/health` | anyone (details: ADMIN) | `UP` / `DOWN` for the database and FHIR server |
| GET | `/actuator/metrics/{name}` | ADMIN | metrics, e.g. `caresummary.patient.load`, `executor` |

Errors are JSON "problem details": `400` bad input, `401` not logged in, `403` wrong role, `404` unknown patient, `503` FHIR server or AI unavailable, `504` FHIR server too slow.

## Run it locally

Needs: Java 17+, Node 20+, Docker Desktop.

**1. Start the FHIR server, PostgreSQL and Keycloak** (Keycloak loads its realm, client and demo users from `keycloak/caresummary-realm.json`; first start takes about a minute)

```bash
docker compose up -d
```

**2. Load fake patients (first time only).** Download `synthea-with-dependencies.jar` from the [Synthea releases](https://github.com/synthetichealth/synthea/releases) into `tools/`, then:

```bash
java -jar tools/synthea-with-dependencies.jar -p 30 -s 42 --exporter.baseDirectory ./fhir-data Massachusetts
```

```powershell
.\load-patients.ps1
```

**3. Add your AI key.** Copy `.env.example` to `.env` and set `GEMINI_API_KEY` (free key from [Google AI Studio](https://aistudio.google.com/apikey)). Without a key, everything works except the AI buttons.

**4. Start the API**

```bash
cd caresummary-api
./mvnw spring-boot:run
```

**5. Start the UI**

```bash
cd caresummary-ui
npm install
npm run dev
```

Open http://localhost:5173, click **Log in with Keycloak** and sign in on Keycloak's page as `doctor` / `doctor123`, `admin` / `admin123`, or `nurse` / `nurse123` (no role). These are local demo users only. Keycloak admin console: http://localhost:8180 (`admin` / `admin`).

No Docker memory for Keycloak? Set `AUTH_MODE=local` in `.env` to use the old built-in login form (same demo users, no `nurse`).

## Tests

```bash
cd caresummary-api
./mvnw test
```

52 tests: FHIR mapping, the source checker (hallucination guard), privacy of the AI input, API security (login, roles, 401/403, validation, audit log), Keycloak token rules (real RS256 tokens: wrong key, wrong issuer, wrong audience, expired → rejected), Keycloak role mapping, the parallel worker pool (runs at the same time, keeps order, passes errors on, times out), the CloudWatch metric filter, and the monitoring endpoints. Tests use an in-memory H2 database and mocks, so no Docker or AI key is needed.

## Tech

Java 17 · Spring Boot 4 · Java concurrency (`ExecutorService`) · Spring Boot Actuator · Micrometer · AWS CloudWatch · Spring Security (OAuth 2.0 resource server, JWT) · Keycloak · OpenID Connect · PKCE · Spring AI 2 · Google Gemini · HAPI FHIR 8 · Spring Data JPA · PostgreSQL · React 19 · TypeScript · Vite · Docker Compose · JUnit 5 · Mockito · GitHub Actions

## Limits (honest list)

- Keycloak runs in dev mode (plain HTTP, built-in database). Production needs HTTPS, `start` mode and a real database.
- The access token is kept in `sessionStorage`; a production health app should use a backend-for-frontend with `HttpOnly` cookies. No silent token refresh yet (log in again after 30 minutes; Keycloak remembers you, so no password).
- The summary cache is in memory and resets on restart.
- No rate limiting on the API (Keycloak locks an account after 5 wrong passwords).
- Only a sample of each record is sent to the AI (up to 60 conditions, 60 medicines, 40 latest results).

See [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md) for a plain-English walkthrough and interview questions.
