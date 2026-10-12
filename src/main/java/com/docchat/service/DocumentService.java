package com.docchat.service;

import com.docchat.dto.DocumentResponse;
import com.docchat.exception.DocumentNotFoundException;
import com.docchat.exception.DocumentProcessingException;
import com.docchat.model.DocumentMetadata;
import com.docchat.repository.DocumentMetadataRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentMetadataRepository repository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ApachePdfBoxDocumentParser pdfParser;
    private final TextDocumentParser textParser;

    private final int chunkSizeChars;
    private final int chunkOverlapChars;

    public DocumentService(
            DocumentMetadataRepository repository,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ApachePdfBoxDocumentParser pdfParser,
            TextDocumentParser textParser,
            @Value("${docchat.rag.chunk-size-chars:1500}") int chunkSizeChars,
            @Value("${docchat.rag.chunk-overlap-chars:150}") int chunkOverlapChars) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.pdfParser = pdfParser;
        this.textParser = textParser;
        this.chunkSizeChars = chunkSizeChars;
        this.chunkOverlapChars = chunkOverlapChars;
    }

    public DocumentResponse uploadDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file cannot be null or empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "uploaded-document-" + System.currentTimeMillis();
        }

        String docId = "doc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        log.info("Processing document upload: name={}, size={}, type={}, docId={}",
                originalFilename, file.getSize(), contentType, docId);

        // 1. Select Parser based on file extension / content type
        DocumentParser parser = selectParser(originalFilename, contentType);

        // 2. Parse Document
        Document parsedDocument;
        try (InputStream inputStream = file.getInputStream()) {
            parsedDocument = parser.parse(inputStream);
        } catch (Exception e) {
            log.error("Failed to parse file: {}", originalFilename, e);
            throw new DocumentProcessingException("Failed to parse document: " + e.getMessage(), e);
        }

        if (parsedDocument == null || parsedDocument.text() == null || parsedDocument.text().isBlank()) {
            throw new DocumentProcessingException("Document contained no readable text");
        }

        // 3. Chunk Document (~500 tokens / 1500 chars with overlap)
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSizeChars, chunkOverlapChars);
        List<TextSegment> rawSegments = splitter.split(parsedDocument);

        if (rawSegments.isEmpty()) {
            throw new DocumentProcessingException("Could not create text chunks from document");
        }

        log.info("Document '{}' split into {} chunks", originalFilename, rawSegments.size());

        // 4. Attach structured metadata to each segment
        List<TextSegment> enrichedSegments = new ArrayList<>(rawSegments.size());
        for (int i = 0; i < rawSegments.size(); i++) {
            TextSegment raw = rawSegments.get(i);
            Metadata metadata = new Metadata();
            metadata.put("documentId", docId);
            metadata.put("documentName", originalFilename);
            metadata.put("chunkIndex", i);
            metadata.put("totalChunks", rawSegments.size());
            enrichedSegments.add(TextSegment.from(raw.text(), metadata));
        }

        // 5. Generate Dense Vector Embeddings via OpenAI
        log.info("Generating embeddings for {} segments via OpenAI...", enrichedSegments.size());
        Response<List<Embedding>> embeddingResponse;
        try {
            embeddingResponse = embeddingModel.embedAll(enrichedSegments);
        } catch (Exception e) {
            log.error("Embedding generation failed for document {}: {}", docId, e.getMessage(), e);
            throw new DocumentProcessingException("Failed to generate vector embeddings: " + e.getMessage(), e);
        }

        List<Embedding> embeddings = embeddingResponse.content();

        // 6. Store Embeddings and Metadata in ChromaDB
        log.info("Storing {} embeddings into ChromaDB collection...", embeddings.size());
        List<String> chunkIds;
        try {
            chunkIds = embeddingStore.addAll(embeddings, enrichedSegments);
        } catch (Exception e) {
            log.error("Failed to store embeddings in ChromaDB: {}", e.getMessage(), e);
            throw new DocumentProcessingException("Failed to store vectors in ChromaDB: " + e.getMessage(), e);
        }

        // 7. Save metadata in repository
        DocumentMetadata metadata = new DocumentMetadata(
                docId,
                originalFilename,
                contentType,
                file.getSize(),
                enrichedSegments.size(),
                chunkIds,
                Instant.now()
        );
        repository.save(metadata);

        log.info("Document '{}' (ID: {}) successfully ingested with {} chunks",
                originalFilename, docId, enrichedSegments.size());

        return toResponse(metadata);
    }

    public List<DocumentResponse> listDocuments() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public DocumentResponse getDocument(String id) {
        DocumentMetadata metadata = repository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return toResponse(metadata);
    }

    public void deleteDocument(String id) {
        DocumentMetadata metadata = repository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        log.info("Deleting document {} and its ChromaDB embeddings", id);

        // Delete from ChromaDB vector store
        if (metadata.getChunkIds() != null && !metadata.getChunkIds().isEmpty()) {
            try {
                embeddingStore.removeAll(metadata.getChunkIds());
            } catch (Exception e) {
                log.warn("Direct removeAll failed on vector store for docId {}: {}. Attempting item-by-item removal.",
                        id, e.getMessage());
                for (String chunkId : metadata.getChunkIds()) {
                    try {
                        embeddingStore.remove(chunkId);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // Delete from metadata repository
        repository.deleteById(id);
        log.info("Document {} successfully deleted", id);
    }

    private DocumentParser selectParser(String filename, String contentType) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf") || "application/pdf".equalsIgnoreCase(contentType)) {
            return pdfParser;
        }
        return textParser;
    }

    private DocumentResponse toResponse(DocumentMetadata metadata) {
        return new DocumentResponse(
                metadata.getId(),
                metadata.getFileName(),
                metadata.getContentType(),
                metadata.getSizeBytes(),
                metadata.getChunksCount(),
                metadata.getUploadedAt()
        );
    }
}
