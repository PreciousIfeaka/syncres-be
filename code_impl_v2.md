# CV Matcher + Job Application Tracker — Implementation Guide (v2)

> This document is a complete, text-based implementation guide intended to be fed directly to an AI coding agent.
> Follow sections in order. No code is included — every implementation decision is described precisely so the agent can generate the correct code.

---

## Project Overview

Build a Spring Boot REST API that:

1. Accepts a CV (PDF or DOCX upload) and a job description (URL or raw text)
2. Extracts text from both inputs
3. Uses the **Google Gemini API** to semantically score the match (0–100)
4. Returns a structured gap report (matched skills, missing skills, weak matches)
5. If the score meets or exceeds a configurable threshold (default 65), uses Gemini to retailor the CV and generates a PDF output
6. Records every job application with the company name, the JD used, and the CV version submitted
7. Allows users to track application status through a defined state machine with full audit history
8. **Access model:** CV matching is available to anonymous (session-based) users. All other features (CV upload/management, application tracking, stats, retailored PDF download) require a registered, authenticated user.
9. **Background jobs:** All long-running operations (Gemini scoring, CV rewriting, PDF generation) run as JobRunr background jobs. Clients poll a job-status endpoint to retrieve results.
10. **Email:** OTP-based email verification during signup and a forgot-password flow.

---

## Stack

* **Language:** Java 21 (use virtual threads — enable with `spring.threads.virtual.enabled=true`)
* **Framework:** Spring Boot 3.3+
* **Database:** PostgreSQL 16
* **ORM:** Spring Data JPA + Hibernate
* **Migrations:** Flyway
* **AI:** Google Gemini API (REST, not SDK — use Spring `WebClient`)
* **CV parsing:** Apache PDFBox 3.x (PDF), Apache POI 5.x (DOCX/DOC)
* **JD scraping:** Jsoup
* **PDF generation:** Flying Saucer (xhtmlrenderer) with Thymeleaf HTML templates
* **Auth:** Spring Security with JWT (jjwt 0.12.x) for authenticated users; HTTP session for anonymous match access
* **Background jobs:** JobRunr (Spring Boot starter) backed by the same PostgreSQL instance
* **Email:** Spring Boot Starter Mail (SMTP)
* **Utilities:** Lombok, Jackson

---

## Project Structure

Organise into the following packages under `com.precious.syncres`:

```
.github/workflows
config/          — Spring config beans (Security, WebClientConfig, StorageConfig,
                   JwtTokenProvider, JwtAuthFilter, UserDetailsServiceImpl,
                   SessionMatchFilter, JobRunrConfig)
entities/        — User, ApplicationNote, ApplicationStatusHistory, JobApplication,
                   JdSnapshot, CvDocument, OtpToken, MatchJobResult
repositories/    — UserRepository, NoteRepository, StatusHistoryRepository,
                   ApplicationRepository, JdSnapshotRepository, CvDocumentRepository,
                   OtpTokenRepository, MatchJobResultRepository
dtos/
services/
controllers/
proxies/
shared/dto/      — All request and response DTOs
shared/exception/— GlobalExceptionHandler, AppException, ErrorCode enum
shared/util/     — SecurityUtils, HmacUtils, OtpUtils
```

Resources:

```
db/migration/    — Flyway SQL files V1__ through V9__
templates/       — cv-template.html (Thymeleaf template for PDF generation)
                   email-otp.html, email-reset.html (email templates)
application.properties
application-prod.properties
```

---

## 1. Maven Dependencies

Include the following dependencies in `pom.xml`:

* `spring-boot-starter-web`
* `spring-boot-starter-data-jpa`
* `spring-boot-starter-security`
* `spring-boot-starter-validation`
* `spring-boot-starter-thymeleaf`
* `spring-boot-starter-mail`
* `spring-boot-starter-webflux` — for WebClient (Gemini calls)
* `software.amazon.awssdk/s3` — for file storage
* `postgresql` (runtime)
* `flyway-core`
* `flyway-database-postgresql`
* `jjwt-api`, `jjwt-impl`, `jjwt-jackson` — version 0.12.6
* `pdfbox` — version 3.0.3
* `poi-ooxml` — version 5.3.0
* `poi-scratchpad` — version 5.3.0 (for legacy .doc files)
* `jsoup` — version 1.18.1
* `flying-saucer-pdf` — version 9.8.1
* `openpdf` — version 2.0.3 (Flying Saucer dependency)
* `jobrunr-spring-boot-3-starter` — version 7.x (latest stable)
* `lombok`
* `jackson-databind`
* `spring-boot-starter-test` (test scope)
* `testcontainers postgresql` (test scope)

---

## 2. Application Configuration

### `application.properties` keys to define:

**Server**

* `server.port=5002`

**Database**

* `spring.datasource.url` — read from env `DB_URL`
* `spring.datasource.username` — read from env `DB_USER`
* `spring.datasource.password` — read from env `DB_PASS`
* `spring.jpa.hibernate.ddl-auto=validate`
* `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect`
* `spring.jpa.open-in-view=false`

**Flyway**

* `spring.flyway.enabled=true`
* `spring.flyway.locations=classpath:db/migration`

**Session**

* `spring.session.store-type=jdbc` — use JDBC-backed sessions (stored in PostgreSQL)
* `server.servlet.session.timeout=60m` — sessions expire after 60 minutes of inactivity
* `spring.session.jdbc.initialize-schema=never` — Flyway manages the session tables (V8)

**JWT**

* `app.jwt.secret` — read from env `JWT_SECRET` (min 64 chars for HS512)
* `app.jwt.expiration-ms=86400000` (24 hours)

**Gemini**

* `gemini.api-key` — read from env `GEMINI_API_KEY`
* `gemini.model=gemini-1.5-pro`
* `gemini.max-output-tokens=8192`
* `gemini.temperature=0.2`

**File Storage**

* `s3.access-key-id` — read from env `S3_ACCESS_KEY_ID`
* `s3.secret-access-key` — read from env `S3_SECRET_ACCESS_KEY`
* `s3.region` — read from env `S3_REGION`
* `s3.bucket-name` — read from env `S3_BUCKET_NAME`
* `app.storage.pdf-expiry-hours=24`

**Matcher**

* `app.matcher.threshold=65`
* `app.matcher.max-cv-chars=15000`
* `app.matcher.max-jd-chars=10000`

**Multipart**

* `spring.servlet.multipart.max-file-size=10MB`
* `spring.servlet.multipart.max-request-size=12MB`

**Virtual threads**

* `spring.threads.virtual.enabled=true`

**Email (SMTP)**

* `spring.mail.host` — read from env `MAIL_HOST`
* `spring.mail.port` — read from env `MAIL_PORT` (typically 587)
* `spring.mail.username` — read from env `MAIL_USERNAME`
* `spring.mail.password` — read from env `MAIL_PASSWORD`
* `spring.mail.properties.mail.smtp.auth=true`
* `spring.mail.properties.mail.smtp.starttls.enable=true`
* `app.mail.from` — read from env `MAIL_FROM` (e.g. `no-reply@cvmatcher.com`)
* `app.otp.expiry-minutes=10` — OTP validity window

**JobRunr**

* `org.jobrunr.background-job-server.enabled=true`
* `org.jobrunr.background-job-server.worker-count=4`
* `org.jobrunr.dashboard.enabled=false` — enable only in development
* `org.jobrunr.database.type=sql` — JobRunr uses the same PostgreSQL datasource

### `application-prod.properties`

Override datasource URL to use the Docker Compose service name (`postgres` instead of `localhost`). Set log levels to WARN for root, INFO for `com.cvmatcher`. Set `org.jobrunr.dashboard.enabled=false`.

---

## 3. Database Schema (Flyway Migrations)

Create nine migration files executed in order.

### V1__create_users.sql

Table: `users`

* `id` — UUID primary key, default `gen_random_uuid()`
* `email` — VARCHAR(255), NOT NULL, UNIQUE
* `password_hash` — VARCHAR(255), NOT NULL
* `full_name` — VARCHAR(255), nullable
* `email_verified` — BOOLEAN NOT NULL DEFAULT false
* `created_at` — TIMESTAMPTZ NOT NULL DEFAULT now()
* `updated_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_users_email` on `email`

### V2__create_cv_documents.sql

Table: `cv_documents`

* `id` — UUID primary key
* `user_id` — UUID NOT NULL, FK → users(id) ON DELETE CASCADE
* `original_filename` — VARCHAR(255) NOT NULL
* `storage_path` — VARCHAR(500) NOT NULL
* `file_type` — VARCHAR(10) NOT NULL, CHECK IN ('PDF','DOCX','DOC')
* `file_size_bytes` — BIGINT nullable
* `extracted_text` — TEXT nullable
* `uploaded_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_cv_documents_user_id` on `user_id`

### V3__create_jd_snapshots.sql

Table: `jd_snapshots`

* `id` — UUID primary key
* `user_id` — UUID nullable, FK → users(id) ON DELETE CASCADE — **nullable to support anonymous match sessions**
* `session_id` — VARCHAR(128) nullable — populated for anonymous matches; null for authenticated users
* `source_url` — VARCHAR(2000) nullable
* `company_name` — VARCHAR(255) nullable
* `role_title` — VARCHAR(255) nullable
* `raw_text` — TEXT NOT NULL
* `captured_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Constraint: CHECK that at least one of `user_id` or `session_id` is NOT NULL.

Index: `idx_jd_snapshots_user_id` on `user_id`
Index: `idx_jd_snapshots_session_id` on `session_id`

### V4__create_job_applications.sql

First create the enum type:

```
application_status ENUM: SAVED, APPLIED, PHONE_SCREEN, INTERVIEW, FINAL_ROUND, OFFER, ACCEPTED, DECLINED, REJECTED, WITHDRAWN
```

Table: `job_applications`

* `id` — UUID primary key
* `user_id` — UUID NOT NULL, FK → users(id) ON DELETE CASCADE
* `cv_document_id` — UUID nullable, FK → cv_documents(id) ON DELETE SET NULL
* `jd_snapshot_id` — UUID nullable, FK → jd_snapshots(id) ON DELETE SET NULL
* `company_name` — VARCHAR(255) NOT NULL
* `role_title` — VARCHAR(255) nullable
* `application_status` — application_status enum, NOT NULL, DEFAULT 'SAVED'
* `match_score` — SMALLINT nullable, CHECK BETWEEN 0 AND 100
* `match_summary` — TEXT nullable
* `matched_skills` — TEXT[] nullable
* `missing_skills` — TEXT[] nullable
* `retailored_cv_path` — VARCHAR(500) nullable
* `jd_url` — VARCHAR(2000) nullable
* `applied_at` — TIMESTAMPTZ nullable
* `deleted_at` — TIMESTAMPTZ nullable (soft delete)
* `created_at` — TIMESTAMPTZ NOT NULL DEFAULT now()
* `updated_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Indexes:

* `idx_job_applications_user_id` on `user_id`
* `idx_job_applications_status` on `(user_id, application_status)`
* `idx_job_applications_company` on `(user_id, company_name)`
* `idx_job_applications_created_at` on `(user_id, created_at DESC)`

### V5__create_status_history_and_notes.sql

Table: `application_status_history`

* `id` — UUID primary key
* `application_id` — UUID NOT NULL, FK → job_applications(id) ON DELETE CASCADE
* `from_status` — application_status nullable
* `to_status` — application_status NOT NULL
* `note` — TEXT nullable
* `changed_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_status_history_application_id` on `(application_id, changed_at DESC)`

Table: `application_notes`

* `id` — UUID primary key
* `application_id` — UUID NOT NULL, FK → job_applications(id) ON DELETE CASCADE
* `content` — TEXT NOT NULL
* `note_type` — VARCHAR(50) DEFAULT 'GENERAL', CHECK IN ('GENERAL','INTERVIEW_PREP','RECRUITER_CONTACT','SALARY','FOLLOW_UP')
* `created_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_notes_application_id` on `(application_id, created_at DESC)`

### V6__create_otp_tokens.sql

Table: `otp_tokens`

* `id` — UUID primary key, default `gen_random_uuid()`
* `user_id` — UUID NOT NULL, FK → users(id) ON DELETE CASCADE
* `token_hash` — VARCHAR(255) NOT NULL — store BCrypt hash of the 6-digit OTP, never the raw value
* `purpose` — VARCHAR(20) NOT NULL, CHECK IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')
* `expires_at` — TIMESTAMPTZ NOT NULL
* `used` — BOOLEAN NOT NULL DEFAULT false
* `created_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_otp_tokens_user_id` on `(user_id, purpose)`

### V7__create_match_job_results.sql

Table: `match_job_results`

* `id` — UUID primary key, default `gen_random_uuid()`
* `jobrunr_job_id` — VARCHAR(255) NOT NULL — the JobRunr job ID, used for polling
* `user_id` — UUID nullable, FK → users(id) ON DELETE CASCADE — null for anonymous sessions
* `session_id` — VARCHAR(128) nullable — for anonymous sessions
* `status` — VARCHAR(20) NOT NULL DEFAULT 'PENDING', CHECK IN ('PENDING','PROCESSING','COMPLETED','FAILED')
* `result_json` — TEXT nullable — serialised `MatchResponseDto` stored on completion
* `error_message` — TEXT nullable — populated on failure
* `created_at` — TIMESTAMPTZ NOT NULL DEFAULT now()
* `completed_at` — TIMESTAMPTZ nullable

Index: `idx_match_job_results_jobrunr_id` on `jobrunr_job_id`
Index: `idx_match_job_results_session_id` on `session_id`

### V8__create_spring_session_tables.sql

Create the Spring Session JDBC tables. Use the standard schema for PostgreSQL from the Spring Session project:

* `SPRING_SESSION` — stores session metadata (primary key `PRIMARY_ID`, also unique on `SESSION_ID`)
* `SPRING_SESSION_ATTRIBUTES` — stores session attributes (FK to `SPRING_SESSION` on cascade delete)

These are the exact table/column names expected by Spring Session's JDBC store. Copy the official DDL from the Spring Session documentation for PostgreSQL.

### V9__create_jobrunr_tables.sql

Create all JobRunr internal tables needed for the SQL storage backend. Copy the official PostgreSQL DDL from the JobRunr documentation. Tables include: `jobrunr_jobs`, `jobrunr_recurring_jobs`, `jobrunr_background_job_servers`, `jobrunr_job_stats`, `jobrunr_metadata`.

---

## 4. JPA Entities

### User

Maps to `users`. Fields include `emailVerified` (boolean).

### CvDocument

Maps to `cv_documents`. All fields map directly. Use `@GeneratedValue(strategy = GenerationType.UUID)`. Use Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.

### JdSnapshot

Maps to `jd_snapshots`. Includes both `userId` (nullable UUID) and `sessionId` (nullable String).

### JobApplication

Maps to `job_applications`. `applicationStatus` uses `@Enumerated(EnumType.STRING)` with `columnDefinition = "application_status"`. `matchedSkills` and `missingSkills` use `columnDefinition = "TEXT[]"`. Add a `@PreUpdate` method setting `updatedAt = OffsetDateTime.now()`.

### ApplicationStatus (enum)

Define all status values. Add `allowedTransitions()` returning `Set<ApplicationStatus>`:

* SAVED → {APPLIED, WITHDRAWN}
* APPLIED → {PHONE_SCREEN, REJECTED, WITHDRAWN}
* PHONE_SCREEN → {INTERVIEW, REJECTED, WITHDRAWN}
* INTERVIEW → {OFFER, FINAL_ROUND, REJECTED, WITHDRAWN}
* FINAL_ROUND → {OFFER, REJECTED, WITHDRAWN}
* OFFER → {ACCEPTED, DECLINED}
* ACCEPTED, DECLINED, REJECTED, WITHDRAWN → {} (terminal)

Add `isTerminal()` returning true when `allowedTransitions()` is empty.

### ApplicationStatusHistory

Maps to `application_status_history`. Both status fields use `@Enumerated(EnumType.STRING)` with `columnDefinition = "application_status"`.

### ApplicationNote

Maps to `application_notes`.

### OtpToken

Maps to `otp_tokens`. Fields: `id`, `userId`, `tokenHash`, `purpose` (enum: `EMAIL_VERIFICATION`, `PASSWORD_RESET`), `expiresAt`, `used`, `createdAt`.

### MatchJobResult

Maps to `match_job_results`. Fields: `id`, `jobrunrJobId`, `userId` (nullable), `sessionId` (nullable), `status` (enum: PENDING, PROCESSING, COMPLETED, FAILED), `resultJson`, `errorMessage`, `createdAt`, `completedAt`.

---

## 5. Repositories

All repositories extend `JpaRepository<Entity, UUID>`.

### CvDocumentRepository

* `findAllByUserId(UUID userId)`
* `findByIdAndUserId(UUID id, UUID userId)` — returns Optional
* `existsByIdAndUserId(UUID id, UUID userId)`

### JdSnapshotRepository

* `findAllByUserIdOrderByCapturedAtDesc(UUID userId)`
* `findByIdAndUserId(UUID id, UUID userId)` — returns Optional

### ApplicationRepository

Custom JPQL query for filtered listing (non-deleted, per user, optional status/company filters, ordered by `createdAt DESC`). Aggregate queries: `countByUserId`, `countByStatus`, `avgMatchScore`.

### StatusHistoryRepository

* `findAllByApplicationIdOrderByChangedAtDesc(UUID applicationId)`

### NoteRepository

* `findAllByApplicationIdOrderByCreatedAtDesc(UUID applicationId)`
* `findByIdAndApplicationId(UUID id, UUID applicationId)` — returns Optional

### OtpTokenRepository

* `findByUserIdAndPurposeAndUsedFalse(UUID userId, OtpPurpose purpose)` — returns Optional — retrieves the latest valid (not used) token
* `deleteAllByUserIdAndPurpose(UUID userId, OtpPurpose purpose)` — used to invalidate all existing tokens before issuing a new one

### MatchJobResultRepository

* `findByJobrunrJobId(String jobId)` — returns Optional
* `findBySessionId(String sessionId)` — returns Optional (for anonymous session polling)
* `findByUserIdOrderByCreatedAtDesc(UUID userId)` — returns list (authenticated user history)

---

## 6. DTOs

### MatchRequestDto (request)

Fields:

* `cvDocumentId` — UUID, **optional** — if null, the client must supply `cvText` directly (anonymous users do not have stored CV documents; they paste or upload text inline). If both are provided, `cvDocumentId` takes precedence for authenticated users.
* `cvText` — String, optional — raw CV text supplied directly; used when `cvDocumentId` is absent
* `jdUrl` — String, optional
* `jdText` — String, optional
* `companyName` — String, optional
* `roleTitle` — String, optional
* `saveAsApplication` — boolean, default false — **only honoured for authenticated users**; ignored silently for anonymous sessions

### MatchJobAcceptedDto (response — returned immediately on POST /api/match)

Fields:

* `jobId` — String — the JobRunr job ID; client uses this to poll for results
* `pollUrl` — String — the full URL to GET results, e.g. `/api/match/jobs/{jobId}`

### MatchResultPollDto (response — returned when polling for a job result)

Fields:

* `jobId` — String
* `status` — String: PENDING, PROCESSING, COMPLETED, FAILED
* `result` — MatchResponseDto (null unless status is COMPLETED)
* `errorMessage` — String (null unless status is FAILED)

### MatchResponseDto (response — embedded in MatchResultPollDto on completion)

Fields:

* `matchResultId` — UUID (the jd_snapshot id)
* `status` — String: "BELOW_THRESHOLD" or "MATCH_SUCCESSFUL"
* `matchScore` — int
* `threshold` — int
* `summary` — String
* `matchedSkills` — List\<String>
* `missingSkills` — List\<String>
* `weakMatches` — List of objects with `skill` and `note` String fields
* `recommendation` — String
* `retailoringOffered` — boolean
* `retailoredCv` — nested object (null if below threshold):
  * `downloadUrl` — String
  * `expiresAt` — String (ISO-8601)
  * `fileSizeKb` — Long
  * `changes` — List\<String>

### RegisterRequestDto (request)

Fields:

* `email` — String, required, valid email format
* `password` — String, required, min 8 chars
* `fullName` — String, optional

### VerifyEmailDto (request)

Fields:

* `email` — String, required
* `otp` — String, required (6 digits)

### LoginRequestDto (request)

Fields:

* `email` — String, required
* `password` — String, required

### AuthResponseDto (response)

Fields:

* `token` — String (JWT)
* `userId` — UUID
* `email` — String
* `fullName` — String

### ForgotPasswordRequestDto (request)

Fields:

* `email` — String, required

### ResetPasswordDto (request)

Fields:

* `email` — String, required
* `otp` — String, required
* `newPassword` — String, required, min 8 chars


### ApplicationCreateDto (request)
Fields:
- `companyName` (String, required)
- `roleTitle` (String, optional)
- `cvDocumentId` (UUID, optional)
- `jdSnapshotId` (UUID, optional) — set this when linking to a prior match result
- `jdUrl` (String, optional)
- `appliedAt` (OffsetDateTime, optional — defaults to now() if not provided)


### ApplicationResponseDto (response)
All JobApplication fields in camelCase. Include nested objects for status history and notes when requested via a `?include=history,notes` query param on the GET single endpoint.

### StatusUpdateDto (request)
Fields:
- `status` (ApplicationStatus, required)
- `note` (String, optional) — recorded in the status history row

### NoteCreateDto (request)
Fields:
- `content` (String, required)
- `noteType` (String, optional, defaults to GENERAL)

### StatsResponseDto (response)
Fields:
- `totalApplications` (long)
- `statusBreakdown` (Map\<String, Long\>) — status name → count
- `averageMatchScore` (Double, nullable)
- `applicationsLast30Days` (long)


Unchanged from v1. See original spec.

---

## 7. Gemini API Integration

### How the Gemini REST API works

Use the Gemini Developer API (not Vertex AI).

Base endpoint:

```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
```

The API key is passed as a query parameter, not a header. Use Spring `WebClient` (not FeignClient) since calls are made from background job threads managed by JobRunr's virtual-thread executor.

### Request body structure

```json
{
  "system_instruction": {
    "parts": { "text": "<your system prompt here>" }
  },
  "contents": [
    {
      "role": "user",
      "parts": [{ "text": "<user message here>" }]
    }
  ],
  "generationConfig": {
    "maxOutputTokens": 8192,
    "temperature": 0.2,
    "responseMimeType": "application/json"
  }
}
```

**Critical:** Set `responseMimeType` to `"application/json"` in `generationConfig`. This instructs Gemini to return pure JSON without markdown code fences, which is essential for reliable parsing.

### Response body structure

```
{
  "candidates": [
    {
      "content": {
        "parts": [
          { "text": "<the model's response text here>" }
        ]
      }
    }
  ]
}
```

Extract the text from `candidates[0].content.parts[0].text`.

### GeminiConfig bean

Create a `@Configuration` class that:
- Reads `gemini.api-key` and `gemini.model` from properties
- Defines a `WebClient` bean named `geminiWebClient` with base URL `https://generativelanguage.googleapis.com/v1beta/models/{model}`
- Sets Content-Type header to `application/json`

### GeminiClient component

Create a `@Component` class `GeminiClient` that:
- Injects the `geminiWebClient` WebClient bean, the API key, max tokens, and temperature
- Exposes a `generate(String systemInstruction, String userMessage)` method that:
  1. Builds the request body map as described above
  2. Makes a blocking POST to `:generateContent?key={apiKey}`
  3. Parses the response JSON with Jackson to extract `candidates[0].content.parts[0].text`
  4. Returns the extracted text string
  5. Throws a RuntimeException on HTTP errors or malformed responses, logging the raw response for debugging
     **Defensive parsing:** Even with `responseMimeType: application/json`, occasionally Gemini may still wrap output in markdown fences. Before parsing the returned text as JSON, strip any leading ` ```json ` and trailing ` ``` ` using a regex replace as a safety measure.

---

## 8. CV Parser Service

Create `CvParserService` as a `@Service`. It receives a `MultipartFile` and returns a plain text string.
### Behaviour

Detect file type from both the MIME type and the file extension (some browsers send incorrect MIME types for .docx files). Support:
- PDF → use PDFBox `PDDocument.load(inputStream)` then `PDFTextStripper` with `setSortByPosition(true)` for correct reading order
- DOCX → use POI `XWPFDocument` with `XWPFWordExtractor`
- DOC (legacy) → use POI `HWPFDocument` with `WordExtractor`
- Any other type → throw `IllegalArgumentException` with a clear message
### Post-processing

After extraction, normalise the text:
1. Remove null bytes (`\u0000`)
2. Normalise line endings to `\n`
3. Collapse multiple consecutive spaces/tabs to a single space
4. Collapse 3+ consecutive blank lines to 2
5. Trim leading and trailing whitespace
6. Truncate to `app.matcher.max-cv-chars` (default 15,000). Log a WARN if truncation occurs.
---

## 9. JD Scraper Service

Create `JdScraperService` as a `@Service`.

### Behaviour

Use Jsoup to fetch the URL with:
- A realistic browser User-Agent string to avoid 403 responses from job boards
- A 10-second connection timeout
- `followRedirects(true)`
  After fetching, clean the document:
1. Remove `script`, `style`, `nav`, `header`, `footer`, `iframe`, `noscript`, `aside` elements
2. Also remove elements with `aria-hidden="true"` and common cookie banner class names
3. Try to extract text from semantic main content containers first: `main`, `article`, `[role=main]`, `.job-description`, `#job-description`, `.description__text`
4. If the result is blank or less than 200 characters, fall back to `doc.body().text()`
   Normalise: collapse multiple spaces, truncate to `app.matcher.max-jd-chars`.

On any exception (connection refused, 403, 429, paywall redirect, etc.), throw a custom `JdScrapeException` with a message telling the user to paste the JD text directly instead.
---

## 10. Match Scorer Service (Gemini)

Create `MatchScorerService` as a `@Service`. It calls `GeminiClient.generate()`.

### System prompt

Instruct Gemini to act as an ATS and recruitment expert. It must:
- Return ONLY valid JSON, no markdown, no preamble
- Use the following exact JSON schema:
  ```
  {
    "overall_score": <integer 0-100>,
    "summary": <string, 2-3 sentences>,
    "matched_skills": [<string>, ...],
    "missing_skills": [<string>, ...],
    "weak_matches": [
      { "skill": <string>, "note": <string, one sentence> }
    ],
    "recommendation": <string, one sentence>
  }
  ```
- Apply this scoring guide: 80-100 excellent, 65-79 good, 50-64 moderate, 0-49 poor
- Not inflate scores — be honest about gaps
- Judge on: skills alignment, experience level, domain knowledge, role seniority requirements
### User message

Format the user turn as two clearly labelled sections:
```
## Candidate CV
{cvText}
 
## Job Description
{jdText}
 
Analyse the match and respond with the JSON object as specified.
```

### Response parsing

Parse the JSON response into an internal `MatchResult` model with fields: `overallScore`, `summary`, `matchedSkills`, `missingSkills`, `weakMatches` (list of objects), `recommendation`.

If parsing fails, throw a `RuntimeException` — do not return a partial result. The orchestrator will handle this as a 502 error.
 
---

## 11. CV Rewriter Service (Gemini)

Create `CvRewriterService` as a `@Service`. It is only called when the match score is at or above the threshold.

### System prompt

Instruct Gemini to act as a professional CV writer. It must:
- NEVER invent experience, qualifications, skills, company names, dates, or job titles the candidate does not have
- MAY: reorder bullet points, strengthen action verbs, incorporate relevant JD keywords the candidate demonstrably possesses, reframe existing experience in role-relevant language, expand the professional summary
- Return ONLY valid JSON with this exact schema:
  ```
  {
    "professional_summary": <string>,
    "experience": [
      {
        "company": <string — unchanged>,
        "title": <string — unchanged>,
        "dates": <string — unchanged>,
        "bullets": [<string>, ...]
      }
    ],
    "skills": [<string>, ...],
    "education": [
      {
        "institution": <string — unchanged>,
        "degree": <string — unchanged>,
        "year": <string — unchanged>
      }
    ],
    "changes": [<string>, ...]
  }
  ```
- The `changes` array must list every modification made as short plain-English sentences (e.g. "Reordered experience bullets to lead with distributed systems work")
### User message

Provide three sections: the original CV text, the JD text, and a summary of the top missing/weak skills identified in the match score to guide the rewriting focus.

### Response

Parse the JSON into a `RewriteResult` internal model. The `PdfGeneratorService` will consume this model.
 
---

## 12. PDF Generator Service

Create `PdfGeneratorService` as a `@Service`.

### Behaviour

1. Receive a `RewriteResult` object
2. Populate a Thymeleaf HTML template (`cv-template.html`) with the structured CV sections using `TemplateEngine.process()`
3. Render the populated HTML to PDF using Flying Saucer's `ITextRenderer`
4. Generate a UUID filename for the PDF
5. Write the PDF bytes to `{app.storage.base-path}/pdfs/{uuid}.pdf`
6. Calculate expiry as `OffsetDateTime.now().plusHours(app.storage.pdf-expiry-hours)`
7. Generate a signed download URL using HMAC-SHA256 of `{uuid}:{expiryEpochSeconds}` keyed with `app.jwt.secret`
8. Return a `PdfResult` object with: `storagePath`, `downloadUrl`, `expiresAt`, `fileSizeKb`
### Download URL format

`/api/match/download/{uuid}?expires={epochSeconds}&sig={hmacHex}`

The download endpoint validates the signature and expiry before streaming the file. Return 410 Gone if expired, 403 Forbidden if signature is invalid.

### CV HTML template (`cv-template.html`)

Use Thymeleaf syntax. The template must be a clean, single-column, ATS-friendly layout:
- No tables for layout (ATS systems often cannot parse table-based CVs)
- Use simple `div` and `p` elements
- Flying Saucer requires valid XHTML — all tags must be properly closed
- Font: a serif or clean sans-serif embedded or specified as a web-safe font
- Sections: Professional Summary, Experience (reverse chronological), Skills, Education
- Use `th:text` for all dynamic values to prevent XSS in the generated PDF
---

## 13. Email Service

Create `EmailService` as a `@Service`. It uses Spring's `JavaMailSender`.

### OTP generation

* `OtpUtils.generate()` produces a cryptographically random 6-digit numeric string using `SecureRandom`
* The raw OTP is BCrypt-hashed before storage; only the hash is persisted in `otp_tokens`
* The raw OTP is included in the email and then discarded — it is never stored in plaintext

### Methods

**sendEmailVerificationOtp(User user)**

1. Invalidate any existing unused `EMAIL_VERIFICATION` tokens for this user (`OtpTokenRepository.deleteAllByUserIdAndPurpose`)
2. Generate a new 6-digit OTP
3. Persist an `OtpToken` with `purpose = EMAIL_VERIFICATION`, `tokenHash = bcrypt(otp)`, `expiresAt = now() + app.otp.expiry-minutes`
4. Render the `email-otp.html` Thymeleaf template with the user's name and the OTP
5. Send the email using `JavaMailSender` from `app.mail.from` to `user.email`, subject: "Verify your CV Matcher account"

**sendPasswordResetOtp(User user)**

1. Invalidate any existing unused `PASSWORD_RESET` tokens for this user
2. Generate a new 6-digit OTP
3. Persist an `OtpToken` with `purpose = PASSWORD_RESET`, matching BCrypt hash and expiry
4. Render the `email-reset.html` template
5. Send the email, subject: "Reset your CV Matcher password"

### Email templates (`email-otp.html`, `email-reset.html`)

Simple, clean HTML emails. Use inline CSS only (many email clients strip `<style>` tags). Each template must show: the user's first name (or "there" as fallback), the 6-digit OTP in a large, monospaced font, the expiry window (e.g. "This code expires in 10 minutes"), and a note that if they did not request this, they should ignore the email.

---

## 14. Auth Service

Create `AuthService` as a `@Service`.

### register(RegisterRequestDto dto)

1. Check if the email is already registered — throw 409 CONFLICT (`EMAIL_ALREADY_REGISTERED`) if so
2. Create a `User` with BCrypt-hashed password and `emailVerified = false`
3. Persist the user
4. Call `EmailService.sendEmailVerificationOtp(user)` — this is a **JobRunr background job** (see Section 17)
5. Return a response indicating that a verification OTP has been sent; **do not issue a JWT yet**

### verifyEmail(VerifyEmailDto dto)

1. Load the user by email — throw 404 if not found
2. Load the latest unused `EMAIL_VERIFICATION` OTP for the user
3. Check the token has not expired — throw 422 (`OTP_EXPIRED`) if it has
4. BCrypt-match the submitted OTP against `tokenHash` — throw 422 (`OTP_INVALID`) if it does not match
5. Mark the token as used (`used = true`)
6. Set `user.emailVerified = true` and persist
7. Generate and return a JWT (`AuthResponseDto`)

### login(LoginRequestDto dto)

1. Load the user by email — throw 401 UNAUTHORIZED if not found (do not reveal which field is wrong)
2. BCrypt-match the password — throw 401 if it does not match
3. Check `user.emailVerified` — throw 403 FORBIDDEN (`EMAIL_NOT_VERIFIED`) if false, with a message telling the user to check their inbox
4. Generate and return a JWT (`AuthResponseDto`)

### forgotPassword(ForgotPasswordRequestDto dto)

1. Load the user by email — if not found, return 200 OK anyway (do not reveal whether the email is registered)
2. Call `EmailService.sendPasswordResetOtp(user)` — dispatched as a **background job**

### resetPassword(ResetPasswordDto dto)

1. Load the user by email — throw 404 if not found
2. Load the latest unused `PASSWORD_RESET` OTP — throw 422 (`OTP_INVALID`) if none exists
3. Check expiry — throw 422 (`OTP_EXPIRED`) if expired
4. BCrypt-match the OTP — throw 422 (`OTP_INVALID`) if mismatch
5. Mark the token as used
6. Hash the new password with BCrypt and update `user.passwordHash`
7. Persist and return 200 OK

---

## 15. CV Matcher Orchestrator (Background Job)

Create `MatchJobService` as a `@Service`. This is the **JobRunr-scheduled job class**.

### Enqueue method: `enqueueMatch(MatchRequestDto request, UUID userId, String sessionId)`

This method is called by `MatchController` synchronously (on the HTTP request thread). It does the following:

1. Create a `MatchJobResult` record with `status = PENDING`, `userId` (nullable), `sessionId` (nullable)
2. Persist the `MatchJobResult` to get its id
3. Enqueue a JobRunr background job: `jobScheduler.enqueue(() -> matchJobService.runMatch(matchJobResultId, request, userId, sessionId))`
4. Update the `MatchJobResult` with the returned JobRunr job ID
5. Return a `MatchJobAcceptedDto` with `jobId` and `pollUrl`

### Background job method: `runMatch(UUID matchJobResultId, MatchRequestDto request, UUID userId, String sessionId)`

This method runs on a JobRunr background thread. It wraps the full orchestration in a try/catch.

On start: update `MatchJobResult.status = PROCESSING`

**Step 1 — Load or resolve CV text**

* If `request.cvDocumentId` is present AND `userId` is not null: load `CvDocument` by id and userId — throw 404 if not found. Use stored `extractedText`.
* Else if `request.cvText` is present: use it directly, applying the same normalisation as `CvParserService` post-processing (null byte removal, whitespace collapse, truncation).
* Else: fail the job with error code `CV_INPUT_REQUIRED`.

**Step 2 — Resolve JD text** (same logic as v1: scrape URL or use pasted text, throw on neither)

**Step 3 — Save JD snapshot**

Persist a `JdSnapshot` with `userId` (nullable) and `sessionId` (nullable).

**Step 4 — Score the match**

Call `MatchScorerService.score(cvText, jdText)`. On any exception, fail the job with `AI_SCORER_ERROR`.

**Step 5 — Threshold check**

If below threshold: build `MatchResponseDto` with `status = BELOW_THRESHOLD`, no PDF. Jump to Step 8.

**Step 6 — Retailor CV** (above threshold only)

Call `CvRewriterService.rewrite(cvText, jdText, matchResult)`. On exception, fail with `AI_REWRITER_ERROR`.

**Step 7 — Generate PDF**

Call `PdfGeneratorService.generate(rewriteResult)`. On exception, fail with `PDF_GENERATION_ERROR`.

**Step 8 — Optionally save application**

If `request.isSaveAsApplication()` is true AND `userId` is not null: call `ApplicationService.createFromMatch(...)`.

**Step 9 — Persist result and complete**

Serialise the `MatchResponseDto` to JSON (using Jackson `ObjectMapper`). Update `MatchJobResult` with `status = COMPLETED`, `resultJson`, `completedAt = now()`.

**On any unhandled exception in the try/catch:**

Update `MatchJobResult` with `status = FAILED`, `errorMessage = exception.getMessage()`, `completedAt = now()`. Re-throw so JobRunr can record the failure in its own tables.

---

## 16. Application Tracker Services

### ApplicationService

Handles creating and soft-deleting applications.

**createApplication(ApplicationCreateDto dto, UUID userId)**
- Validate that `cvDocumentId` (if provided) belongs to the user — throw 403 otherwise
- Validate that `jdSnapshotId` (if provided) belongs to the user — throw 403 otherwise
- Set `appliedAt` to `dto.appliedAt` if provided, otherwise `OffsetDateTime.now()`
- Persist the `JobApplication` entity
- Write an initial `ApplicationStatusHistory` row with `fromStatus = null`, `toStatus = SAVED`
- Return the mapped `ApplicationResponseDto`
  **deleteApplication(UUID id, UUID userId)**
- Load application by id and userId (throw 404 if not found)
- Set `deletedAt = OffsetDateTime.now()` and persist (soft delete)
- Do not physically delete the row — this preserves history

### ApplicationStatusService

**updateStatus(UUID applicationId, UUID userId, StatusUpdateDto dto)**
- Load the application (throw 404 if not found or not owned by user)
- Check that the current status is not terminal (throw 422 `STATUS_IS_TERMINAL` if it is)
- Validate the requested transition using `currentStatus.allowedTransitions()` — throw 422 `INVALID_STATUS_TRANSITION` if invalid, include the set of valid next statuses in the error response body
- Update `applicationStatus` on the entity
- Persist a new `ApplicationStatusHistory` row with `fromStatus`, `toStatus`, `note`
- If transitioning to APPLIED and `appliedAt` is null, set `appliedAt = OffsetDateTime.now()`
- Return the updated `ApplicationResponseDto`
### ApplicationNoteService

**addNote(UUID applicationId, UUID userId, NoteCreateDto dto)**
- Verify the application belongs to the user (throw 403 if not)
- Persist the `ApplicationNote`
- Return the mapped note DTO
  **deleteNote(UUID noteId, UUID applicationId, UUID userId)**
- Verify the application belongs to the user, then verify the note belongs to the application
- Physically delete the note
- Return 204 No Content
### ApplicationQueryService

**listApplications(UUID userId, ApplicationStatus statusFilter, String companyFilter)**
- Call `ApplicationRepository.findAllByFilters(userId, statusFilter, companyFilter)`
- Map to list of `ApplicationResponseDto`
  **getApplication(UUID id, UUID userId, boolean includeHistory, boolean includeNotes)**
- Load by id and userId (throw 404 if not found or soft-deleted)
- Map to `ApplicationResponseDto`
- If `includeHistory`, populate the nested status history list
- If `includeNotes`, populate the nested notes list
### ApplicationStatsService

**getStats(UUID userId)**
- Query total count, status breakdown, and average match score using the custom repository methods
- Calculate `applicationsLast30Days` using a JPQL query filtering `createdAt >= now() - 30 days`
- Return `StatsResponseDto`
---

## 17. JobRunr Configuration

### JobRunrConfig

Create a `@Configuration` class `JobRunrConfig`.

* Declare a `JobScheduler` bean injected from the JobRunr Spring Boot autoconfiguration — no manual wiring needed if the starter is on the classpath and `org.jobrunr.database.type=sql` is set
* The background job server uses the same PostgreSQL `DataSource` bean
* Worker count is set via `org.jobrunr.background-job-server.worker-count`

### Jobs that run as background jobs

Every operation listed below **must** be enqueued via `jobScheduler.enqueue(...)`. None of them block the HTTP thread.

| Operation | Enqueued by | Job method |
|---|---|---|
| CV match (score + rewrite + PDF) | `MatchJobService.enqueueMatch` | `MatchJobService.runMatch` |
| Send email verification OTP | `AuthService.register` | `EmailJobService.sendVerificationOtp` |
| Send password reset OTP | `AuthService.forgotPassword` | `EmailJobService.sendPasswordResetOtp` |

### EmailJobService

Create `EmailJobService` as a `@Service`. This class contains the actual job methods called by JobRunr for email sending, so they can be referenced as lambda-serializable method references.

* `sendVerificationOtp(UUID userId)` — loads the user, calls `EmailService.sendEmailVerificationOtp(user)`
* `sendPasswordResetOtp(UUID userId)` — loads the user, calls `EmailService.sendPasswordResetOtp(user)`

Both methods should be idempotent — if the OTP was already sent and a retry occurs, they silently succeed rather than issuing a duplicate token. Implement idempotency by checking whether a fresh non-expired, unused token already exists before creating a new one.

---

## 18. Controllers

### AuthController — `/api/auth`

No authentication required on any of these endpoints.

* `POST /register` — accepts `RegisterRequestDto`. Calls `AuthService.register()`. Returns 202 ACCEPTED with `{message: "Verification OTP sent to your email"}`.
* `POST /verify-email` — accepts `VerifyEmailDto`. Calls `AuthService.verifyEmail()`. Returns `AuthResponseDto` (JWT issued on success).
* `POST /login` — accepts `LoginRequestDto`. Calls `AuthService.login()`. Returns `AuthResponseDto`.
* `POST /forgot-password` — accepts `ForgotPasswordRequestDto`. Calls `AuthService.forgotPassword()`. Always returns 200 OK.
* `POST /reset-password` — accepts `ResetPasswordDto`. Calls `AuthService.resetPassword()`. Returns 200 OK.

### MatchController — `/api/match`

**Access:** `POST /` and `GET /jobs/{jobId}` are accessible to both anonymous and authenticated users. `GET /download/{uuid}` is accessible without a Bearer token (HMAC-secured). All other match endpoints require authentication.

* `POST /` — **anonymous and authenticated**. Accepts `MatchRequestDto`. Calls `MatchJobService.enqueueMatch(request, userId, sessionId)`. `userId` is null if anonymous; `sessionId` is the HTTP session ID (`HttpSession.getId()`). Returns 202 ACCEPTED with `MatchJobAcceptedDto`.
* `GET /jobs/{jobId}` — **anonymous and authenticated**. Poll for job result. Load `MatchJobResult` by `jobrunrJobId`. For anonymous requests, verify that `matchJobResult.sessionId` matches the current HTTP session ID — return 403 if it does not. For authenticated requests, verify `matchJobResult.userId` matches the authenticated user. Deserialise `resultJson` into `MatchResponseDto` if COMPLETED. Returns `MatchResultPollDto`.
* `GET /download/{uuid}` — validates HMAC signature and expiry, streams PDF. Returns 410 if expired, 403 if invalid signature, 404 if file not found. **Requires authentication** — verify JWT before streaming. Anonymous users cannot access the retailored PDF.

### CvUploadController — `/api/cv`

**Requires authentication on all endpoints.**

* `POST /upload` — accepts `multipart/form-data`. Calls `CvParserService`, saves file to S3, persists `CvDocument`. Returns document metadata.
* `GET /` — list CV documents for the current user.
* `GET /{id}` — get metadata for a specific CV (must be owned by the current user).
* `DELETE /{id}` — validate ownership, check CV not in use, delete from S3 and database.

### ApplicationController — `/api/applications`

**Requires authentication on all endpoints.**

* `POST /` — create application. Returns 201.
* `GET /` — list applications. Query params: `status`, `company`.
* `GET /{id}` — get single application. Query param: `include` (comma-separated: `history`, `notes`).
* `PATCH /{id}/status` — update status. Returns updated `ApplicationResponseDto`.
* `DELETE /{id}` — soft-delete. Returns 204.
* `GET /{id}/history` — get status history.
* `POST /{id}/notes` — add note. Returns 201.
* `GET /{id}/notes` — list notes.
* `DELETE /{id}/notes/{noteId}` — delete note. Returns 204.
* `GET /stats` — get aggregate stats.

---

## 19. Security

### Access model summary

| Endpoint group | Anonymous (session) | Authenticated (JWT) |
|---|---|---|
| `POST /api/match` | ✅ Allowed | ✅ Allowed |
| `GET /api/match/jobs/{jobId}` | ✅ Allowed (session-scoped) | ✅ Allowed (user-scoped) |
| `GET /api/match/download/{uuid}` | ❌ Not allowed | ✅ Allowed + HMAC check |
| `POST /api/auth/**` | ✅ Public | ✅ Public |
| `POST /api/cv/**` | ❌ Not allowed | ✅ Allowed |
| `GET /api/cv/**` | ❌ Not allowed | ✅ Allowed |
| `DELETE /api/cv/**` | ❌ Not allowed | ✅ Allowed |
| `/api/applications/**` | ❌ Not allowed | ✅ Allowed |

### JwtTokenProvider

* Generate tokens using jjwt HS512
* Embed user UUID as the `subject` claim
* Set expiry from `app.jwt.expiration-ms`
* Validate: check signature, check expiry, extract subject

### JwtAuthFilter

* Extend `OncePerRequestFilter`
* Read `Authorization` header, extract Bearer token
* If valid: load `UserDetails`, set `UsernamePasswordAuthenticationToken` in `SecurityContextHolder`
* If missing or invalid: do not set authentication, allow request to continue (Spring Security will enforce access rules per-endpoint)

### SessionMatchFilter

Create `SessionMatchFilter` extending `OncePerRequestFilter`. This filter runs on all requests.

* If the request path is `POST /api/match` or `GET /api/match/jobs/{jobId}` and no JWT authentication is present, ensure an HTTP session exists (`request.getSession(true)`). This creates the session used to scope anonymous match results.
* This filter must run **after** `JwtAuthFilter` so that requests with a valid JWT are treated as authenticated and not given a session unnecessarily.

### SecurityConfig

* Use `SecurityFilterChain` bean
* Permit all on `/api/auth/**`
* Permit all on `POST /api/match` — anonymous access allowed (session scoped in the filter)
* Permit all on `GET /api/match/jobs/**` — anonymous access allowed (session scoped in the filter)
* Require authentication on `GET /api/match/download/**`
* Require authentication on `/api/cv/**`
* Require authentication on `/api/applications/**`
* Add `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`
* Add `SessionMatchFilter` after `JwtAuthFilter`
* Disable CSRF (REST API)
* Set session management policy to `IF_REQUIRED` — sessions are created by `SessionMatchFilter` only for anonymous match users; JWT users remain stateless
* Disable HTTP Basic

### UserDetailsServiceImpl

Load user by email. Return `UserDetails` with stored `password_hash`.

### SecurityUtils

Static `getCurrentUserId()` method extracting UUID from `SecurityContextHolder`.

---

## 20. Exception Handling

### ErrorCode enum

Add new error codes to those defined in v1:

* Existing: `JD_INPUT_REQUIRED`, `JD_SCRAPE_FAILED`, `CV_NOT_FOUND`, `APPLICATION_NOT_FOUND`, `CV_IN_USE`, `INVALID_STATUS_TRANSITION`, `STATUS_IS_TERMINAL`, `AI_SCORER_ERROR`, `AI_REWRITER_ERROR`, `PDF_GENERATION_ERROR`, `FILE_TOO_LARGE`, `UNSUPPORTED_FILE_TYPE`, `DOWNLOAD_EXPIRED`, `DOWNLOAD_INVALID_SIGNATURE`
* New: `EMAIL_ALREADY_REGISTERED`, `EMAIL_NOT_VERIFIED`, `OTP_INVALID`, `OTP_EXPIRED`, `CV_INPUT_REQUIRED`, `JOB_NOT_FOUND`, `JOB_ACCESS_DENIED`

### AppException, GlobalExceptionHandler

Unchanged from v1. Ensure the new error codes are handled and map to appropriate HTTP statuses: `EMAIL_ALREADY_REGISTERED` → 409, `EMAIL_NOT_VERIFIED` → 403, `OTP_INVALID`/`OTP_EXPIRED` → 422, `JOB_NOT_FOUND` → 404, `JOB_ACCESS_DENIED` → 403.

---

## 21. Docker Compose

### app service

* Multi-stage Maven + JRE 21 Dockerfile
* Environment variables: `DB_URL`, `DB_USER`, `DB_PASS`, `JWT_SECRET`, `GEMINI_API_KEY`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION`, `S3_BUCKET_NAME`
* `SPRING_PROFILES_ACTIVE=prod`
* Port: 8080 (internal only)
* Depends on `postgres`
* Health check: `GET http://localhost:8080/actuator/health` every 30s, 3 retries

### postgres service

* Image: `postgres:16-alpine`
* Named volume `pgdata`
* No external port exposure in production

### nginx service

* Image: `nginx:alpine`
* Ports 80 and 443 exposed
* Depends on `app`
* Config: HTTP → HTTPS redirect, TLS via Let's Encrypt, proxy `/api/` → `http://app:8080`, `proxy_read_timeout 120s` (background job polling), security headers, rate limiting via `limit_req_zone`

---

## 22. GitHub Actions CI/CD

Trigger: push to `main`.

Steps:
1. Checkout code
2. Set up Java 21 with Maven cache
3. `mvn test` — fail fast
4. `mvn package -DskipTests`
5. Build and tag Docker image with git commit SHA
6. Push to GitHub Container Registry (ghcr.io)
7. SSH into VPS
8. `docker compose pull && docker compose up -d --remove-orphans`
9. Wait 20s, curl health endpoint — fail workflow if not 200

Required secrets: `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `GHCR_TOKEN`, `GEMINI_API_KEY`, `JWT_SECRET`, `DB_USER`, `DB_PASS`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION`, `S3_BUCKET_NAME`

---

## 23. Key Design Decisions

**Why session-based access for anonymous match users?**
Requiring registration creates friction for users who just want a quick CV check. HTTP sessions allow anonymous users to submit a match job and retrieve its result without an account, while keeping results scoped to their browser session so other users cannot access them.

**Why poll-based async instead of synchronous match response?**
Gemini scoring + CV rewriting + PDF generation can take 15–45 seconds. A synchronous endpoint would hit Nginx's `proxy_read_timeout`, hold a virtual thread, and give a poor user experience on slow connections. The enqueue-and-poll pattern decouples submission from retrieval and lets the client show a progress state.

**Why JobRunr instead of Spring's `@Async`?**
`@Async` jobs are lost on JVM crash or restart. JobRunr persists jobs in PostgreSQL, retries on failure, and provides observability. For operations that involve external API calls and PDF generation, durability matters.

**Why BCrypt for OTP hashing instead of storing in plaintext?**
OTPs in the database are equivalent to temporary passwords. If the database is compromised, bcrypt-hashed OTPs cannot be trivially reversed, protecting users' email accounts from takeover via the reset flow.

**Why not issue a JWT on `/register`?**
Issuing a token before email verification allows unverified accounts to access authenticated endpoints. The verification step is a security checkpoint, not just a courtesy.

**Why anonymous users cannot access the retailored PDF download?**
PDF download URLs are intended to be shareable links. Without tying them to an authenticated identity, any person who obtains the URL could download the CV. Requiring authentication before streaming the file closes this exposure while still allowing the HMAC check to gate against forged or expired URLs.

**Why use WebClient instead of FeignClient for Gemini?**
Gemini calls run inside JobRunr background threads. FeignClient is designed for synchronous servlet-style execution and its thread handling is less predictable on virtual threads. WebClient with `.block()` is explicit about the blocking boundary and integrates cleanly with the virtual thread model.

---

## 24. Summary Checklist for the AI Agent

Before considering the implementation complete, verify:

* [ ] Flyway migrations V1–V9 run cleanly on a fresh Postgres instance
* [ ] Anonymous user can POST to `/api/match` without a JWT and receives a job ID
* [ ] Anonymous user can poll `GET /api/match/jobs/{jobId}` and eventually receives a COMPLETED result
* [ ] Anonymous user cannot access `/api/cv/**` or `/api/applications/**` — returns 401
* [ ] Anonymous user cannot access `/api/match/download/{uuid}` — returns 401
* [ ] Authenticated user can POST to `/api/match` with `cvDocumentId` and receives a job ID
* [ ] Job polling returns PENDING then PROCESSING then COMPLETED in sequence
* [ ] Match score below threshold returns `retailoringOffered: false` and no PDF in result
* [ ] Match score at or above threshold returns a valid download URL in the COMPLETED result
* [ ] `/register` returns 202, does not issue a JWT, triggers a background OTP email
* [ ] OTP email is sent via background job (check JobRunr job table)
* [ ] `/verify-email` with correct OTP returns a JWT and sets `email_verified = true`
* [ ] `/login` without verified email returns 403 with `EMAIL_NOT_VERIFIED` error code
* [ ] `/forgot-password` for unknown email returns 200 (no disclosure)
* [ ] `/reset-password` with valid OTP updates password hash and marks token as used
* [ ] OTP is BCrypt-hashed in `otp_tokens` — never stored in plaintext
* [ ] Expired OTP returns 422 with `OTP_EXPIRED`
* [ ] JWT auth works — protected endpoints return 401 without a token
* [ ] CV upload accepts PDF and DOCX, rejects other types with 400
* [ ] JD URL scraping failure returns 422 with `JD_SCRAPE_FAILED` in the job result
* [ ] Application creation, status transitions, notes, and stats all work for authenticated users
* [ ] Invalid status transitions return 422 with valid next states in response body
* [ ] Deleting a user cascades correctly — no orphaned data
* [ ] No endpoint returns data belonging to a different user or session
* [ ] Docker Compose brings up all services cleanly
* [ ] GitHub Actions pipeline deploys and health check passes