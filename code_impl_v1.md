# CV Matcher + Job Application Tracker — Implementation Guide

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

---

## Stack

- **Language:** Java 21 (use virtual threads — enable with `spring.threads.virtual.enabled=true`)
- **Framework:** Spring Boot 3.3+
- **Database:** PostgreSQL 16
- **ORM:** Spring Data JPA + Hibernate
- **Migrations:** Flyway
- **AI:** Google Gemini API (REST, not SDK — use Spring WebClient)
- **CV parsing:** Apache PDFBox 3.x (PDF), Apache POI 5.x (DOCX/DOC)
- **JD scraping:** Jsoup
- **PDF generation:** Flying Saucer (xhtmlrenderer) with Thymeleaf HTML templates
- **Auth:** Spring Security with JWT (jjwt 0.12.x)
- **Utilities:** Lombok, Jackson

---

## Project Structure

Organise into the following packages under `com.cvmatcher`:

```
config/          — Spring config beans (Security, Gemini WebClient, Storage, WebClient)
auth/            — AuthController, AuthService, JwtTokenProvider, JwtAuthFilter, UserDetailsServiceImpl
cv/              — CvUploadController, CvDocumentService, CvParserService, CvDocumentRepository, CvDocument entity
matcher/         — MatchController, CvMatcherService (orchestrator), JdScraperService, MatchScorerService,
                   CvRewriterService, PdfGeneratorService, JdSnapshotRepository, JdSnapshot entity, MatchResult (internal model)
tracker/         — ApplicationController, ApplicationService, ApplicationStatusService, ApplicationNoteService,
                   ApplicationQueryService, ApplicationStatsService, ApplicationRepository,
                   StatusHistoryRepository, NoteRepository, JobApplication entity,
                   ApplicationStatusHistory entity, ApplicationNote entity, ApplicationStatus enum
shared/dto/      — All request and response DTOs
shared/exception/— GlobalExceptionHandler, AppException, ErrorCode enum
shared/util/     — SecurityUtils (extract userId from JWT context), HmacUtils (sign download URLs)
```

Resources:
```
db/migration/    — Flyway SQL files V1__ through V5__
templates/       — cv-template.html (Thymeleaf template for PDF generation)
application.properties
application-prod.properties
```

---

## 1. Maven Dependencies

Include the following dependencies in `pom.xml`:

- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-validation`
- `spring-boot-starter-thymeleaf`
- `spring-boot-starter-webflux` — for the Gemini WebClient
- `postgresql` (runtime)
- `flyway-core`
- `flyway-database-postgresql`
- `jjwt-api`, `jjwt-impl`, `jjwt-jackson` — version 0.12.6
- `pdfbox` — version 3.0.3
- `poi-ooxml` — version 5.3.0
- `poi-scratchpad` — version 5.3.0 (for legacy .doc files)
- `jsoup` — version 1.18.1
- `flying-saucer-pdf` — version 9.8.1
- `openpdf` — version 2.0.3 (Flying Saucer dependency)
- `lombok`
- `jackson-databind`
- `spring-boot-starter-test` (test scope)
- `testcontainers postgresql` (test scope)

---

## 2. Application Configuration

### `application.properties` keys to define:

**Server**
- `server.port=8080`

**Database**
- `spring.datasource.url` — read from env `DB_URL`
- `spring.datasource.username` — read from env `DB_USER`
- `spring.datasource.password` — read from env `DB_PASS`
- `spring.jpa.hibernate.ddl-auto=validate` — Flyway manages schema, not Hibernate
- `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect`
- `spring.jpa.open-in-view=false`

**Flyway**
- `spring.flyway.enabled=true`
- `spring.flyway.locations=classpath:db/migration`

**JWT**
- `app.jwt.secret` — read from env `JWT_SECRET` (min 64 chars for HS512)
- `app.jwt.expiration-ms=86400000` (24 hours)

**Gemini**
- `gemini.api-key` — read from env `GEMINI_API_KEY`
- `gemini.model=gemini-1.5-pro`
- `gemini.max-output-tokens=8192`
- `gemini.temperature=0.2`

**File Storage**
- `app.storage.base-path=/data/cv-files`
- `app.storage.pdf-expiry-hours=24`

**Matcher**
- `app.matcher.threshold=65`
- `app.matcher.max-cv-chars=15000`
- `app.matcher.max-jd-chars=10000`

**Multipart**
- `spring.servlet.multipart.max-file-size=10MB`
- `spring.servlet.multipart.max-request-size=12MB`

**Virtual threads**
- `spring.threads.virtual.enabled=true`

### `application-prod.properties`

Override datasource URL to use the Docker Compose service name (`postgres` instead of `localhost`). Set log levels to WARN for root, INFO for `com.cvmatcher`.

---

## 3. Database Schema (Flyway Migrations)

Create five migration files executed in order.

### V1__create_users.sql

Table: `users`
- `id` — UUID primary key, default `gen_random_uuid()`
- `email` — VARCHAR(255), NOT NULL, UNIQUE
- `password_hash` — VARCHAR(255), NOT NULL
- `full_name` — VARCHAR(255), nullable
- `created_at` — TIMESTAMPTZ NOT NULL DEFAULT now()
- `updated_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_users_email` on `email`

### V2__create_cv_documents.sql

Table: `cv_documents`
- `id` — UUID primary key
- `user_id` — UUID NOT NULL, FK → users(id) ON DELETE CASCADE
- `original_filename` — VARCHAR(255) NOT NULL
- `storage_path` — VARCHAR(500) NOT NULL
- `file_type` — VARCHAR(10) NOT NULL, CHECK IN ('PDF','DOCX','DOC')
- `file_size_bytes` — BIGINT nullable
- `extracted_text` — TEXT nullable (stored after parsing)
- `uploaded_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_cv_documents_user_id` on `user_id`

### V3__create_jd_snapshots.sql

Table: `jd_snapshots`
- `id` — UUID primary key
- `user_id` — UUID NOT NULL, FK → users(id) ON DELETE CASCADE
- `source_url` — VARCHAR(2000) nullable (null if JD was pasted as text)
- `company_name` — VARCHAR(255) nullable
- `role_title` — VARCHAR(255) nullable
- `raw_text` — TEXT NOT NULL
- `captured_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_jd_snapshots_user_id` on `user_id`

### V4__create_job_applications.sql

First create the enum type:
```
application_status ENUM: SAVED, APPLIED, PHONE_SCREEN, INTERVIEW, FINAL_ROUND, OFFER, ACCEPTED, DECLINED, REJECTED, WITHDRAWN
```

Table: `job_applications`
- `id` — UUID primary key
- `user_id` — UUID NOT NULL, FK → users(id) ON DELETE CASCADE
- `cv_document_id` — UUID nullable, FK → cv_documents(id) ON DELETE SET NULL
- `jd_snapshot_id` — UUID nullable, FK → jd_snapshots(id) ON DELETE SET NULL
- `company_name` — VARCHAR(255) NOT NULL
- `role_title` — VARCHAR(255) nullable
- `application_status` — application_status enum, NOT NULL, DEFAULT 'SAVED'
- `match_score` — SMALLINT nullable, CHECK BETWEEN 0 AND 100
- `match_summary` — TEXT nullable
- `matched_skills` — TEXT[] nullable (Postgres array)
- `missing_skills` — TEXT[] nullable
- `retailored_cv_path` — VARCHAR(500) nullable
- `jd_url` — VARCHAR(2000) nullable
- `applied_at` — TIMESTAMPTZ nullable
- `deleted_at` — TIMESTAMPTZ nullable (soft delete)
- `created_at` — TIMESTAMPTZ NOT NULL DEFAULT now()
- `updated_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Indexes:
- `idx_job_applications_user_id` on `user_id`
- `idx_job_applications_status` on `(user_id, application_status)`
- `idx_job_applications_company` on `(user_id, company_name)`
- `idx_job_applications_created_at` on `(user_id, created_at DESC)`

### V5__create_status_history_and_notes.sql

Table: `application_status_history`
- `id` — UUID primary key
- `application_id` — UUID NOT NULL, FK → job_applications(id) ON DELETE CASCADE
- `from_status` — application_status nullable (null for first transition)
- `to_status` — application_status NOT NULL
- `note` — TEXT nullable
- `changed_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_status_history_application_id` on `(application_id, changed_at DESC)`

Table: `application_notes`
- `id` — UUID primary key
- `application_id` — UUID NOT NULL, FK → job_applications(id) ON DELETE CASCADE
- `content` — TEXT NOT NULL
- `note_type` — VARCHAR(50) DEFAULT 'GENERAL', CHECK IN ('GENERAL','INTERVIEW_PREP','RECRUITER_CONTACT','SALARY','FOLLOW_UP')
- `created_at` — TIMESTAMPTZ NOT NULL DEFAULT now()

Index: `idx_notes_application_id` on `(application_id, created_at DESC)`

---

## 4. JPA Entities

### CvDocument
Maps to `cv_documents`. All fields map directly to columns. Use `@GeneratedValue(strategy = GenerationType.UUID)` for the id. Use Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`.

### JdSnapshot
Maps to `jd_snapshots`. Same Lombok setup.

### JobApplication
Maps to `job_applications`.
- `applicationStatus` maps to the Postgres enum using `@Enumerated(EnumType.STRING)` with `columnDefinition = "application_status"`
- `matchedSkills` and `missingSkills` use `columnDefinition = "TEXT[]"` for the Postgres array columns
- Add a `@PreUpdate` method that sets `updatedAt = OffsetDateTime.now()`

### ApplicationStatus (enum)
Define all status values. Add a method `allowedTransitions()` that returns a `Set<ApplicationStatus>` of valid next states per the state machine:
- SAVED → {APPLIED, WITHDRAWN}
- APPLIED → {PHONE_SCREEN, REJECTED, WITHDRAWN}
- PHONE_SCREEN → {INTERVIEW, REJECTED, WITHDRAWN}
- INTERVIEW → {OFFER, FINAL_ROUND, REJECTED, WITHDRAWN}
- FINAL_ROUND → {OFFER, REJECTED, WITHDRAWN}
- OFFER → {ACCEPTED, DECLINED}
- ACCEPTED, DECLINED, REJECTED, WITHDRAWN → {} (terminal — empty set)

Add a helper `isTerminal()` that returns true when `allowedTransitions()` is empty.

### ApplicationStatusHistory
Maps to `application_status_history`. Both `fromStatus` and `toStatus` use `@Enumerated(EnumType.STRING)` with `columnDefinition = "application_status"`.

### ApplicationNote
Maps to `application_notes`.

---

## 5. Repositories

All repositories extend `JpaRepository<Entity, UUID>`.

### CvDocumentRepository
- `findAllByUserId(UUID userId)`
- `findByIdAndUserId(UUID id, UUID userId)` — returns Optional
- `existsByIdAndUserId(UUID id, UUID userId)`

### JdSnapshotRepository
- `findAllByUserIdOrderByCapturedAtDesc(UUID userId)`
- `findByIdAndUserId(UUID id, UUID userId)` — returns Optional

### ApplicationRepository
Add a custom JPQL query for filtered listing:
- Select all applications for a user where `deletedAt IS NULL`
- Optional filter by `applicationStatus` (skip filter if null)
- Optional filter by `companyName` using LOWER + LIKE (skip if null)
- Order by `createdAt DESC`

Add aggregate queries:
- `countByUserId(UUID userId)` — count non-deleted applications
- `countByStatus(UUID userId)` — GROUP BY applicationStatus, returns List of Object[] pairs
- `avgMatchScore(UUID userId)` — AVG of matchScore where not null and not deleted

### StatusHistoryRepository
- `findAllByApplicationIdOrderByChangedAtDesc(UUID applicationId)`

### NoteRepository
- `findAllByApplicationIdOrderByCreatedAtDesc(UUID applicationId)`
- `findByIdAndApplicationId(UUID id, UUID applicationId)` — returns Optional (for ownership check before delete)

---

## 6. DTOs

### MatchRequestDto (request)
Fields:
- `cvDocumentId` (UUID, required)
- `jdUrl` (String, optional)
- `jdText` (String, optional) — at least one of jdUrl or jdText must be present; validate this in the service layer, not via annotation
- `companyName` (String, optional)
- `roleTitle` (String, optional)
- `saveAsApplication` (boolean, default false) — if true, automatically create an application record after matching

### MatchResponseDto (response)
Fields:
- `matchResultId` (UUID) — the jd_snapshot id, usable to create an application later
- `status` (String) — either "BELOW_THRESHOLD" or "MATCH_SUCCESSFUL"
- `matchScore` (int)
- `threshold` (int) — the configured threshold value, so the client knows what it was compared against
- `summary` (String)
- `matchedSkills` (List\<String\>)
- `missingSkills` (List\<String\>)
- `weakMatches` (List of objects with `skill` and `note` String fields)
- `recommendation` (String)
- `retailoringOffered` (boolean)
- `retailoredCv` (nested object, null if below threshold):
    - `downloadUrl` (String)
    - `expiresAt` (String, ISO-8601)
    - `fileSizeKb` (Long)
    - `changes` (List\<String\>) — list of changes Gemini made to the CV

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

---

## 7. Gemini API Integration

### How the Gemini REST API works

Use the Gemini Developer API (not Vertex AI) — it is simpler and does not require a GCP project.

Base endpoint:
```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
```

The API key is passed as a query parameter, not a header.

### Request body structure

```
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

## 13. CV Matcher Orchestrator Service

Create `CvMatcherService` as a `@Service`. This is the main orchestrator for the match flow.

### Method: `runMatch(MatchRequestDto request, UUID userId)`

Execute these steps in order:

**Step 1 — Load CV**
Fetch the `CvDocument` by `cvDocumentId` and `userId`. Throw 404 if not found. Use the stored `extractedText` — do not re-parse.

**Step 2 — Resolve JD text**
- If `jdUrl` is present: call `JdScraperService.scrape(url)`. On `JdScrapeException`, propagate as a 422 with error code `JD_SCRAPE_FAILED`.
- If `jdText` is present: use it directly (normalise whitespace).
- If neither is present: throw 400 with error code `JD_INPUT_REQUIRED`.

**Step 3 — Save JD snapshot**
Create and persist a `JdSnapshot` with the resolved text, user ID, URL (if present), company name, and role title from the request. This snapshot is the durable record of the exact JD used.

**Step 4 — Score the match**
Call `MatchScorerService.score(cvText, jdText)`. Catch any exception and rethrow as 502 with error code `AI_SCORER_ERROR`.

**Step 5 — Threshold check**
Compare `matchResult.overallScore` against the configured threshold.

If BELOW threshold: build and return a `MatchResponseDto` with `status = "BELOW_THRESHOLD"`, all gap data, and `retailoringOffered = false`. `retailoredCv` field is null. Skip steps 6 and 7.

**Step 6 — Retailor CV (above threshold only)**
Call `CvRewriterService.rewrite(cvText, jdText, matchResult)`. Catch any exception and rethrow as 502 with error code `AI_REWRITER_ERROR`.

**Step 7 — Generate PDF**
Call `PdfGeneratorService.generate(rewriteResult)`. Catch any exception and rethrow as 500 with error code `PDF_GENERATION_ERROR`.

**Step 8 — Optionally save application**
If `request.isSaveAsApplication()` is true, call `ApplicationService.createFromMatch(...)` with the snapshot ID, CV document ID, company name, role title, match score, and match summary.

**Step 9 — Build and return response**
Build the full `MatchResponseDto` with `status = "MATCH_SUCCESSFUL"`, all gap data, `retailoringOffered = true`, and the nested `retailoredCv` object containing the download URL, expiry, file size, and changes list.

---

## 14. Application Tracker Services

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

## 15. Controllers

All controllers are annotated `@RestController` and `@RequestMapping`. All endpoints require authentication (enforced by Spring Security).

### AuthController — `/api/auth`

- `POST /register` — accepts `{email, password, fullName}`, creates user with BCrypt-hashed password, returns JWT
- `POST /login` — accepts `{email, password}`, validates credentials, returns JWT

### CvUploadController — `/api/cv`

- `POST /upload` — accepts `multipart/form-data` with a `file` field. Calls `CvParserService`, saves the file to storage, persists `CvDocument`, returns the document metadata including the new UUID
- `GET /` — list all CV documents for the current user (metadata only, no extracted text)
- `GET /{id}` — get metadata for a specific CV. Validate user ownership. Return 404 if not found or not owned
- `DELETE /{id}` — validate ownership. Check that the CV is not referenced by any non-deleted application (throw 409 `CV_IN_USE` if it is). Otherwise physically delete the file from storage and the database row

### MatchController — `/api/match`

- `POST /` — main match endpoint. Accepts `MatchRequestDto` as JSON body. Calls `CvMatcherService.runMatch()`. Returns `MatchResponseDto`
- `GET /download/{uuid}` — file download endpoint. Validates the `expires` and `sig` query params using `HmacUtils`. Streams the PDF file as `application/pdf` with `Content-Disposition: attachment`. Returns 410 if expired, 403 if signature invalid, 404 if file not found on disk

### ApplicationController — `/api/applications`

- `POST /` — create application. Accepts `ApplicationCreateDto`. Returns 201 with `ApplicationResponseDto`
- `GET /` — list applications. Query params: `status` (optional, maps to `ApplicationStatus`), `company` (optional, string filter). Returns list of `ApplicationResponseDto`
- `GET /{id}` — get single application. Query params: `include` (optional, comma-separated: `history`, `notes`). Returns `ApplicationResponseDto`
- `PATCH /{id}/status` — update status. Accepts `StatusUpdateDto`. Returns updated `ApplicationResponseDto`
- `DELETE /{id}` — soft-delete. Returns 204 No Content
- `GET /{id}/history` — get status history. Returns ordered list of history records
- `POST /{id}/notes` — add note. Accepts `NoteCreateDto`. Returns 201 with note DTO
- `GET /{id}/notes` — list notes. Returns ordered list
- `DELETE /{id}/notes/{noteId}` — delete note. Returns 204 No Content
- `GET /stats` — get stats. Returns `StatsResponseDto`

---

## 16. Security (JWT)

### JwtTokenProvider

- Generate tokens using `jjwt` HS512 algorithm
- Embed the user's UUID as the `subject` claim
- Set expiry from `app.jwt.expiration-ms`
- Validate tokens: check signature, check expiry, extract subject (userId as UUID)

### JwtAuthFilter

- Extend `OncePerRequestFilter`
- Read the `Authorization` header, extract the Bearer token
- Call `JwtTokenProvider.validateToken()` — if invalid or missing, do not set authentication and let the request proceed (Spring Security will reject it as 401)
- If valid, load user details using `UserDetailsServiceImpl`, set `UsernamePasswordAuthenticationToken` in the `SecurityContextHolder`

### SecurityConfig

- Use `SecurityFilterChain` bean (not `WebSecurityConfigurerAdapter`)
- Permit all on `/api/auth/**`
- Permit GET on `/api/match/download/**` (download links need to be accessible without auth, they are secured by HMAC signature instead)
- Require authentication on all other `/api/**` endpoints
- Add `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`
- Disable CSRF (REST API)
- Set session management to STATELESS
- Disable HTTP Basic

### UserDetailsServiceImpl

Load user by email from the `users` table. Return a `UserDetails` with the stored `password_hash`. Spring Security uses this for login validation.

### SecurityUtils

Utility class with a static method `getCurrentUserId()` that extracts the authenticated user's UUID from the `SecurityContextHolder`. Use this in all service methods rather than accepting userId as a parameter from the controller — keeps controllers thin.

---

## 17. Exception Handling

### ErrorCode enum

Define string codes for all application-specific errors:
- `JD_INPUT_REQUIRED` — neither jdUrl nor jdText provided
- `JD_SCRAPE_FAILED` — Jsoup could not scrape the URL
- `CV_NOT_FOUND` — CV document not found or not owned by user
- `APPLICATION_NOT_FOUND`
- `CV_IN_USE` — attempt to delete a CV referenced by an application
- `INVALID_STATUS_TRANSITION` — invalid status transition attempted
- `STATUS_IS_TERMINAL` — application is in a terminal status
- `AI_SCORER_ERROR` — Gemini scoring call failed
- `AI_REWRITER_ERROR` — Gemini rewriting call failed
- `PDF_GENERATION_ERROR`
- `FILE_TOO_LARGE`
- `UNSUPPORTED_FILE_TYPE`
- `DOWNLOAD_EXPIRED`
- `DOWNLOAD_INVALID_SIGNATURE`

### AppException

A `RuntimeException` subclass that carries an `ErrorCode`, an HTTP status, a human-readable message, and an optional `details` object (e.g., the set of valid next statuses for a transition error).

### GlobalExceptionHandler

Annotated `@RestControllerAdvice`. Handle:
- `AppException` → respond with `{errorCode, message, details}` and the embedded HTTP status
- `MethodArgumentNotValidException` → 400 with field validation errors
- `MaxUploadSizeExceededException` → 413 with `FILE_TOO_LARGE` error code
- `JdScrapeException` → 422 with `JD_SCRAPE_FAILED`
- All other `Exception` → 500 with a generic internal error message (do not expose stack traces to clients)

---

## 18. Docker Compose

Define three services:

### app service
- Image built from a `Dockerfile` that uses a multi-stage build: Maven build stage → JRE 21 slim runtime stage
- Environment variables: `DB_URL`, `DB_USER`, `DB_PASS`, `JWT_SECRET`, `GEMINI_API_KEY`
- `SPRING_PROFILES_ACTIVE=prod`
- Port: 8080 (internal only, not exposed to host — Nginx proxies to it)
- Volume mount: `./data/cv-files:/data/cv-files` for persistent file storage
- Depends on `postgres` service
- Health check: `GET http://localhost:8080/actuator/health` every 30s, 3 retries

### postgres service
- Image: `postgres:16-alpine`
- Environment: `POSTGRES_DB=cvmatcher`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (from env)
- Volume: named volume `pgdata` mounted to `/var/lib/postgresql/data`
- No external port exposure in production

### nginx service
- Image: `nginx:alpine`
- Mounts: `./nginx/nginx.conf:/etc/nginx/nginx.conf:ro`, `./certbot/conf:/etc/letsencrypt:ro`
- Ports: 80 and 443 exposed to host
- Depends on `app` service

### Nginx configuration notes
- Redirect all HTTP (port 80) to HTTPS
- TLS certificate from Let's Encrypt (certbot)
- Proxy all `/api/` requests to `http://app:8080`
- Set `proxy_read_timeout 30s` (AI calls can be slow)
- Include security headers: `Strict-Transport-Security`, `X-Frame-Options DENY`, `X-Content-Type-Options nosniff`
- Add a `limit_req_zone` keyed by `$binary_remote_addr` at the Nginx level as a first layer of rate limiting

---

## 19. GitHub Actions CI/CD

### Workflow trigger
On push to `main` branch.

### Steps in order:
1. Checkout code
2. Set up Java 21 with Maven cache
3. Run `mvn test` — fail fast if tests fail
4. Run `mvn package -DskipTests` to build the JAR
5. Build Docker image and tag with the git commit SHA
6. Push image to GitHub Container Registry (ghcr.io)
7. SSH into the VPS using a stored private key secret
8. On the VPS: `docker compose pull && docker compose up -d --remove-orphans`
9. Verify: wait 20 seconds, then curl `https://{your-domain}/api/actuator/health` — fail the workflow if it does not return 200

### Required GitHub secrets:
- `VPS_HOST` — IP or domain of the VPS
- `VPS_USER` — SSH username
- `VPS_SSH_KEY` — private SSH key
- `GHCR_TOKEN` — GitHub token with `write:packages` scope
- `GEMINI_API_KEY`
- `JWT_SECRET`
- `DB_USER`
- `DB_PASS`

---

## 20. Key Design Decisions to Keep in Mind

**Why WebClient for Gemini and not the Java SDK?**
The Gemini Java SDK is still evolving and has breaking changes between minor versions. Using `WebClient` with the REST API directly gives full control over the request structure and response parsing, is easier to test and mock, and avoids transitive dependency conflicts with other Google libraries.

**Why `responseMimeType: application/json` in Gemini config?**
Without this setting, Gemini often wraps JSON output in markdown code fences (` ```json ... ``` `), which breaks direct `JSON.parse`/`objectMapper.readTree()` calls. Setting this mime type instructs the model to return raw JSON, making parsing reliable. Still add defensive fence-stripping as a fallback.

**Why soft delete for applications?**
Hard-deleting an application would remove the audit trail. Since `application_status_history` and `application_notes` cascade-delete from `job_applications`, a hard delete would destroy all history. Soft delete with a `deleted_at` timestamp preserves the record while hiding it from normal queries.

**Why store `extracted_text` on the `cv_documents` row?**
Parsing PDFs and DOCX files on every match request is slow and CPU-intensive. Storing the extracted text once at upload time makes match requests fast regardless of how many times the same CV is used.

**Why TEXT[] arrays for matched/missing skills?**
Postgres native arrays avoid the need for a separate junction table for what is read-only denormalised data from the AI response. These arrays are never queried with WHERE clauses — they are always fetched as part of the full application record — so the lack of indexability is not a concern.

**Why HMAC-signed URLs for PDF download instead of auth?**
PDF download links are intended to be shareable (e.g., open in a browser tab, download via a mobile app without complex auth headers). HMAC with an expiry timestamp provides security without requiring a Bearer token in the download request, while still being unforgeable without the server's secret key.

---

## Summary Checklist for the AI Agent

Before considering the implementation complete, verify:

- [ ] Flyway migrations run cleanly on a fresh Postgres instance
- [ ] JWT auth works — protected endpoints return 401 without a token
- [ ] CV upload accepts PDF and DOCX, rejects other types with 400
- [ ] JD URL scraping handles a 403 gracefully and returns 422 with `JD_SCRAPE_FAILED`
- [ ] Gemini match scorer returns a valid score with all required fields
- [ ] Match score below threshold returns `retailoringOffered: false` and no PDF
- [ ] Match score at or above threshold returns a valid download URL
- [ ] Download URL returns the PDF and expires correctly after the configured hours
- [ ] Application creation records the correct `cv_document_id` and `jd_snapshot_id`
- [ ] Invalid status transitions return 422 with the valid next states in the response body
- [ ] Terminal status transitions are rejected
- [ ] `GET /api/applications/stats` returns correct aggregate data
- [ ] Deleting a user cascades correctly — no orphaned data
- [ ] No endpoint ever returns data belonging to a different user
- [ ] Docker Compose brings up all three services cleanly
- [ ] GitHub Actions pipeline deploys successfully and health check passes