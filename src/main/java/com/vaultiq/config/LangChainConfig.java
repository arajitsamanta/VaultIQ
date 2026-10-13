package com.vaultiq.config;

import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChainConfig.class);

    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${vaultiq.openai.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${vaultiq.openai.api-key:ollama}") String apiKey,
            @Value("${vaultiq.openai.chat-model:llama3.2}") String modelName,
            @Value("${vaultiq.openai.temperature:0.0}") double temperature) {
        String effectiveKey = (apiKey == null || apiKey.isBlank()) ? "ollama" : apiKey;
        log.info("Initializing ChatLanguageModel with baseUrl: {}, model: {}, temperature: {}", baseUrl, modelName,
                temperature);
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(effectiveKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${vaultiq.openai.base-url:http://localhost:11434/v1}") String baseUrl,
            @Value("${vaultiq.openai.api-key:ollama}") String apiKey,
            @Value("${vaultiq.openai.embedding-model:nomic-embed-text}") String modelName) {
        String effectiveKey = (apiKey == null || apiKey.isBlank()) ? "ollama" : apiKey;
        log.info("Initializing EmbeddingModel with baseUrl: {}, model: {}", baseUrl, modelName);
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(effectiveKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(120))
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${vaultiq.chroma.base-url:http://localhost:8000}") String baseUrl,
            @Value("${vaultiq.chroma.collection-name:VaultIQ}") String collectionName,
            @Value("${vaultiq.chroma.timeout-seconds:60}") int timeoutSeconds) {
        log.info("Connecting to ChromaDB at {} with collection '{}'", baseUrl, collectionName);
        return ChromaEmbeddingStore.builder()
                .baseUrl(baseUrl)
                .collectionName(collectionName)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Bean
    public ApachePdfBoxDocumentParser pdfDocumentParser() {
        return new ApachePdfBoxDocumentParser();
    }

    @Bean
    public TextDocumentParser textDocumentParser() {
        return new TextDocumentParser();
    }
}
