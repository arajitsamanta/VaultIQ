package com.docchat.model;

import java.time.Instant;
import java.util.List;

public class DocumentMetadata {
    private String id;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private int chunksCount;
    private List<String> chunkIds;
    private Instant uploadedAt;

    public DocumentMetadata() {
    }

    public DocumentMetadata(String id, String fileName, String contentType, long sizeBytes, int chunksCount, List<String> chunkIds, Instant uploadedAt) {
        this.id = id;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.chunksCount = chunksCount;
        this.chunkIds = chunkIds;
        this.uploadedAt = uploadedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getChunksCount() {
        return chunksCount;
    }

    public void setChunksCount(int chunksCount) {
        this.chunksCount = chunksCount;
    }

    public List<String> getChunkIds() {
        return chunkIds;
    }

    public void setChunkIds(List<String> chunkIds) {
        this.chunkIds = chunkIds;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
