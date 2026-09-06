package com.oodd.library.service;

import com.oodd.library.model.Category;
import com.oodd.library.model.DigitalResource;
import com.oodd.library.repository.CategoryRepository;
import com.oodd.library.repository.DigitalResourceRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DigitalResourceService {

    private final DigitalResourceRepository resourceRepository;
    private final CategoryRepository categoryRepository;

    @Value("${app.pdf.storage-dir:uploads/pdf}")
    private String storageDir;

    private Path storageRoot;

    public DigitalResourceService(DigitalResourceRepository resourceRepository,
                                  CategoryRepository categoryRepository) {
        this.resourceRepository = resourceRepository;
        this.categoryRepository = categoryRepository;
    }

    @PostConstruct
    public void init() {
        storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create PDF storage directory: " + storageRoot, e);
        }
    }

    @Transactional(readOnly = true)
    public List<DigitalResource> getAllResources() {
        return resourceRepository.findAllByOrderByUploadDateDesc();
    }

    @Transactional(readOnly = true)
    public List<DigitalResource> searchResources(String search) {
        if (search == null || search.isBlank()) {
            return getAllResources();
        }
        return resourceRepository.searchResources(search.trim());
    }

    @Transactional(readOnly = true)
    public DigitalResource getResourceById(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Digital resource not found with id: " + id));
    }

    @Transactional
    public DigitalResource uploadResource(MultipartFile file, String title, String author,
                                          String description, Long categoryId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please select a PDF file to upload");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files (.pdf) are allowed");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("application/pdf")
                && !contentType.toLowerCase(Locale.ROOT).startsWith("application/octet-stream")) {
            throw new IllegalArgumentException("Invalid file type: expected a PDF document");
        }
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + ".pdf";
        Path target = storageRoot.resolve(storedName).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid storage path resolved for the uploaded file");
        }
        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store PDF file: " + e.getMessage(), e);
        }

        DigitalResource resource = new DigitalResource();
        resource.setTitle(title.trim());
        resource.setAuthor(trimToNull(author));
        resource.setDescription(trimToNull(description));
        resource.setOriginalFileName(originalName);
        resource.setFileSize(file.getSize());
        resource.setFilePath(storageDir.replace('\\', '/') + "/" + storedName);
        resource.setUploadDate(LocalDateTime.now());
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));
            resource.setCategory(category);
        }

        try {
            return resourceRepository.save(resource);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // best-effort cleanup of the orphaned file
            }
            throw e;
        }
    }

    @Transactional
    public void deleteResource(Long id) {
        DigitalResource resource = getResourceById(id);
        try {
            Files.deleteIfExists(resolveStoredFile(resource));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete stored PDF file: " + e.getMessage(), e);
        }
        resourceRepository.deleteById(id);
    }

    /**
     * Resolves the stored file for a resource, guarding against path traversal
     * by verifying the final path stays inside the configured storage root.
     */
    public Path resolveStoredFile(DigitalResource resource) {
        Path path = Paths.get(resource.getFilePath()).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot)) {
            throw new IllegalStateException("Stored file path escapes the PDF storage directory");
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("PDF file is missing from storage for resource: " + resource.getId());
        }
        return path;
    }

    @Transactional(readOnly = true)
    public long countResources() {
        return resourceRepository.count();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
