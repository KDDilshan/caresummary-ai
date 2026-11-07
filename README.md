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
- **Login with JWT** and two roles: `DOCTOR` (patients + AI) and `ADMIN` (audit log only, no patient data)
- **Audit log** in PostgreSQL: every login, patient view and AI request (who, what, when)
- **CI** with GitHub Actions: API tests and UI type-check/build on every push

## Architecture

```
React UI (Vite, :5173)
      │  JWT in "Authorization: Bearer ..."
      ▼
Spring Boot API (:8080) ──── Spring Security (JWT, roles)
      │        │        └── Audit log ──► PostgreSQL (:5432)
      │        └── Spring AI ──► Google Gemini   (facts only, no name/ID)
      ▼
HAPI FHIR server (:8090, Docker) ◄── Synthea fake patients
```

**AI flow:** load patient records from FHIR → build a fact list where each fact starts with its source (`[Condition/2040] Essential hypertension | active | 2024-07-04`) → send facts + strict rules to Gemini → get structured JSON (`lines[].text`, `lines[].sources`) → keep only sources that exist → mark lines with no real source as unverified → save an audit event → return.

## API

| Method | Path | Role | Returns |
|--------|------|------|---------|
| POST | `/api/auth/login` | anyone | JWT token + roles |
| GET | `/api/auth/me` | any logged-in user | username + roles |
| GET | `/api/patients?name=` | DOCTOR | patient list (optional name search) |
| GET | `/api/patients/{id}` | DOCTOR | patient + conditions + medicines + latest vitals/labs |
| POST | `/api/patients/{id}/summary?refresh=false` | DOCTOR | AI summary with checked sources |
| POST | `/api/patients/{id}/ask` `{"question": "..."}` | DOCTOR | AI answer with checked sources |
| GET | `/api/ai/status` | DOCTOR | is the AI configured? |
| GET | `/api/audit?patientId=` | ADMIN | latest 200 audit events |

Errors are JSON "problem details": `400` bad input, `401` not logged in, `403` wrong role, `404` unknown patient, `503` FHIR server or AI unavailable.

## Run it locally

Needs: Java 17+, Node 20+, Docker Desktop.

**1. Start the FHIR server and PostgreSQL**

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

Open http://localhost:5173 and log in as `doctor` / `doctor123` or `admin` / `admin123`. These are local demo passwords; change them in `.env`.

## Tests

```bash
cd caresummary-api
./mvnw test
```

23 tests: FHIR mapping, the source checker (hallucination guard), privacy of the AI input, and API security (login, roles, 401/403, validation, audit log). Tests use an in-memory H2 database and mocks, so no Docker or AI key is needed.

## Tech

Java 17 · Spring Boot 4 · Spring Security (JWT) · Spring AI 2 · Google Gemini · HAPI FHIR 8 · Spring Data JPA · PostgreSQL · React 19 · TypeScript · Vite · Docker Compose · JUnit 5 · Mockito · GitHub Actions

## Limits (honest list)

- Demo users live in memory; a real system would use a user database or hospital single sign-on.
- The summary cache is in memory and resets on restart.
- No rate limiting on login yet.
- Only a sample of each record is sent to the AI (up to 60 conditions, 60 medicines, 40 latest results).

See [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md) for a plain-English walkthrough and interview questions.
