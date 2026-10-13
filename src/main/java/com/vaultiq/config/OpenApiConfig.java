package com.vaultiq.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI docChatOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("DocChat API — RAG Document Q&A Service")
                        .description("Production-grade Retrieval-Augmented Generation (RAG) REST API built with Java 17, Spring Boot 3, LangChain4j, Ollama (Llama 3.2 & Nomic Embed Text), and ChromaDB. "
                                + "Supports PDF and text document ingestion, token-aware chunking, dense vector embeddings, grounded similarity search, and hallucination-free Q&A with source citations.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("DocChat AI Engineering")
                                .email("engineering@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("/").description("Default Server")
                ));
    }
}
