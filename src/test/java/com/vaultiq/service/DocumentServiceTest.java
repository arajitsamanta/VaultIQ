package com.vaultiq.service;

import com.vaultiq.dto.DocumentResponse;
import com.vaultiq.exception.DocumentNotFoundException;
import com.vaultiq.exception.DocumentProcessingException;
import com.vaultiq.model.DocumentMetadata;
import com.vaultiq.repository.DocumentMetadataRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private ApachePdfBoxDocumentParser pdfParser;

    @Mock
    private TextDocumentParser textParser;

    private DocumentMetadataRepository repository;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        repository = new DocumentMetadataRepository();
        documentService = new DocumentService(
                repository,
                embeddingModel,
                embeddingStore,
                pdfParser,
                textParser,
                500,
                50
        );
    }

    @Test
    @DisplayName("Should successfully upload, parse, chunk, embed, and store a text document")
    void shouldUploadTextDocumentSuccessfully() {
        String content = "DocChat is an enterprise RAG service powered by Spring Boot and LangChain4j. "
                + "It allows users to ask questions over their proprietary documents with source citations.";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.txt",
                "text/plain",
                content.getBytes()
        );

        when(textParser.parse(any(InputStream.class))).thenReturn(Document.from(content));

        Embedding mockEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingModel.embedAll(anyList())).thenAnswer(inv -> {
            List<TextSegment> segments = inv.getArgument(0);
            List<Embedding> embeddings = segments.stream().map(s -> mockEmbedding).toList();
            return Response.from(embeddings);
        });

        when(embeddingStore.addAll(anyList(), anyList())).thenReturn(List.of("chroma-id-1"));

        DocumentResponse response = documentService.uploadDocument(file);

        assertThat(response).isNotNull();
        assertThat(response.fileName()).isEqualTo("sample.txt");
        assertThat(response.chunksCount()).isGreaterThan(0);
        assertThat(repository.count()).isEqualTo(1);
        verify(embeddingStore).addAll(anyList(), anyList());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when uploaded file is empty")
    void shouldThrowWhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> documentService.uploadDocument(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("Should throw DocumentProcessingException when parsed text is blank")
    void shouldThrowWhenParsedTextIsBlank() {
        MockMultipartFile file = new MockMultipartFile("file", "blank.txt", "text/plain", " ".getBytes());
        when(textParser.parse(any(InputStream.class))).thenReturn(null);

        assertThatThrownBy(() -> documentService.uploadDocument(file))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("no readable text");
    }

    @Test
    @DisplayName("Should list all ingested documents sorted by upload time")
    void shouldListIngestedDocuments() {
        DocumentMetadata doc1 = new DocumentMetadata("doc1", "first.pdf", "application/pdf", 1024, 2, List.of("c1", "c2"), Instant.now().minusSeconds(60));
        DocumentMetadata doc2 = new DocumentMetadata("doc2", "second.txt", "text/plain", 2048, 4, List.of("c3", "c4"), Instant.now());
        repository.save(doc1);
        repository.save(doc2);

        List<DocumentResponse> documents = documentService.listDocuments();

        assertThat(documents).hasSize(2);
        assertThat(documents.get(0).id()).isEqualTo("doc2"); // Most recent first
    }

    @Test
    @DisplayName("Should delete document metadata and purge embeddings from vector store")
    void shouldDeleteDocumentAndPurgeVectors() {
        DocumentMetadata doc = new DocumentMetadata("doc123", "contract.pdf", "application/pdf", 1024, 2, List.of("cid-1", "cid-2"), Instant.now());
        repository.save(doc);

        documentService.deleteDocument("doc123");

        assertThat(repository.existsById("doc123")).isFalse();
        verify(embeddingStore).removeAll(List.of("cid-1", "cid-2"));
    }

    @Test
    @DisplayName("Should throw DocumentNotFoundException when deleting non-existent document")
    void shouldThrowNotFoundOnDeleteNonExistent() {
        assertThatThrownBy(() -> documentService.deleteDocument("non-existent-id"))
                .isInstanceOf(DocumentNotFoundException.class);
    }
}
