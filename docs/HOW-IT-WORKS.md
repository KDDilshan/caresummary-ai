# How CareSummary AI works (plain English)

Read this top to bottom once. Then open each file named here and find the lines it talks about.
After that, practise the interview questions at the end out loud.

---

## 1. The idea in one paragraph

Doctors don't have time to read a long patient history. CareSummary AI loads the patient's records,
asks an AI to write a short summary, and **makes the AI prove every line** by citing the record it came from.
The API then checks those citations. If the AI invents something, the line is flagged ⚠.
Only fake patients are used.

---

## 2. The parts

| Part | What it is | Where |
|------|-----------|-------|
| FHIR server | A ready-made health database with a REST API (HAPI FHIR). Runs in Docker. | `docker-compose.yml` |
| Fake patients | Made by Synthea, uploaded with a script | `load-patients.ps1` |
| PostgreSQL | Stores the audit log. Runs in Docker. | `docker-compose.yml` |
| API | Spring Boot app: login, patients, AI, audit | `caresummary-api/` |
| UI | React app the doctor uses | `caresummary-ui/` |
| CI | GitHub runs the tests on every push | `.github/workflows/ci.yml` |

---

## 3. What happens when a doctor clicks "Generate summary"

1. **React** (`AiPanel.tsx`) sends `POST /api/patients/1413/summary` with the header `Authorization: Bearer <token>`.
2. **Spring Security** (`SecurityConfig.java`) checks the token: valid signature? not expired? role `DOCTOR`?
   If not → `401` (not logged in) or `403` (wrong role). The request never reaches our code.
3. **AiController** checks the id format (`PatientIds.java`), so bad input never reaches the FHIR server.
4. **PatientService** asks the FHIR server for the patient, their conditions, medicines and latest 40 vital signs/labs.
   FHIR returns results in pages; `fetchAll` follows the "next" links.
5. **FhirMapper** turns big FHIR objects into small records (`ClinicalItem`). Each keeps its **source**, e.g. `Condition/2040`.
6. **PatientFacts** builds the text for the AI. Each line starts with its source:
   `[Condition/2040] Essential hypertension | active | 2024-07-04`.
   **No name, no id, no birth date**, only age and gender. That's privacy by design.
7. **AiService** sends the facts + strict rules (`SYSTEM_RULES`) to Gemini through **Spring AI**.
   `.entity(AiAnswer.class)` tells Spring AI to ask for JSON and turn it into a Java record.
8. **SourceChecker** compares every source the AI cited with the real list.
   Fake sources are removed. A line with no real source → `verified = false`.
9. **AuditService** saves a row in PostgreSQL: who, what, which patient, when.
10. The JSON goes back to React, which shows each line with clickable source chips.
    Clicking a chip scrolls to that record and highlights it.

---

## 4. Login (JWT) in simple words

- `POST /api/auth/login` with username + password.
- `AuthenticationManager` checks the password against a **BCrypt hash** (passwords are never stored as plain text).
- `TokenService` makes a **JWT**: a signed text containing *who* (`sub`), *roles*, and *expiry* (60 min).
- It's signed with a secret key (HMAC-SHA256). If anyone changes the token, the signature check fails.
- The browser sends the token on every request. The server keeps **no session**: it's "stateless".
- Because we don't use cookies, CSRF protection isn't needed, so it's turned off.

**Roles:** `DOCTOR` sees patients and the AI. `ADMIN` sees only the audit log, not patient data.
That's the "least privilege" rule: give each person only what they need.

---

## 5. File map (API)

```
caresummary-api/src/main/java/com/kavindu/caresummaryapi/
├── config/
│   ├── FhirConfig.java        FHIR client bean (connect + timeouts)
│   └── AiToggle.java          turns Spring AI off when there is no API key
├── patient/
│   ├── PatientController.java GET /api/patients, /api/patients/{id}  (+ audit)
│   ├── PatientService.java    talks to FHIR, paging, limits
│   ├── FhirMapper.java        FHIR objects -> our small DTOs
│   ├── PatientIds.java        id validation
│   └── dto/                   PatientSummary, PatientDetail, ClinicalItem
├── ai/
│   ├── AiController.java      POST summary / ask (+ audit)
│   ├── AiService.java         prompt rules, Spring AI call, cache
│   ├── PatientFacts.java      facts text for the AI (no name/id)
│   ├── SourceChecker.java     the hallucination check
│   └── AiAnswer.java          JSON shape we ask the AI for
├── security/
│   ├── SecurityConfig.java    who can call what, JWT encoder/decoder, users, CORS
│   ├── TokenService.java      makes JWTs
│   └── AuthController.java    login, /me
├── audit/                     AuditEvent (JPA entity), repository, service, controller
└── common/                    error handling -> JSON problem details
```

---

## 6. Tests (23)

| Test | What it proves |
|------|----------------|
| `FhirMapperTest` | FHIR → DTO conversion: names, ages, dates, blood pressure values |
| `SourceCheckerTest` | made-up sources are removed; lines without real sources are unverified |
| `PatientFactsTest` | the AI never gets the name, id or birth date |
| `ApiSecurityTest` | no token → 401; fake token → 401; doctor can't see audit; admin can't see patients; bad id → 400; unknown → 404; logins and views are audited |

Tests use **H2** (an in-memory database) and **Mockito mocks** for FHIR and the AI, so they run anywhere,
including GitHub Actions, without Docker or an API key.

---

## 7. Problems I hit and how I fixed them (good interview stories)

- **FHIR server lost all data after a restart.** HAPI keeps data in memory by default. I moved it to a file
  database in a Docker volume. Then it crashed because the container user couldn't write to the volume,
  so I ran that container as root (local demo only).
- **A 28 MB patient file crashed the FHIR server (OutOfMemory).** I kept the server's memory low on purpose
  (the laptop has 7 GB), so I skipped that one patient instead of raising the memory.
- **The app wouldn't start without an AI key.** Spring AI's Gemini setup fails with no key.
  I wrote `AiToggle`, which switches the AI off at startup when the key is missing,
  so the rest of the app still works and the UI says "AI is not set up".
- **The token contained a strange role, `FACTOR_PASSWORD`.** Spring Security 7 adds a marker for
  "logged in with a password". I now keep only real `ROLE_` authorities, and added a test for it.
- **Lab results were full of long questionnaires.** I filtered observations to the `vital-signs` and
  `laboratory` categories in the FHIR query.

---

## 8. Interview questions — practise these out loud

**What is FHIR?**
The global standard for exchanging health data. Each record is a JSON "resource" (Patient, Condition,
MedicationRequest, Observation) with a REST API. Records point to each other with references like `Patient/123`.

**Why not let the AI read everything and just trust it?**
AI models can hallucinate. In healthcare a wrong line is dangerous. So every line must cite a source,
and the server checks the citation exists. Unchecked lines are shown with a warning.

**How do you stop the AI from leaking patient identity?**
We never send the name, id or birth date, only age, gender and clinical facts. A test checks this.

**What is prompt injection and how do you handle it?**
When a user writes instructions in the input to change the AI's rules. The question is wrapped in
`<question>` tags, the rules say to ignore instructions inside it, the question is limited to 300 characters,
and even if the AI is tricked, the source check still applies.

**What is a JWT? Why stateless?**
A signed token holding the user and roles. The server checks the signature on each request, with no session
to store. That makes it easy to run several API copies. Tokens expire after 60 minutes.

**Why is ADMIN not allowed to see patients?**
Least privilege. The admin's job is to review access, not to read medical records.

**Why an audit log?**
Health systems must show who opened which record and when. Every login (including failed ones),
patient view and AI call is stored in PostgreSQL.

**Why DTOs instead of returning FHIR objects?**
FHIR objects are huge. DTOs return only what the UI needs, keep the API stable,
and don't leak fields by accident.

**How do the tests run without Docker or an AI key?**
H2 in-memory database for JPA, and `@MockitoBean` replaces the FHIR and AI services in Spring tests.

**What does CI do?**
On every push, GitHub Actions builds the API and runs all tests, then type-checks and builds the React app.
A failing test blocks a green build.

**What would you add next?**
A real user database or hospital SSO, login rate limiting, Flyway database migrations instead of
`ddl-auto=update`, a persistent summary cache, and an evaluation set to measure summary quality.
