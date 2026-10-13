# VaultIQ — RAG Document Q&A Service

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.36.0-blue.svg)](https://github.com/langchain4j/langchain4j)
[![Ollama](https://img.shields.io/badge/Ollama-llama3.2-black.svg)](https://ollama.ai)
[![ChromaDB](https://img.shields.io/badge/Vector%20Store-ChromaDB-purple.svg)](https://www.trychroma.com)
[![RAG](https://img.shields.io/badge/RAG-Powered-blueviolet.svg)](https://en.wikipedia.org/wiki/Retrieval-augmented_generation)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

**VaultIQ** is a production-style **Retrieval-Augmented Generation (RAG)** document question-answering REST API built with **Java 17**, **Spring Boot 3**, **LangChain4j**, **Ollama (`llama3.2` & `nomic-embed-text`)**, and **ChromaDB**.

Upload documents (`PDF`, `TXT`, `MD`) into your vault. VaultIQ automatically parses, token-splits, generates dense embeddings using **`nomic-embed-text`**, and indexes them into ChromaDB. When queried, it retrieves the most relevant semantic chunks and prompts the local chat model (**`llama3.2`**) with strict grounding rules to produce precise answers accompanied by verifiable source citations. If the document does not contain the answer, VaultIQ responds honestly rather than hallucinating.

---

## Demo

> Swagger UI

<!-- Replace the placeholder below with an actual screenshot:
     - Swagger UI mid-query: capture the POST /api/chat "Try it out" panel with a question submitted and the cited answer returned.
     - Terminal Q&A: a cURL invocation followed by the JSON response showing `answer` + `citations`.
-->

![VaultIQ demo — Swagger UI ](docs/VaultIQ_Swagger.png)

---

## Architecture Overview

### System Layers & Data Flows

```mermaid
flowchart TD
    %% CLIENT LAYER
    subgraph CL["Client Layer"]
        Browser["Web Browser / Mobile App"]
        Postman["Postman / cURL"]
        SwaggerUI["Swagger UI\n/swagger-ui.html"]
    end

    %% API LAYER
    subgraph API["REST API Layer — Spring Boot 3.3.4 port 8080"]
        direction TB
        subgraph Controllers["Controllers"]
            DocCtrl["DocumentController\nPOST /api/documents\nGET /api/documents\nGET /api/documents/{id}\nDELETE /api/documents/{id}"]
            ChatCtrl["ChatController\nPOST /api/chat"]
            HealthCtrl["Actuator\n/actuator/health\n/actuator/metrics"]
        end
        subgraph ExHandling["Exception Handling"]
            GEH["GlobalExceptionHandler\n@RestControllerAdvice"]
            DNF["DocumentNotFoundException -> 404"]
            DPE["DocumentProcessingException -> 422"]
        end
        DocCtrl & ChatCtrl -.->|throws| GEH
        GEH -.-> DNF & DPE
    end

    %% SERVICE LAYER
    subgraph SVC["Service Layer"]
        direction LR
        subgraph DocPipeline["Document Ingestion Pipeline"]
            direction TB
            P1["1. Select Parser\nPDF / TXT / MD"]
            P2["2. Parse Document\nApachePDFBox or TextParser"]
            P3["3. Recursive Split\n1500 chars, 150 overlap"]
            P4["4. Enrich Chunks\ndocId, fileName, chunkIndex"]
            P5["5. Embed Chunks\nnomic-embed-text via Ollama"]
            P6["6. Store Vectors\nChromaDB collection"]
            P7["7. Persist Metadata\nDocumentMetadataRepository"]
            P1 --> P2 --> P3 --> P4 --> P5 --> P6 --> P7
        end
        subgraph ChatPipeline["RAG Query Pipeline"]
            direction TB
            Q1["1. Guard Check\nrepository.count > 0"]
            Q2["2. Embed Question\nnomic-embed-text via Ollama"]
            Q3["3. Top-K Search\nmaxResults=4, minScore=0.55"]
            Q4["4. Build Context\n& CitationDto list"]
            Q5["5. Grounded Prompt\nStrict Anti-Hallucination Rules"]
            Q6["6. LLM Generate\nllama3.2 via Ollama"]
            Q7["7. Return ChatResponse\nanswer + citations"]
            Q1 --> Q2 --> Q3 --> Q4 --> Q5 --> Q6 --> Q7
        end
    end

    %% CONFIG LAYER
    subgraph CFG["Configuration — LangChainConfig"]
        direction LR
        CfgChat["ChatLanguageModel Bean\nOpenAiChatModel\ntimeout 120s"]
        CfgEmbed["EmbeddingModel Bean\nOpenAiEmbeddingModel\ntimeout 120s"]
        CfgChroma["EmbeddingStore Bean\nChromaEmbeddingStore\ntimeout 60s"]
        CfgParsers["Parser Beans\nApachePdfBoxDocumentParser\nTextDocumentParser"]
    end

    %% DATA LAYER
    subgraph DATA["Data Layer"]
        Repo["DocumentMetadataRepository\nIn-Memory ConcurrentHashMap\nsave / findById / findAll\ndeleteById / count"]
    end

    %% EXTERNAL SERVICES
    subgraph EXT["External Services"]
        direction LR
        subgraph OllamaBox["Ollama host port 11434"]
            OllamaEmbed["nomic-embed-text\nEmbedding Model"]
            OllamaChat["llama3.2\nChat Model\ntemperature=0.0"]
        end
        subgraph ChromaBox["ChromaDB port 8000"]
            ChromaCol["Collection: VaultIQ\nCosine Similarity Index\nPersistent Docker Volume"]
        end
    end

    %% CLIENT TO API
    Browser & Postman -->|HTTP| DocCtrl & ChatCtrl
    SwaggerUI -->|OpenAPI 3| DocCtrl & ChatCtrl
    Browser & Postman --> HealthCtrl

    %% API TO SERVICE
    DocCtrl -->|MultipartFile| P1
    ChatCtrl -->|ChatRequest| Q1
    P7 -->|DocumentResponse 201| DocCtrl
    Q7 -->|ChatResponse 200| ChatCtrl

    %% SERVICE TO CONFIG
    P5 -.->|uses| CfgEmbed
    P6 -.->|uses| CfgChroma
    P1 -.->|uses| CfgParsers
    Q2 -.->|uses| CfgEmbed
    Q3 -.->|uses| CfgChroma
    Q6 -.->|uses| CfgChat

    %% SERVICE TO DATA
    P7 -->|save| Repo
    DocCtrl -->|findAll / findById| Repo
    DocCtrl -->|deleteById + removeAll chunkIds| Repo
    Q1 -->|count| Repo

    %% CONFIG TO EXTERNAL
    CfgEmbed -->|POST /v1/embeddings| OllamaEmbed
    CfgChat -->|POST /v1/chat/completions| OllamaChat
    CfgChroma -->|REST API| ChromaCol
```

---

### Request Sequence — Ingestion & RAG

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant DC as DocumentController
    participant DS as DocumentService
    participant EM as EmbeddingModel
    participant CH as ChromaDB
    participant Repo as DocumentMetadataRepository

    Note over User,Repo: Document Ingestion Flow
    User->>DC: POST /api/documents multipart file
    DC->>DS: uploadDocument(file)
    DS->>DS: selectParser — PDFBox or TextParser
    DS->>DS: parser.parse(inputStream)
    DS->>DS: DocumentSplitters.recursive(1500 chars, 150 overlap)
    DS->>DS: Enrich segments with docId, fileName, chunkIndex
    DS->>EM: embedAll(List of TextSegment)
    EM-->>DS: List of Embedding vectors (nomic-embed-text)
    DS->>CH: addAll(embeddings, enrichedSegments)
    CH-->>DS: List of chunkIds
    DS->>Repo: save(DocumentMetadata)
    DS-->>DC: DocumentResponse
    DC-->>User: 201 Created — id, fileName, chunksCount, uploadedAt

    Note over User,Repo: RAG Query Flow
    User->>DC: POST /api/chat with question
    DC->>DS: ask(ChatRequest)
    DS->>Repo: count() — abort early if no docs
    DS->>EM: embed(question)
    EM-->>DS: queryEmbedding vector
    DS->>CH: search(queryEmbedding, maxResults=4, minScore=0.55)
    CH-->>DS: List of EmbeddingMatch with scores
    DS->>DS: Build context string from chunks
    DS->>DS: Build List of CitationDto
    DS->>DS: Compose SystemMessage with grounding rules
    DS->>DS: Compose UserMessage — context + question
    DS->>EM: chatModel.generate(system, user)
    EM-->>DS: AiMessage — grounded answer text
    DS-->>DC: ChatResponse — question, answer, citations
    DC-->>User: 200 OK — answer, citations with relevance scores
```

---

### Deployment Topology (Docker Compose)

```mermaid
flowchart LR
    subgraph Host["Host Machine"]
        Ollama["Ollama Process\nport 11434\nllama3.2\nnomic-embed-text"]
    end

    subgraph DockerNet["Docker Compose Network"]
        direction TB
        subgraph AppCont["VaultIQ-app port 8080"]
            JVM["Spring Boot JVM\nREST API\nLangChain4j\nIn-Memory Metadata Store"]
        end
        subgraph ChromaCont["VaultIQ-chromadb port 8000"]
            ChromaProc["ChromaDB 0.5.5\nIS_PERSISTENT=TRUE"]
            Vol[("chroma_data\nDocker Volume")]
            ChromaProc --- Vol
        end
        AppCont -->|"CHROMA_URL http://chromadb:8000"| ChromaCont
        ChromaCont -.->|healthcheck curl| ChromaCont
        AppCont -.->|"depends_on chromadb healthy"| ChromaCont
    end

    AppCont -->|"OPENAI_BASE_URL http://host.docker.internal:11434/v1"| Host
    Client["Client\nBrowser / Postman"] -->|"port 8080"| AppCont
```

---

## Tech Stack

| Component | Technology | Purpose |
| :--- | :--- | :--- |
| **Language & Framework** | Java 17, Spring Boot 3.3.4 | Core enterprise runtime and REST API |
| **LLM & Embeddings** | LangChain4j 0.36.0, Local Ollama | Orchestration, `llama3.2`, `nomic-embed-text` |
| **Vector Store** | ChromaDB (Docker) | Dense vector storage, cosine similarity indexing |
| **Document Parsers** | Apache PDFBox, LangChain4j Text Parser | Native PDF, TXT, and Markdown parsing |
| **API Documentation** | springdoc-openapi 2.6.0 | Interactive OpenAPI 3 spec & Swagger UI |
| **Observability** | Spring Boot Actuator | `/actuator/health`, metrics, and liveness probes |
| **Containerization** | Docker, Docker Compose | Multi-container deployment |
| **Testing** | JUnit 5, AssertJ, Mockito, MockMvc | Unit and integration test coverage |

---

## Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/documents` | Upload PDF/text &rarr; parse, chunk (~500 tokens), embed with `nomic-embed-text`, store in ChromaDB |
| `GET` | `/api/documents` | List all ingested documents and chunk metadata |
| `GET` | `/api/documents/{id}` | Retrieve metadata for a specific document |
| `DELETE` | `/api/documents/{id}` | Delete a document and purge all its embeddings from ChromaDB |
| `POST` | `/api/chat` | Query documents with `llama3.2` &rarr; grounded answer + cited source chunks |
| `GET` | `/actuator/health` | Service health check |
| `GET` | `/swagger-ui.html` | Interactive Swagger UI API documentation |

---

## Quickstart

### 1. Prerequisites
- [Docker](https://www.docker.com/) and [Docker Compose](https://docs.docker.com/compose/)
- [Ollama](https://ollama.ai) installed and running locally

### 2. Pull Local Models
Ensure the required models are pulled in Ollama:
```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

### 3. Spin Up Services
Run the stack with Docker Compose:

```bash
docker compose up --build -d
```

Check container status and logs:
```bash
docker compose ps
docker compose logs -f app
```

Once started:
- **Application API**: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **ChromaDB**: `http://localhost:8000`
- **Health Check**: `http://localhost:8080/actuator/health`

To shut down:
```bash
docker compose down
```

---

## Running Locally Without Dockerizing the App

If you want to run ChromaDB in Docker while running the Spring Boot app directly on your host machine:

1. Start ChromaDB:
   ```bash
   docker run -d --name chromadb -p 8000:8000 chromadb/chroma:0.5.5
   ```

2. Run the Spring Boot application:
   ```bash
   ./mvnw spring-boot:run
   ```

3. Run unit tests:
   ```bash
   ./mvnw test
   ```

---

## Sample cURL Requests

### 1. Health Check
```bash
curl -X GET http://localhost:8080/actuator/health
```
**Response:**
```json
{
  "status": "UP"
}
```

---

### 2. Upload Document (PDF or Text)
Upload a document to parse, chunk into ~500 tokens, generate dense embeddings, and index into ChromaDB:

```bash
curl -X POST http://localhost:8080/api/documents \
  -F "file=@sample_policy.txt"
```
**Response (`201 Created`):**
```json
{
  "id": "doc_a1b2c3d4e5f6",
  "fileName": "sample_policy.txt",
  "contentType": "text/plain",
  "sizeBytes": 1420,
  "chunksCount": 2,
  "uploadedAt": "2026-10-11T21:30:00.000Z"
}
```

---

### 3. List Ingested Documents
```bash
curl -X GET http://localhost:8080/api/documents
```
**Response (`200 OK`):**
```json
[
  {
    "id": "doc_a1b2c3d4e5f6",
    "fileName": "sample_policy.txt",
    "contentType": "text/plain",
    "sizeBytes": 1420,
    "chunksCount": 2,
    "uploadedAt": "2026-10-11T21:30:00.000Z"
  }
]
```

---

### 4. Ask a Question (Grounded Q&A with Citations)
Query the knowledge base:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the annual paid time off policy?"
  }'
```
**Response (`200 OK`):**
```json
{
  "question": "What is the annual paid time off policy?",
  "answer": "According to sample_policy.txt, full-time employees are entitled to 20 days of paid time off per calendar year, accruing at 1.67 days per month.",
  "citations": [
    {
      "documentId": "doc_a1b2c3d4e5f6",
      "documentName": "sample_policy.txt",
      "chunkIndex": 0,
      "text": "Full-time employees are entitled to 20 days of paid time off per calendar year, accruing at 1.67 days per month...",
      "relevanceScore": 0.892
    }
  ]
}
```

---

### 5. Out-of-Domain Question (Anti-Hallucination Fallback)
When asked a question not covered in the uploaded documents, the model responds honestly rather than inventing facts:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the capital of Mars?"
  }'
```
**Response (`200 OK`):**
```json
{
  "question": "What is the capital of Mars?",
  "answer": "I could not find the answer in the provided documents.",
  "citations": []
}
```

---

### 6. Delete Document and Embeddings
Remove a document and purge all its vectors from ChromaDB:

```bash
curl -X DELETE http://localhost:8080/api/documents/doc_a1b2c3d4e5f6
```
**Response (`204 No Content`)**

---

## Postman Collection

A complete Postman collection is committed under [`postman/VaultIQ_API.postman_collection.json`](postman/VaultIQ_API.postman_collection.json).
- Import into Postman.
- Set `baseUrl` variable to `http://localhost:8080`.
- The upload request automatically saves the resulting `documentId` into collection variables for one-click testing of GET and DELETE operations.

---

###  Portfolio Title
```text
VaultIQ — RAG Document Q&A Service (Spring Boot + LangChain4j)
```

###  Portfolio Description
```text
Production-style retrieval-augmented generation service built with Java 17, Spring Boot 3, and LangChain4j. Upload PDF/text documents into your vault; VaultIQ chunks and embeds them into ChromaDB and answers questions with cited sources. Includes a REST API with OpenAPI docs, Docker Compose deployment, Postman collection, and unit tests. Demonstrates: LLM API integration, embeddings/vector search, prompt design with grounded answers, and clean enterprise Java delivery.
```

### Skill Tags
`LangChain4j` &bull; `Ollama` &bull; `Llama 3.2` &bull; `Vector Database` &bull; `Retrieval-Augmented Generation (RAG)` &bull; `Docker Compose` &bull; `Java 17` &bull; `Spring Boot 3`
