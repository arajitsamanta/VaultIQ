package com.docchat.controller;

import com.docchat.dto.DocumentResponse;
import com.docchat.dto.ErrorResponse;
import com.docchat.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Ingestion, cataloging, and lifecycle management for documents and vector embeddings")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Upload and ingest a document", description = "Parses PDF or text file, chunks it (~500 tokens), embeds chunks via OpenAI, and indexes vectors into ChromaDB.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document ingested successfully",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or unparseable document file",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "File exceeds size limit",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DocumentResponse> uploadDocument(
            @Parameter(description = "PDF or text/markdown document file to ingest", required = true)
            @RequestParam("file") MultipartFile file) {
        DocumentResponse response = documentService.uploadDocument(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all ingested documents", description = "Returns metadata for all documents currently indexed in ChromaDB.")
    @ApiResponse(responseCode = "200", description = "List of ingested documents",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = DocumentResponse.class))))
    public ResponseEntity<List<DocumentResponse>> listDocuments() {
        return ResponseEntity.ok(documentService.listDocuments());
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get document metadata by ID", description = "Retrieves ingestion metadata for a specific document.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document metadata retrieved",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DocumentResponse> getDocument(
            @Parameter(description = "Document ID", example = "doc_8f1b2c3d")
            @PathVariable("id") String id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document and its vector embeddings", description = "Removes document metadata and purges all associated vector embeddings from ChromaDB.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Document and embeddings deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Document not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Document ID to delete", example = "doc_8f1b2c3d")
            @PathVariable("id") String id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
