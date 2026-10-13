# AGENTS.md — VaultIQ

> Guidance for AI coding agents working in this repository.

---

## Project Overview

**VaultIQ** is a production-style **Retrieval-Augmented Generation (RAG)** REST API built with:

- **Java 17** + **Spring Boot 3.3.4**
- **LangChain4j 0.36.0** (LLM orchestration)
- **Ollama** (`llama3.2` chat, `nomic-embed-text` embeddings) — or any OpenAI-compatible provider
- **ChromaDB** (vector store, runs via Docker)
- **springdoc-openapi 2.6.0** (Swagger UI)
- **JUnit 5 + Mockito** (unit tests)

The app exposes a REST API to upload PDF/TXT/MD documents, chunk and embed them into ChromaDB, and answer grounded Q&A questions with cited sources.

---

## Repository Layout

```
VaultIQ/
├── src/
│   ├── main/
│   │   ├── java/com/vaultiq/
│   │   │   ├── VaultIQApplication.java          # Spring Boot entry point
│   │   │   ├── config/
│   │   │   │   ├── LangChainConfig.java          # Beans: ChatLanguageModel, EmbeddingModel, EmbeddingStore, parsers
│   │   │   │   └── OpenApiConfig.java            # Swagger/OpenAPI configuration
│   │   │   ├── controller/
│   │   │   │   ├── DocumentController.java       # POST/GET/DELETE /api/documents
│   │   │   │   └── ChatController.java           # POST /api/chat
│   │   │   ├── service/
│   │   │   │   ├── DocumentService.java          # Ingestion pipeline (parse → chunk → embed → store)
│   │   │   │   └── ChatService.java              # RAG query pipeline (embed → search → prompt → generate)
│   │   │   ├── repository/
│   │   │   │   └── DocumentMetadataRepository.java  # In-memory ConcurrentHashMap store
│   │   │   ├── model/
│   │   │   │   └── DocumentMetadata.java         # Domain entity (id, fileName, chunkIds, etc.)
│   │   │   ├── dto/
│   │   │   │   ├── ChatRequest.java
│   │   │   │   ├── ChatResponse.java
│   │   │   │   ├── CitationDto.java
│   │   │   │   ├── DocumentResponse.java
│   │   │   │   └── ErrorResponse.java
│   │   │   └── exception/
│   │   │       ├── DocumentNotFoundException.java     # → HTTP 404
│   │   │       ├── DocumentProcessingException.java   # → HTTP 422
│   │   │       └── GlobalExceptionHandler.java        # @RestControllerAdvice
│   │   └── resources/
│   │       └── application.yml                   # All config with env-var overrides
│   └── test/
│       ├── java/com/vaultiq/
│       │   ├── controller/
│       │   │   ├── ChatControllerTest.java
│       │   │   └── DocumentControllerTest.java
│       │   └── service/
│       │       ├── ChatServiceTest.java
│       │       └── DocumentServiceTest.java
│       └── resources/
│           └── application-test.yml              # Test-specific overrides
├── postman/
│   └── VaultIQ_API.postman_collection.json
├── sample-docs/
│   └── company-policy.txt
├── docker-compose.yml                            # ChromaDB + app services
├── Dockerfile
├── pom.xml
├── .env.example                                  # Template for required env vars
└── .gitignore
```

---

## Environment Setup

### Prerequisites

- Java 17
- Maven (wrapper included: `./mvnw`)
- Docker + Docker Compose (for ChromaDB and the containerized app)
- [Ollama](https://ollama.ai) (for local LLM/embedding) OR an OpenAI API key

### Required Environment Variables

Copy `.env.example` to `.env` and fill in values before running:

```bash
cp .env.example .env
```

| Variable | Default | Description |
|---|---|---|
| `OPENAI_BASE_URL` | `http://localhost:11434/v1` | Base URL for the OpenAI-compatible API (Ollama or OpenAI) |
| `OPENAI_API_KEY` | `ollama` | API key (`ollama` for local, real key for OpenAI) |
| `OPENAI_CHAT_MODEL` | `llama3.2` | Chat model name |
| `OPENAI_EMBEDDING_MODEL` | `nomic-embed-text` | Embedding model name |
| `OPENAI_TEMPERATURE` | `0.0` | LLM temperature (keep at `0.0` for grounded RAG) |
| `CHROMA_URL` | `http://localhost:8000` | ChromaDB base URL |
| `CHROMA_COLLECTION` | `vaultiq` | ChromaDB collection name |
| `RAG_MAX_RESULTS` | `4` | Maximum top-K chunks retrieved per query |
| `RAG_MIN_SCORE` | `0.55` | Minimum cosine similarity score to include a chunk |
| `RAG_CHUNK_SIZE` | `1500` | Chunk size in characters |
| `RAG_CHUNK_OVERLAP` | `150` | Chunk overlap in characters |
| `PORT` | `8080` | HTTP server port |

**Never commit `.env` to version control.** It is listed in `.gitignore`.

---

## Build & Run Commands

```bash
# Build the project (skip tests for speed during dev)
./mvnw package -DskipTests

# Run all unit tests
./mvnw test

# Run the Spring Boot app locally (ChromaDB must be running separately)
./mvnw spring-boot:run

# Start ChromaDB only (for local dev)
docker run -d --name chromadb -p 8000:8000 chromadb/chroma:0.5.5

# Full Docker Compose stack (ChromaDB + app)
docker compose up --build -d
docker compose logs -f app
docker compose down
```

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/documents` | Upload PDF/TXT/MD → parse, chunk, embed, store |
| `GET` | `/api/documents` | List all ingested documents |
| `GET` | `/api/documents/{id}` | Get document metadata by ID |
| `DELETE` | `/api/documents/{id}` | Delete document and purge its ChromaDB vectors |
| `POST` | `/api/chat` | Ask a grounded question; returns answer + citations |
| `GET` | `/actuator/health` | Health probe |
| `GET` | `/swagger-ui.html` | Interactive OpenAPI docs |

---

## Architecture & Key Design Decisions

### Document Ingestion Pipeline (`DocumentService`)
1. Select parser by file extension: `.pdf` → `ApachePdfBoxDocumentParser`, everything else → `TextDocumentParser`
2. Parse file content from `InputStream`
3. Split with `DocumentSplitters.recursive(1500 chars, 150 overlap)`
4. Enrich each `TextSegment` with metadata: `documentId`, `documentName`, `chunkIndex`, `totalChunks`
5. Batch-embed all segments via `EmbeddingModel` (`nomic-embed-text`)
6. Store embeddings + segments in `ChromaEmbeddingStore`
7. Persist `DocumentMetadata` (id, filename, chunkIds list) in the in-memory `DocumentMetadataRepository`

### RAG Query Pipeline (`ChatService`)
1. Guard: abort with a friendly message if `repository.count() == 0`
2. Embed the user question via `EmbeddingModel`
3. Cosine-similarity search in ChromaDB: `maxResults=4`, `minScore=0.55`
4. Build context string and `List<CitationDto>` from results
5. Compose a grounded `SystemMessage` with strict anti-hallucination rules
6. Generate answer via `ChatLanguageModel` (`llama3.2`)
7. Return `ChatResponse` with `answer` and `citations`

### Metadata Store
`DocumentMetadataRepository` uses a `ConcurrentHashMap` for **in-memory** storage — **metadata does NOT survive a restart**. ChromaDB vectors persist (Docker volume), but the ID-to-metadata mapping is lost. This is a known trade-off; a future improvement would add a persistent store (H2/PostgreSQL).

### LangChain4j Configuration (`LangChainConfig`)
All LangChain4j beans are created here. The chat model and embedding model share the same `OPENAI_BASE_URL` / `OPENAI_API_KEY` configuration, making it trivial to swap Ollama for OpenAI by only changing env vars.

---

## Testing Guidelines

- All tests live under `src/test/java/com/vaultiq/`
- Use `application-test.yml` for test-specific config (no real Ollama/ChromaDB required)
- Tests use **Mockito** to mock `EmbeddingModel`, `EmbeddingStore`, and `DocumentMetadataRepository`
- `@WebMvcTest` is used for controller tests; `@ExtendWith(MockitoExtension.class)` for service tests
- Run tests: `./mvnw test`
- Test reports: `target/surefire-reports/`

**When adding new features:**
1. Write or update unit tests covering both happy-path and error/edge cases
2. Ensure `DocumentControllerTest` and `ChatControllerTest` cover new endpoints
3. Ensure `DocumentServiceTest` and `ChatServiceTest` cover new service logic
4. All tests must pass before committing: `./mvnw test`

---

## Code Style & Conventions

- **Package structure**: `com.vaultiq.<layer>` — controller, service, repository, model, dto, config, exception
- **Logging**: Use `LoggerFactory.getLogger(ClassName.class)` (SLF4J); log at `INFO` for major pipeline steps, `ERROR` for failures, `WARN` for recoverable issues
- **Error handling**: Throw `DocumentNotFoundException` (→ 404) or `DocumentProcessingException` (→ 422); `GlobalExceptionHandler` converts them to `ErrorResponse` JSON
- **Records vs classes**: DTOs (`ChatRequest`, `ChatResponse`, etc.) use Java records for immutability
- **Validation**: Use `@Valid` and Bean Validation annotations on request DTOs where appropriate
- **No `@Autowired` field injection**: Use constructor injection throughout
- **Config via `@Value`**: All tunable parameters (`chunk-size-chars`, etc.) are injected via `@Value` with sensible defaults

---

## Common Pitfalls for Agents

1. **ChromaDB must be running** before the app starts. The app will fail to initialize `ChromaEmbeddingStore` if ChromaDB is unreachable.
2. **Ollama models must be pulled** before first use: `ollama pull llama3.2 && ollama pull nomic-embed-text`
3. **In-memory metadata store**: Restarting the app clears `DocumentMetadataRepository`. ChromaDB vectors remain, but the app won't know about them. Re-upload documents after a restart in dev.
4. **File size limits**: `max-file-size=20MB`, `max-request-size=25MB` — respect these in tests.
5. **Supported formats**: Only `PDF`, `TXT`, and `MD` files are handled. Other types fall through to `TextDocumentParser` which may produce garbage or throw.
6. **Temperature must be `0.0`** for grounded/deterministic RAG answers — do not increase it without understanding the anti-hallucination prompt design.
7. **ChromaDB collection name** in Docker Compose defaults to `vaultiq` — the `application.yml` default also uses `vaultiq`. Keep them consistent across environments.

---

## Adding New Features — Checklist

- [ ] Place new classes in the correct package (`controller`, `service`, `dto`, etc.)
- [ ] Add constructor-injected dependencies (no field injection)
- [ ] Add `@Valid` + Bean Validation to new request DTOs
- [ ] Map new exceptions in `GlobalExceptionHandler`
- [ ] Add unit tests (mock external dependencies)
- [ ] Update `OpenApiConfig` / controller-level `@Operation` annotations if adding new endpoints
- [ ] Run `./mvnw test` — all tests green before committing
- [ ] Update `.env.example` if new environment variables are introduced
