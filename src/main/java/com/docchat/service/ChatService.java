package com.docchat.service;

import com.docchat.dto.ChatRequest;
import com.docchat.dto.ChatResponse;
import com.docchat.dto.CitationDto;
import com.docchat.repository.DocumentMetadataRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public static final String NOT_FOUND_MESSAGE = "I could not find the answer in the provided documents.";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatLanguageModel chatLanguageModel;
    private final DocumentMetadataRepository repository;

    private final int maxResults;
    private final double minScore;

    public ChatService(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ChatLanguageModel chatLanguageModel,
            DocumentMetadataRepository repository,
            @Value("${docchat.rag.max-results:4}") int maxResults,
            @Value("${docchat.rag.min-score:0.55}") double minScore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatLanguageModel = chatLanguageModel;
        this.repository = repository;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public ChatResponse ask(ChatRequest request) {
        String question = request.question().trim();
        log.info("Processing chat question: '{}'", question);

        // 1. Check if store has any documents ingested
        if (repository.count() == 0) {
            log.info("No documents present in repository. Returning grounded empty response.");
            return new ChatResponse(question, NOT_FOUND_MESSAGE + " No documents have been uploaded yet.", Collections.emptyList());
        }

        // 2. Embed the question
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        // 3. Retrieve relevant chunks from ChromaDB
        List<EmbeddingMatch<TextSegment>> matches = retrieveRelevantChunks(queryEmbedding);

        if (matches.isEmpty()) {
            log.info("No relevant chunks met minimum relevance score ({}) for question: '{}'", minScore, question);
            return new ChatResponse(question, NOT_FOUND_MESSAGE, Collections.emptyList());
        }

        log.info("Found {} relevant chunks for question: '{}'", matches.size(), question);

        // 4. Build citations
        List<CitationDto> citations = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            TextSegment segment = match.embedded();

            String docId = segment.metadata() != null ? segment.metadata().getString("documentId") : "unknown";
            String docName = segment.metadata() != null ? segment.metadata().getString("documentName") : "unknown";
            Integer chunkIndexVal = segment.metadata() != null ? segment.metadata().getInteger("chunkIndex") : 0;
            int chunkIndex = chunkIndexVal != null ? chunkIndexVal : 0;

            citations.add(new CitationDto(docId, docName, chunkIndex, segment.text(), match.score()));

            contextBuilder.append(String.format("--- [Source %d: %s (Chunk %d)] ---\n%s\n\n",
                    i + 1, docName, chunkIndex, segment.text()));
        }

        // 5. Prompt the model with grounded context
        String systemInstruction = """
                You are a precise, grounded assistant answering questions strictly based on the provided document excerpts.

                STRICT GROUNDING RULES:
                1. Answer the question using ONLY the factual information contained in the provided context excerpts.
                2. If the answer cannot be found in or directly inferred from the excerpts, reply honestly and concisely: "I could not find the answer in the provided documents." Do NOT attempt to fabricate, extrapolate, or use outside knowledge.
                3. Maintain an objective, professional tone and cite the document names where appropriate.
                """;

        String userPrompt = String.format("""
                Context Excerpts:
                %s

                Question:
                %s

                Answer:
                """, contextBuilder, question);

        String answer = chatLanguageModel.generate(
                SystemMessage.from(systemInstruction),
                UserMessage.from(userPrompt)
        ).content().text();

        log.info("Generated answer successfully for question: '{}'", question);

        return new ChatResponse(question, answer, citations);
    }

    private List<EmbeddingMatch<TextSegment>> retrieveRelevantChunks(Embedding queryEmbedding) {
        try {
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            return searchResult != null && searchResult.matches() != null ? searchResult.matches() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("Chroma searchRequest failed: {}. Falling back to findRelevant.", e.getMessage());
            try {
                return embeddingStore.findRelevant(queryEmbedding, maxResults, minScore);
            } catch (Exception ex) {
                log.error("Failed to query vector store: {}", ex.getMessage(), ex);
                return Collections.emptyList();
            }
        }
    }
}
