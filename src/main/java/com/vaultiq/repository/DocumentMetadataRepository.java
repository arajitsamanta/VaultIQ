package com.vaultiq.repository;

import com.vaultiq.model.DocumentMetadata;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class DocumentMetadataRepository {

    private final Map<String, DocumentMetadata> store = new ConcurrentHashMap<>();

    public DocumentMetadata save(DocumentMetadata metadata) {
        store.put(metadata.getId(), metadata);
        return metadata;
    }

    public Optional<DocumentMetadata> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<DocumentMetadata> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(DocumentMetadata::getUploadedAt).reversed())
                .toList();
    }

    public boolean existsById(String id) {
        return store.containsKey(id);
    }

    public Optional<DocumentMetadata> deleteById(String id) {
        return Optional.ofNullable(store.remove(id));
    }

    public void clear() {
        store.clear();
    }

    public long count() {
        return store.size();
    }
}
