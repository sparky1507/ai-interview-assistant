# AI Interview Assistant

A RAG backend that ingests a resume and a job description, stores embeddings in PostgreSQL (pgvector),
and uses an LLM to generate tailored interview questions and evaluate answers.

**Stack:** Java 21 · Spring Boot 3.5 · Spring AI · PostgreSQL + pgvector · Flyway · Spring Security (JWT) · SSE · Docker

## Run locally

Prerequisites: JDK 21, Maven 3.9+, Docker.

```bash
# 1. Start Postgres with pgvector
docker compose up -d

# 2. Run the app
mvn spring-boot:run
```

- Health check: http://localhost:8080/actuator/health
- Swagger UI:  http://localhost:8080/swagger-ui.html

Configuration comes from environment variables (see `.env.example`). `application.yml` ships a
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

## Tests

```bash
mvn test   # unit tests + integration tests against a real Postgres+pgvector container (Testcontainers)
```

## Project structure

```
src/main/java/com/harsh/interviewassistant
├── config/              # OpenAPI (bearer JWT "Authorize" button)
├── common/exception/    # Global error handling (RFC 7807 ProblemDetail)
├── security/            # JWT service + filter, SecurityConfig, 401/403 handler
├── auth/                # Register / login endpoints and DTOs
├── user/                # User entity, repository, GET /users/me
├── document/            # Step 3: upload, parse, chunk, embed
├── interview/           # Step 4–5: question generation (SSE), answer feedback
src/main/resources
├── application.yml
└── db/migration/        # Flyway: V1 extensions, V2 users, ... one migration per feature
```

## Roadmap

1. ✅ Project setup: Spring Boot, Postgres + pgvector, Flyway, error handling, Swagger
2. ✅ JWT authentication (register, login, stateless JWT filter, BCrypt)
3. Document upload → parse → chunk → embed into pgvector
4. RAG question generation with SSE streaming
5. Answer evaluation and feedback
6. Rate limiting, polish
7. Dockerfile + GitHub Actions CI
