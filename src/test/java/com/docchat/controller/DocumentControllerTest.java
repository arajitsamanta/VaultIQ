package com.docchat.controller;

import com.docchat.dto.DocumentResponse;
import com.docchat.service.DocumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @Test
    @DisplayName("POST /api/documents should upload document and return 201 Created")
    void shouldUploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "dummy content".getBytes()
        );

        DocumentResponse response = new DocumentResponse(
                "doc_123",
                "test.pdf",
                "application/pdf",
                1024,
                3,
                Instant.now()
        );

        when(documentService.uploadDocument(any())).thenReturn(response);

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isEqualTo("doc_123"))
                .andExpect(jsonPath("$.fileName").isEqualTo("test.pdf"))
                .andExpect(jsonPath("$.chunksCount").isEqualTo(3));
    }

    @Test
    @DisplayName("GET /api/documents should return list of documents")
    void shouldListDocuments() throws Exception {
        DocumentResponse doc = new DocumentResponse(
                "doc_123",
                "manual.txt",
                "text/plain",
                512,
                1,
                Instant.now()
        );
        when(documentService.listDocuments()).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isEqualTo("doc_123"))
                .andExpect(jsonPath("$[0].fileName").isEqualTo("manual.txt"));
    }

    @Test
    @DisplayName("DELETE /api/documents/{id} should return 204 No Content")
    void shouldDeleteDocument() throws Exception {
        doNothing().when(documentService).deleteDocument("doc_123");

        mockMvc.perform(delete("/api/documents/doc_123"))
                .andExpect(status().isNoContent());
    }
}
