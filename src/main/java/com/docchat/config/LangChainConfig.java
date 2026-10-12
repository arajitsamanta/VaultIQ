package com.docchat.config;

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
            @Value("${docchat.openai.api-key:}") String apiKey,
            @Value("${docchat.openai.chat-model:gpt-4o-mini}") String modelName,
            @Value("${docchat.openai.temperature:0.0}") double temperature) {
        String effectiveKey = (apiKey == null || apiKey.isBlank()) ? "demo-key" : apiKey;
        log.info("Initializing OpenAiChatModel with model: {}, temperature: {}", modelName, temperature);
        return OpenAiChatModel.builder()
                .apiKey(effectiveKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${docchat.openai.api-key:}") String apiKey,
            @Value("${docchat.openai.embedding-model:text-embedding-3-small}") String modelName) {
        String effectiveKey = (apiKey == null || apiKey.isBlank()) ? "demo-key" : apiKey;
        log.info("Initializing OpenAiEmbeddingModel with model: {}", modelName);
        return OpenAiEmbeddingModel.builder()
                .apiKey(effectiveKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${docchat.chroma.base-url:http://localhost:8000}") String baseUrl,
            @Value("${docchat.chroma.collection-name:docchat}") String collectionName,
            @Value("${docchat.chroma.timeout-seconds:60}") int timeoutSeconds) {
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
