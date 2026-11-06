# CareSummary AI

A Spring Boot app that gives clinicians a short, **source-linked** summary of a patient's health record.
It reads standard **FHIR R4** health data, and every line of the AI summary points back to the record it came from, so a doctor can check it.

> ⚠️ Uses **synthetic (fake) patients only**, generated with [Synthea](https://github.com/synthetichealth/synthea). Not a medical device and not medical advice.

## Status

| Part | State |
|------|-------|
| FHIR server + 34 synthetic patients | ✅ Done |
| Patient REST API (list, search, detail) | ✅ Done |
| AI summary with source links (Spring AI) | 🔜 Next |
| Ask questions about one patient | 🔜 Next |
| React UI | 🔜 Next |
| Login + roles, audit log, data masking | 🔜 Planned |
| CI with GitHub Actions | 🔜 Planned |

## How it works

```
React UI  ──►  Spring Boot API (port 8080)  ──►  HAPI FHIR server (port 8090, Docker)
                     │                                  ▲
                     └──►  LLM (summary, Q&A)           └── Synthea fake patients
```

- **FHIR** is the global standard for health data. Each record (Patient, Condition, MedicationRequest, Observation) is a JSON "resource".
- The API uses the **HAPI FHIR client** to read records and turns them into small DTOs.
- Each item keeps its FHIR reference (e.g. `Condition/2266`). The AI summary will cite these.

## API

| Method | Path | What it returns |
|--------|------|-----------------|
| GET | `/api/patients?name=agn` | Patient list (optional name search) |
| GET | `/api/patients/{id}` | Patient + conditions + medications + latest vitals/labs |

Errors use standard JSON problem details: `404` (unknown patient), `400` (bad id), `503` (FHIR server down).

## Run it locally

Needs: Java 17+, Docker Desktop.

```bash
docker compose up -d                 # start the FHIR server (first start ~1-2 min)
```

Make fake patients and load them (first time only):

```bash
java -jar tools/synthea-with-dependencies.jar -p 30 -s 42 --exporter.baseDirectory ./fhir-data Massachusetts
```

```powershell
.\load-patients.ps1
```

Start the API:

```bash
cd caresummary-api
./mvnw spring-boot:run
```

Open http://localhost:8080/api/patients

## Tech

Java 17 · Spring Boot 4 · HAPI FHIR 8 · Docker · JUnit 5 · AssertJ

## Data rules

- Synthetic data only. No real patient data, ever.
- `tools/` and `fhir-data/` are not committed (large downloaded/generated files).
