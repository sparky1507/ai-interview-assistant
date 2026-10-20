# AI Interview Assistant

A RAG backend that ingests a resume and a job description, stores embeddings in PostgreSQL (pgvector),
and uses an LLM to generate tailored interview questions and evaluate answers.

**Stack:** Java 21 · Spring Boot 3.5 · Spring AI · PostgreSQL + pgvector · Flyway · Spring Security (JWT) · SSE · Docker

## Run locally

Prerequisites: JDK 21, Maven 3.9+, Docker.

```bash
# 1. Start Postgres with pgvector
docker compose up -d

# 2. Put your OpenAI key in .env (git-ignored, loaded automatically)
cp .env.example .env    # then set OPENAI_API_KEY=sk-...

# 3. Run the app
mvn spring-boot:run
```

- Health check: http://localhost:8080/actuator/health
- Swagger UI:  http://localhost:8080/swagger-ui.html

Configuration comes from environment variables or `.env` (see `.env.example`). `application.yml` ships a
**dev-only** `JWT_SECRET` default; any shared or production environment must set its own:

```bash
openssl rand -base64 48
```

## Try the auth flow in Swagger

1. Open http://localhost:8080/swagger-ui.html
2. **Authentication → POST /api/v1/auth/register** → *Try it out* with
   `{"email": "jane@example.com", "password": "s3cret-pass", "fullName": "Jane Doe"}` → `201` with an `accessToken`.
3. Click **Authorize** (top right), paste the `accessToken` (without the `Bearer ` prefix) → *Authorize*.
4. **Users → GET /api/v1/users/me** → `200` with your profile (no password hash).
5. **Authorize → Logout**, call `/users/me` again → `401` ProblemDetail.

Auth is stateless: the JWT (HS256, 24h by default) carries the user id, email and role, so requests are
verified without a DB lookup. Logout is client-side (discard the token).

## Upload documents

With a token from the auth flow (Swagger → **Documents**):

- **POST /api/v1/documents** (multipart): `file` = your resume (PDF, DOCX or TXT, max 5MB), `type` = `RESUME`
- **POST /api/v1/documents/text**: `{"type": "JOB_DESCRIPTION", "title": "Backend Engineer @ Acme", "content": "..."}`
- **GET /api/v1/documents**, **GET /api/v1/documents/{id}** (includes the extracted text), **DELETE /api/v1/documents/{id}**

Pipeline: detect type from the bytes (Tika) → extract text → split into ~400-token chunks → embed →
store in pgvector (`document_chunks`) with `userId`/`documentId` metadata, so retrieval only ever
searches the caller's own chunks.

### AI provider

`AI_PROVIDER=openai` (default) or `ollama`. Embeddings are 1024-dimensional for both
(OpenAI `text-embedding-3-small` with `dimensions=1024`, Ollama `mxbai-embed-large`), so switching needs
no schema change, but existing documents must be re-uploaded (different models' vectors aren't comparable).
**Free local option (Ollama in Docker, no API key):**

```bash
docker compose --profile ollama up -d                          # starts Postgres + Ollama
docker exec interview-ollama ollama pull mxbai-embed-large     # ~670 MB, once
```

Then set `AI_PROVIDER=ollama` in `.env` and run the app.

## Tests

```bash
mvn test   # unit tests + integration tests against a real Postgres+pgvector container (Testcontainers)
```

Tests never call an AI provider: a deterministic fake `EmbeddingModel` replaces it, so no API key is needed.

## Project structure

```
src/main/java/com/harsh/interviewassistant
├── config/              # OpenAPI (bearer JWT "Authorize" button)
├── common/exception/    # Global error handling (RFC 7807 ProblemDetail)
├── security/            # JWT service + filter, SecurityConfig, 401/403 handler
├── auth/                # Register / login endpoints and DTOs
├── user/                # User entity, repository, GET /users/me
├── document/            # Upload, Tika text extraction, chunking, embedding into pgvector
├── interview/           # Step 4–5: question generation (SSE), answer feedback
src/main/resources
├── application.yml
└── db/migration/        # Flyway: V1 extensions, V2 users, V3 documents + vector chunks
```

## Roadmap

1. ✅ Project setup: Spring Boot, Postgres + pgvector, Flyway, error handling, Swagger
2. ✅ JWT authentication (register, login, stateless JWT filter, BCrypt)
3. ✅ Document upload → parse → chunk → embed into pgvector (OpenAI or Ollama)
4. RAG question generation with SSE streaming
5. Answer evaluation and feedback
6. Rate limiting, polish
7. Dockerfile + GitHub Actions CI
