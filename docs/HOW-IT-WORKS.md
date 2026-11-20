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

> **Now:** the default login is **Keycloak** (OAuth 2.0 + OpenID Connect), explained in **section 12**.
> This section is the older built-in login, still there as `AUTH_MODE=local` for running without Keycloak.

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
│   ├── PatientService.java    talks to FHIR, paging, limits, runs 3 searches in parallel, load-time metric
│   ├── FhirWorkers.java       fixed thread pool: run tasks at the same time, wait, timeout, metrics
│   ├── FhirTimeoutException   FHIR too slow -> 504
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
│   ├── SecurityConfig.java        who can call what (same rules in both login modes), CORS
│   ├── KeycloakConfig.java        keycloak mode: check Keycloak tokens (signature, iss, aud, exp)
│   ├── KeycloakRoleConverter.java keycloak mode: realm_access.roles -> ROLE_DOCTOR / ROLE_ADMIN
│   ├── LocalLoginConfig.java      local mode: in-memory users, BCrypt, our own HS256 tokens
│   ├── LocalLoginController.java  local mode: POST /api/auth/login
│   ├── TokenService.java          local mode: makes JWTs
│   └── AuthController.java        GET /api/auth/config, /api/auth/me
├── audit/                     AuditEvent (JPA entity), repository, service, controller
├── monitoring/
│   ├── FhirHealthIndicator.java      adds "fhir" UP/DOWN to /actuator/health
│   └── CloudWatchMetricsConfig.java  sends caresummary.* metrics to AWS CloudWatch (when enabled)
└── common/                    error handling -> JSON problem details
```

---

## 6. Tests (52)

| Test | What it proves |
|------|----------------|
| `FhirMapperTest` | FHIR → DTO conversion: names, ages, dates, blood pressure values |
| `SourceCheckerTest` | made-up sources are removed; lines without real sources are unverified |
| `PatientFactsTest` | the AI never gets the name, id or birth date |
| `FhirWorkersTest` | tasks really run at the same time (3 × 400 ms finish in under 1 s), on different threads; results keep their order; a task's error is passed on; a stuck task times out; the pool is measured |
| `CloudWatchFilterTest` | only `caresummary.*` metrics would be sent to CloudWatch |
| `ActuatorTest` | health is open but shows only UP/DOWN; admin sees which part is down; metrics need ADMIN; the thread pool shows up in metrics |
| `ApiSecurityTest` | (local login mode) no token → 401; fake token → 401; doctor can't see audit; admin can't see patients; bad id → 400; unknown → 404; logins and views are audited |
| `KeycloakTokenValidationTest` | real RS256 tokens signed by a test key: good token passes; token signed by another key, from another realm (`iss`), for another app (`aud`, e.g. an ID token), with no `aud`, or expired → rejected |
| `KeycloakRoleConverterTest` | only `DOCTOR` / `ADMIN` from `realm_access.roles` become roles; Keycloak's built-in roles are ignored; no claim = no roles; names must match exactly |
| `KeycloakModeApiTest` | (keycloak mode) `/api/auth/config` is public; audit log shows `doctor`, not the Keycloak user id; a user with no role can log in but gets 403 everywhere; rejected token → 401; there is no password login endpoint |

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
- **The AI request spun forever.** A thread dump (`jstack`) showed the Google client waiting for
  response headers with no timeout. I gave Spring AI my own Gemini `Client` bean with a 60-second timeout
  (`GeminiClientConfig`), so a stuck call now returns a clear 503 "try again". Testing Gemini directly
  showed the main Flash models were hanging even for "Say OK", while Flash Lite answered in 2 seconds,
  so the app now uses Flash Lite (configurable with `GEMINI_MODEL`).
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

**What happens if the AI service is slow or down?**
Every Gemini call has a 60-second timeout. On failure the API returns `503` with a clear message,
the UI shows "try again", and the patient records still work because they don't depend on the AI.

**What would you add next?**
A real user database or hospital SSO, login rate limiting, Flyway database migrations instead of
`ddl-auto=update`, a persistent summary cache, and an evaluation set to measure summary quality.

---

## 9. Concurrency: loading a patient with parallel FHIR calls

**The problem.** Opening one patient needs 3 searches on the FHIR server: conditions, medicines,
test results. They don't depend on each other. Done one after another, the doctor waits A + B + C.

**The fix.** `PatientService` first reads the patient (so an unknown id gets a fast 404), then gives the
3 searches to `FhirWorkers`, which runs them **at the same time**. Now the wait is only the slowest one.

```java
List<Callable<List<ClinicalItem>>> searches = List.of(
        () -> ...conditions...,
        () -> ...medicines...,
        () -> ...observations...);
List<List<ClinicalItem>> results = workers.runAll(searches);   // same order as the list
```

Inside `FhirWorkers`:

| Piece | What it does | Why |
|-------|--------------|-----|
| `Executors.newFixedThreadPool(6, ...)` | 6 reusable worker threads named `fhir-worker-1..6` | creating a thread per request is slow; a **fixed** size means a busy moment can't open hundreds of connections to FHIR |
| `pool.invokeAll(tasks, 20, SECONDS)` | starts every task, waits for all, cancels any still running after 20 s | one stuck call can't hang the request forever |
| `Future.get()` | gets each result, in task order | the results list matches the task list |
| `ExecutionException` → `getCause()` | throws the task's own exception | `ApiExceptionHandler` still maps "FHIR down" to 503, not a vague 500 |
| `CancellationException` → `FhirTimeoutException` | a timed-out task | becomes HTTP **504** |
| `InterruptedException` → `Thread.currentThread().interrupt()` | keeps the interrupt flag | the standard rule: never swallow an interrupt |
| `destroy()` → `shutdownNow()` | stops the threads when the app stops | no leaked threads |

**Is it thread-safe?** Each task only reads from FHIR and builds its **own** new list, so the threads share no
changing data. The HAPI FHIR client is made to be shared between threads (it uses a connection pool).

**Proof it works (real run, 2026-11-19):** 3 patient loads = 9 FHIR tasks. The tasks' times added up to about
6.0 s, but the 3 loads took only 2.4 s in total, because the searches overlapped.

---

## 10. Monitoring: health checks, metrics and AWS CloudWatch

**Health check: `GET /actuator/health`** (Spring Boot Actuator)
- `UP` (200) or `DOWN` (503). It checks PostgreSQL and the FHIR server (`FhirHealthIndicator` asks FHIR
  for just the patient count with `_summary=count`, which is cheap).
- Open to everyone, so load balancers and uptime monitors can call it, but it shows **only** UP/DOWN.
  An ADMIN token shows which part is down and why.

**Metrics: `GET /actuator/metrics/...`** (Micrometer, ADMIN only)
- `caresummary.patient.load`: how long loading a patient takes, with the tag `outcome=success|error`
  (a `Timer` started before and stopped in a `finally`, so failures are measured too).
- `executor`, `executor.active`, `executor.queued`, `executor.pool.core` with the tag `name=fhir.workers`:
  the thread pool (`ExecutorServiceMetrics.monitor` wraps the pool).
- Plus the automatic ones: HTTP requests, JVM memory, garbage collection, database connections.

**AWS CloudWatch** (`CloudWatchMetricsConfig`, off unless `CLOUDWATCH_ENABLED=true`)
- Micrometer's CloudWatch registry sends metrics to AWS every minute (`PutMetricData`). In the AWS console:
  CloudWatch → Metrics → `CareSummaryAI` → graphs, and you can add an **alarm** (e.g. email me if the average
  load time is over 3 s).
- Only `caresummary.*` metrics are sent (a `MeterFilter`). Each custom metric costs money after the free 10,
  and JVM/HTTP metrics would be dozens. The patient timer is 2 outcomes × 4 statistics = 8 metrics: inside the free tier.
- A local `SimpleMeterRegistry` runs next to it (Spring Boot joins the two), so `/actuator/metrics` still shows everything.
- AWS keys come from `.env` (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) or the normal AWS login on the PC.

**Turn it on (about 10 minutes, free tier):**
1. AWS console → IAM → Users → Create user `caresummary-metrics` (no console access).
2. Permissions → Create inline policy (JSON):
   `{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":"cloudwatch:PutMetricData","Resource":"*"}]}`
   (only this one action: least privilege).
3. Security credentials → Create access key → "Application running outside AWS".
4. Put them in `.env`: `CLOUDWATCH_ENABLED=true`, `AWS_REGION=ap-south-1`, `AWS_ACCESS_KEY_ID=...`, `AWS_SECRET_ACCESS_KEY=...`
5. Start the app, open a few patients, wait 1–2 minutes → CloudWatch → Metrics → All metrics → `CareSummaryAI`.

---

## 11. Interview questions: concurrency and monitoring

**Why a thread pool and not `new Thread()` each time?**
Threads are expensive to create. A pool reuses them, and a fixed size limits how many FHIR calls run at once,
so we protect the FHIR server and our memory.

**What is the difference between `Runnable` and `Callable`?**
`Runnable` returns nothing and can't throw checked exceptions. `Callable<T>` returns a value and can throw.
I need the results, so I used `Callable`.

**What is a `Future`?**
A "ticket" for a result that will be ready later. `get()` waits for it. If the task failed, `get()` throws
`ExecutionException`, and the real error is inside it (`getCause()`).

**Why not `CompletableFuture`?**
It also works (`supplyAsync(..., pool)`, then `allOf(...).join()`). `invokeAll` with a timeout was simpler
for "run these N tasks, wait for all, give up after 20 s". `CompletableFuture` is better when you chain steps
("when A finishes, do B").

**What is a race condition? Could you have one here?**
Two threads changing the same data at the same time, so the result depends on timing. Here each task builds its
own new list and shares nothing that changes, so there is no race. If they shared a list, I would use a
thread-safe collection or a lock.

**What is a deadlock?**
Two threads each waiting for something the other one holds, so both wait forever. Our tasks don't wait for each
other or hold locks, and there is a timeout anyway.

**What happens if FHIR is down or slow?**
Down: the task throws `FhirClientConnectionException`, I re-throw that same exception, and the user gets 503.
Slow: after 20 s `invokeAll` cancels the task and the user gets 504. The health check also turns DOWN.

**Why is `/actuator/health` public but without details?**
Monitors and load balancers must call it without logging in. The details (error messages, database type)
could help an attacker, so only ADMIN sees them.

**Why not send all metrics to CloudWatch?**
Cost and noise. Each custom metric is billed after the free tier, so I filter to the ones that matter for this
app: patient load time and errors.

**Metrics vs logs vs traces?**
Metrics = numbers over time (how slow, how many errors) for graphs and alarms. Logs = what happened in one
request. Traces = one request's path across services. Here I added metrics and health checks.

---

## 12. Login with Keycloak (OAuth 2.0 + OpenID Connect)

### The big idea

Before, our API did everything: kept the users, checked passwords, made tokens. Now that job moves to
**Keycloak**, a separate login server (an "identity provider", like the ones big companies use for single sign-on).

| Word | In this app | Simple meaning |
|------|-------------|----------------|
| **Authorization server / identity provider** | Keycloak (`:8180`) | Keeps users, passwords, roles. Shows the login page. Gives out signed tokens. |
| **Client** | React app, client id `caresummary-ui` | The app that wants a token for the user. |
| **Resource server** | Our Spring Boot API | Holds the data. Accepts a request only with a valid token. |
| **Realm** | `caresummary` | One separate "space" in Keycloak: its own users, roles, clients. |
| **OAuth 2.0** | | The rules for getting an **access token** (what you are allowed to call). |
| **OpenID Connect (OIDC)** | | A layer on top of OAuth 2.0 that also says **who you are** (the **ID token**) and adds discovery, nonce and logout. |

Why this is better:
- **Our API never sees a password.** No password database to leak, no login endpoint to attack.
- **One login for many apps** (single sign-on). Add a second app, and users don't make a new account.
- **Keycloak gives us** password hashing, brute-force lock-out (5 tries), login event logs, password reset, MFA
  and "log in with Google/Microsoft" (we don't use those, but they are one setting away).
- **Tokens are signed with a private key (RS256).** Our API only needs the *public* key, so it can't make tokens,
  only check them. With the old shared secret (HS256), anyone who can check tokens can also make them.

### The login flow, step by step (Authorization Code + PKCE)

```
 Browser (React :5173)                Keycloak (:8180)                    API (:8080)
 1. click "Log in with Keycloak"
    make verifier, state, nonce
    challenge = SHA-256(verifier)
    -- redirect: /auth?client_id&redirect_uri&state&nonce&code_challenge -->
                                      2. login page, user types password
                                         (password checked HERE only)
    <-- redirect: localhost:5173/?code=XYZ&state=... --
 3. state the same as I sent? yes
    POST /token: code + verifier --->  4. SHA-256(verifier) == challenge?
                                         code works once, short life
    <-- access token + ID token --
 5. nonce in ID token the same? yes
 6. GET /api/auth/me   Authorization: Bearer <access token> --------------->  7. check token:
                                                                              signature (JWKS public key),
                                                                              iss, exp, aud = caresummary-api
    <------------------------------- {"username":"doctor","roles":["DOCTOR"]} --
 8. every API call sends the same access token (30 min life)
```

File: `caresummary-ui/src/oidc.ts` (each step is commented). API side: `KeycloakConfig.java`.

### Why each check exists (the security part interviewers ask about)

- **PKCE (code verifier / code challenge).** A browser app can't keep a client secret (anyone can read
  the JavaScript). Without PKCE, someone who steals the `code` (from a log, browser history, or a bad app on the phone)
  could swap it for tokens. With PKCE, they also need the random `verifier`, which never left our browser tab.
  We use `S256` (a hash), and Keycloak is set to **require** it for this client.
- **`state`.** A random value we send and check when we come back. Stops an attacker from making your browser
  finish *their* login (login CSRF).
- **`nonce`.** A random value that Keycloak puts inside the ID token. Stops an old ID token being replayed.
- **Signature (JWKS).** Keycloak publishes its public keys at `/realms/caresummary/protocol/openid-connect/certs`.
  Spring downloads and caches them, and picks the right one by the token's `kid` (key id). So Keycloak can
  **rotate keys** without us changing anything. We also allow **only RS256**, so a token saying `alg: none` or
  `HS256` is refused.
- **`iss` (issuer).** Must be exactly `http://localhost:8180/realms/caresummary`. A token from another realm
  or another Keycloak is refused.
- **`aud` (audience).** Must contain `caresummary-api`. Keycloak adds it with an "audience mapper" on the
  `caresummary-ui` client. This stops a token made for **another app** being used against our API, and also
  stops someone sending the **ID token** instead of the access token (the ID token's audience is `caresummary-ui`).
- **`exp`.** Access tokens live 30 minutes (60 s clock difference allowed).

### Roles: from Keycloak to `hasRole("DOCTOR")`

Keycloak puts roles in a nested claim: `"realm_access": {"roles": ["DOCTOR", "offline_access", ...]}`.
`KeycloakRoleConverter` keeps only the roles this app knows (`DOCTOR`, `ADMIN`) and turns them into
`ROLE_DOCTOR` / `ROLE_ADMIN`. The access rules in `SecurityConfig` did not change at all.

The user's name comes from `preferred_username` (`doctor`), not `sub` (a random-looking Keycloak id),
so the audit log stays readable.

**Authentication vs authorization:** Keycloak does *authentication* (who are you?). Our API still does
*authorization* (what may you do?): the `nurse` user logs in fine but has no role, so the API answers 403 to everything.

### Logout

"Log out" does two things: forgets the token in our app, then sends the browser to Keycloak's
`end_session_endpoint` with the ID token as a hint, so the **Keycloak session** ends too. Otherwise the next
"Log in" would sign you straight back in without a password (that is single sign-on working, but not what
"log out" should do). When a token just **expires** (401), we only forget it here; clicking "Log in" then
uses the still-open Keycloak session, so no password is needed.

### What a real (production) setup would add

- **HTTPS everywhere** (Keycloak runs `start-dev` here: plain HTTP + a built-in database). Production:
  `start` mode, PostgreSQL for Keycloak, real hostname, TLS.
- **Token storage.** We keep the access token in `sessionStorage` (cleared when the tab closes). If an attacker
  could run JavaScript on our page (XSS), they could read it. The stronger pattern is a **BFF**
  (backend-for-frontend): the server keeps the tokens and the browser only gets an `HttpOnly` cookie.
- **Refresh tokens / silent renew**, so the user isn't sent back to the login button every 30 minutes.
- **Users from a real directory** (LDAP / Active Directory) and **MFA** for clinicians; both are Keycloak settings.
- **Short token life + revocation:** an access token stays valid until it expires even if the user is disabled.
  Shorter life or token introspection solves that.

### Two login modes

| `AUTH_MODE` | Who logs users in | Token signed with | Use it for |
|-------------|-------------------|-------------------|-----------|
| `keycloak` (default) | Keycloak | Keycloak's RSA private key (RS256) | normal use |
| `local` | our API (`POST /api/auth/login`) | shared secret (HS256) | running without Docker/Keycloak, low-RAM machines |

Spring picks the beans with `@ConditionalOnProperty(name = "app.security.mode", ...)`: `KeycloakConfig` or
`LocalLoginConfig`. Each gives a `JwtDecoder` + `JwtAuthenticationConverter`; `SecurityConfig` uses whichever exists.

### Where things are in Keycloak

- Realm file: `keycloak/caresummary-realm.json` (loaded on first start with `--import-realm`).
- Admin console: http://localhost:8180 (admin / admin). Realm `caresummary` -> *Users*, *Realm roles*,
  *Clients -> caresummary-ui*, and *Events* (every login and failed login).
- Discovery document: http://localhost:8180/realms/caresummary/.well-known/openid-configuration

---

## 13. Interview questions: OAuth 2.0, OpenID Connect, Keycloak

**What is the difference between OAuth 2.0 and OpenID Connect?**
OAuth 2.0 is about *authorization*: getting an access token to call an API. It does not say who the user is.
OpenID Connect adds *authentication* on top: an ID token (a JWT about the user), a `userinfo` endpoint,
discovery and logout. I use both: the ID token for the login check (nonce) and logout, the access token for the API.

**Access token vs ID token?**
The access token is for the **API** (audience `caresummary-api`); the API checks it on every call.
The ID token is for the **client app**, to know who logged in. Never send the ID token to an API; my API rejects
it because its audience is wrong.

**Why Authorization Code + PKCE and not the Implicit flow?**
Implicit put tokens straight in the URL, where they leak to history and logs. It is no longer recommended
(OAuth 2.0 Security Best Current Practice). Code + PKCE returns only a one-time code in the URL, and the code
is useless without the verifier.

**Why not the "password" grant (send username + password to Keycloak's token endpoint)?**
Then our app sees the password again, which defeats the point, and it can't do MFA or SSO. It is deprecated.
I turned it off for this client (`directAccessGrantsEnabled: false`).

**What is a public client vs a confidential client?**
A confidential client (a server) can keep a client secret. A public client (browser or mobile app) can't,
because users can read its code. My React app is a public client, so it must use PKCE.

**How does your API check a token without calling Keycloak every time?**
The token is a signed JWT. The API downloads Keycloak's public keys (JWKS) once, caches them, and checks the
signature locally, plus `iss`, `aud` and `exp`. That's fast and works even under heavy load. The trade-off: a token
stays valid until it expires, so I keep the life short (30 min). For instant revocation you would use token
introspection (ask Keycloak each time), which is slower.

**What happens when Keycloak rotates its signing key?**
The new token has a new `kid`. Spring doesn't find it in the cache, downloads the JWKS again and finds it.
No restart, no code change.

**What is the `aud` check for? Give an example attack.**
Say the hospital has a second app, "Appointments", in the same realm. A user's token for Appointments is signed by
the same Keycloak. Without the audience check, my API would accept it. With it, only tokens made for
`caresummary-api` are accepted.

**What are `state` and `nonce` for?**
`state` protects the redirect back to my app (login CSRF). `nonce` protects the ID token from replay. Both are random,
one-use values I make before the redirect and check after.

**Where do you store the token in the browser? Is that safe?**
In `sessionStorage`, so it's gone when the tab closes and isn't sent automatically like a cookie (no CSRF).
The risk is XSS: injected JavaScript could read it. React escapes output by default and I don't use
`dangerouslySetInnerHTML`. For a production health app I would move to a BFF with `HttpOnly` cookies.

**What is SAML and how is it different?**
An older single sign-on standard using XML messages, common in big enterprises and universities. OIDC uses JSON and
JWTs and is simpler for web and mobile apps and APIs. Keycloak supports both, and can act as a bridge: users log in
with a company's SAML identity provider, and my app still gets OIDC tokens.

**Authentication vs authorization in your app?**
Authentication (who you are) = Keycloak. Authorization (what you may do) = my API's rules (`hasRole("DOCTOR")`).
The `nurse` demo user proves they are separate: logged in, but forbidden everywhere.

**How did you test it?**
Three levels. Unit: the role converter. Token rules: I make an RSA key pair in the test, sign real JWTs and check
that wrong key, wrong issuer, wrong audience, no audience and expired tokens are all rejected, using the **same**
validator the app uses. API: the whole Spring app in keycloak mode with the decoder mocked, to check roles,
403s, the audit username and that no password endpoint exists. Then I logged in for real in the browser as
doctor, admin and nurse.
