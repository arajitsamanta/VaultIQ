package com.vaultiq.service;

import com.vaultiq.dto.ChatRequest;
import com.vaultiq.dto.ChatResponse;
import com.vaultiq.model.DocumentMetadata;
import com.vaultiq.repository.DocumentMetadataRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private ChatLanguageModel chatLanguageModel;

    private DocumentMetadataRepository repository;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        repository = new DocumentMetadataRepository();
        chatService = new ChatService(
                embeddingModel,
                embeddingStore,
                chatLanguageModel,
                repository,
                4,
                0.55
        );
    }

    @Test
    @DisplayName("Should return honest fallback immediately when no documents are ingested")
    void shouldReturnFallbackWhenNoDocumentsIngested() {
        ChatRequest request = new ChatRequest("What is the company policy?");

        ChatResponse response = chatService.ask(request);

        assertThat(response).isNotNull();
        assertThat(response.answer()).contains(ChatService.NOT_FOUND_MESSAGE);
        assertThat(response.citations()).isEmpty();
        verify(embeddingModel, never()).embed(anyString());
        verify(chatLanguageModel, never()).generate(any(ChatMessage.class));
    }

    @Test
    @DisplayName("Should return honest fallback when vector search finds no matching chunks")
    void shouldReturnFallbackWhenNoChunksMatch() {
        repository.save(new DocumentMetadata("doc1", "handbook.pdf", "application/pdf", 100, 1, List.of("c1"), Instant.now()));

        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(Collections.emptyList()));

        ChatResponse response = chatService.ask(new ChatRequest("What are quantum computers?"));

        assertThat(response.answer()).isEqualTo(ChatService.NOT_FOUND_MESSAGE);
        assertThat(response.citations()).isEmpty();
        verify(chatLanguageModel, never()).generate(any(ChatMessage.class));
    }

    @Test
    @DisplayName("Should retrieve relevant chunks, prompt LLM, and return answer with citations")
    void shouldReturnAnswerWithCitationsWhenMatchesFound() {
        repository.save(new DocumentMetadata("doc1", "policy.txt", "text/plain", 500, 2, List.of("c1", "c2"), Instant.now()));

        when(embeddingModel.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));

        Metadata meta = new Metadata();
        meta.put("documentId", "doc1");
        meta.put("documentName", "policy.txt");
        meta.put("chunkIndex", 0);
        TextSegment segment = TextSegment.from("Employees receive 20 days of paid time off annually.", meta);

        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.88, "match-id-1", Embedding.from(new float[]{0.1f}), segment);
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(new EmbeddingSearchResult<>(List.of(match)));

        when(chatLanguageModel.generate(any(SystemMessage.class), any(UserMessage.class)))
                .thenReturn(Response.from(dev.langchain4j.data.message.AiMessage.from("According to policy.txt, employees receive 20 days of paid time off annually.")));

        ChatResponse response = chatService.ask(new ChatRequest("How much PTO do employees get?"));

        assertThat(response.answer()).contains("20 days");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).documentName()).isEqualTo("policy.txt");
        assertThat(response.citations().get(0).documentId()).isEqualTo("doc1");
        assertThat(response.citations().get(0).relevanceScore()).isEqualTo(0.88);
    }
}
