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

## Tests

```bash
mvn test   # spins up a real Postgres+pgvector container via Testcontainers
```

## Project structure

```
src/main/java/com/harsh/interviewassistant
├── config/              # OpenAPI, (later) security, AI client config
├── common/exception/    # Global error handling (RFC 7807 ProblemDetail)
├── auth/                # Step 2: users, JWT
├── document/            # Step 3: upload, parse, chunk, embed
├── interview/           # Step 4–5: question generation (SSE), answer feedback
src/main/resources
├── application.yml
└── db/migration/        # Flyway: V1, V2, ... one migration per feature
```

## Roadmap

1. ✅ Project setup: Spring Boot, Postgres + pgvector, Flyway, error handling, Swagger
2. JWT authentication
3. Document upload → parse → chunk → embed into pgvector
4. RAG question generation with SSE streaming
5. Answer evaluation and feedback
6. Rate limiting, polish
7. Dockerfile + GitHub Actions CI
