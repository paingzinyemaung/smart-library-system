package com.oodd.library.controller;

import com.oodd.library.model.DigitalResource;
import com.oodd.library.service.DigitalResourceService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources")
public class DigitalResourceController {

    private final DigitalResourceService resourceService;

    public DigitalResourceController(DigitalResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> listResources(@RequestParam(required = false) String search) {
        List<Map<String, Object>> list = resourceService.searchResources(search).stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(list);
    }

    /**
     * Server-side paginated resource search with dynamic category + text filters.
     * Powers the Digital Resources section's real-time search and pagination.
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> searchResources(@RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "6") int size,
                                                               @RequestParam(required = false) String search,
                                                               @RequestParam(required = false) Long category) {
        var result = resourceService.searchResourcesPaged(page, size, search, category);
        Map<String, Object> body = new HashMap<>();
        body.put("content", result.getContent().stream().map(this::toMap).toList());
        body.put("page", page);
        body.put("size", result.getSize());
        body.put("totalPages", result.getTotalPages());
        body.put("totalElements", result.getTotalElements());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getResource(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(toMap(resourceService.getResourceById(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam("title") String title,
                                                      @RequestParam(value = "author", required = false) String author,
                                                      @RequestParam(value = "description", required = false) String description,
                                                      @RequestParam(value = "categoryId", required = false) Long categoryId) {
        Map<String, Object> response = new HashMap<>();
        try {
            DigitalResource saved = resourceService.uploadResource(file, title, author, description, categoryId);
            response.put("success", true);
            response.put("message", "\"" + saved.getTitle() + "\" uploaded successfully");
            response.put("resource", toMap(saved));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "File too large — the maximum upload size is 100 MB.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Invalid multipart upload: " + e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/{id}/view")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> viewPdf(@PathVariable Long id) {
        return streamPdf(id, "inline");
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadPdf(@PathVariable Long id) {
        return streamPdf(id, "attachment");
    }

    private ResponseEntity<Resource> streamPdf(Long id, String dispositionType) {
        DigitalResource resource;
        Path path;
        try {
            resource = resourceService.getResourceById(id);
            path = resourceService.resolveStoredFile(resource);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", contentDisposition(dispositionType, resource))
                .cacheControl(CacheControl.noStore())
                .contentLength(path.toFile().length())
                .body(new FileSystemResource(path));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteResource(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            resourceService.deleteResource(id);
            response.put("success", true);
            response.put("message", "Digital resource deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    private Map<String, Object> toMap(DigitalResource resource) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", resource.getId());
        map.put("title", resource.getTitle());
        map.put("author", resource.getAuthor());
        map.put("description", resource.getDescription());
        map.put("originalFileName", resource.getOriginalFileName());
        map.put("fileSize", resource.getFileSize());
        map.put("sizeLabel", formatSize(resource.getFileSize()));
        map.put("uploadDate", resource.getUploadDate() != null
                ? resource.getUploadDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null);
        map.put("category", resource.getCategory() != null ? resource.getCategory().getName() : "Uncategorized");
        map.put("viewUrl", "/api/resources/" + resource.getId() + "/view");
        map.put("downloadUrl", "/api/resources/" + resource.getId() + "/download");
        return map;
    }

    private String formatSize(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 KB";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.0f KB", bytes / 1024.0);
        }
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }

    private String contentDisposition(String type, DigitalResource resource) {
        String fileName = resource.getOriginalFileName() != null && !resource.getOriginalFileName().isBlank()
                ? resource.getOriginalFileName()
                : resource.getTitle().replaceAll("[^A-Za-z0-9 _-]", "").trim().replace(" ", "_") + ".pdf";
        String fallback = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }
}
