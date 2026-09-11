package com.oodd.library.controller;

import com.oodd.library.dto.BookRequest;
import com.oodd.library.exception.ResourceNotFoundException;
import com.oodd.library.model.Book;
import com.oodd.library.model.Category;
import com.oodd.library.model.DigitalResource;
import com.oodd.library.model.User;
import com.oodd.library.repository.UserRepository;
import com.oodd.library.service.BookService;
import com.oodd.library.service.BorrowService;
import com.oodd.library.service.CategoryService;
import com.oodd.library.service.DigitalResourceService;
import com.oodd.library.service.ExcelUploadService;
import com.oodd.library.service.SystemSettingsService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class BookController {

    private final ExcelUploadService excelUploadService;
    private final BookService bookService;
    private final CategoryService categoryService;
    private final UserRepository userRepository;
    private final BorrowService borrowService;
    private final DigitalResourceService digitalResourceService;
    private final SystemSettingsService systemSettingsService;

    public BookController(ExcelUploadService excelUploadService,
                          BookService bookService,
                          CategoryService categoryService,
                          UserRepository userRepository,
                          BorrowService borrowService,
                          DigitalResourceService digitalResourceService,
                          SystemSettingsService systemSettingsService) {
        this.excelUploadService = excelUploadService;
        this.bookService = bookService;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
        this.borrowService = borrowService;
        this.digitalResourceService = digitalResourceService;
        this.systemSettingsService = systemSettingsService;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "5") int size,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) Long category,
                            @RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "all") String type,
                            @RequestParam(defaultValue = "1") int rpage,
                            @RequestParam(defaultValue = "6") int rsize,
                            @RequestParam(required = false) String rsearch,
                            @RequestParam(required = false) Long rcategory,
                            Model model) {
        page = Math.max(page, 1);
        rpage = Math.max(rpage, 1);
        User user = currentUser();
        model.addAttribute("user", user);
        model.addAttribute("isAdmin", user != null && user.getRole() == User.UserRole.ADMIN);

        // Flip past-due ISSUED records to OVERDUE before rendering
        borrowService.refreshOverdueRecords();

        // Dashboard statistics (totalBooks, activeCategories, lowStockCount, ...)
        Map<String, Object> stats = bookService.getDashboardStatistics();
        stats.put("activeBorrows", borrowService.countActiveBorrows());
        stats.put("overdueCount", borrowService.getActiveRecords().stream()
                .filter(r -> r.getStatus() == com.oodd.library.model.BorrowRecord.BorrowStatus.OVERDUE).count());
        stats.put("totalFines", borrowService.getTotalCollectedFines());
        model.addAttribute("stats", stats);
        model.addAttribute("activeBorrows", borrowService.getActiveRecords());
        model.addAttribute("members", userRepository.findAll());
        model.addAttribute("settings", systemSettingsService.getSettings());

        // Books by category for the chart
        model.addAttribute("categoryData", bookService.getBooksByCategory());

        // Category dropdown data for the Add/Edit Book modal + catalog filters
        List<Category> categories = categoryService.getAllCategories();
        model.addAttribute("categories", categories);

        // ---- Advanced catalog search (server-side filter + pagination) ----
        Book.BookStatus statusFilter = parseStatus(status);
        Page<Book> bookPage = bookService.searchBooks(page, size, search, category, statusFilter, null);

        boolean showBooks = !"digital".equalsIgnoreCase(type);
        boolean showResources = !"physical".equalsIgnoreCase(type);

        // ---- Digital resources: own server-side search + pagination ----
        Page<DigitalResource> resourcePage = digitalResourceService
                .searchResourcesPaged(rpage, rsize, rsearch, rcategory);

        model.addAttribute("books", bookPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", bookPage.getSize());
        model.addAttribute("totalPages", bookPage.getTotalPages());
        model.addAttribute("totalBooks", bookPage.getTotalElements());
        model.addAttribute("search", search);
        model.addAttribute("filterCategory", category);
        model.addAttribute("filterStatus", status);
        model.addAttribute("bookFiltersActive",
                (search != null && !search.isBlank()) || category != null || (status != null && !status.isBlank()));
        model.addAttribute("catalogType", type);
        model.addAttribute("showBooks", showBooks);
        model.addAttribute("showResources", showResources);
        model.addAttribute("pageNumbers", pageWindow(page, bookPage.getTotalPages()));

        model.addAttribute("resources", resourcePage.getContent());
        model.addAttribute("rCurrentPage", Math.max(rpage, 1));
        model.addAttribute("rPageSize", resourcePage.getSize());
        model.addAttribute("rTotalPages", resourcePage.getTotalPages());
        model.addAttribute("rTotalElements", resourcePage.getTotalElements());
        model.addAttribute("rSearch", rsearch);
        model.addAttribute("rFilterCategory", rcategory);
        model.addAttribute("rFiltersActive",
                (rsearch != null && !rsearch.isBlank()) || rcategory != null);
        model.addAttribute("rPageNumbers", pageWindow(rpage, resourcePage.getTotalPages()));

        return "dashboard";
    }

    private Book.BookStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return Book.BookStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Builds a compact pagination window as page numbers where 0 marks an
     * ellipsis gap (e.g. [1, 0, 4, 5, 6, 0, 20] renders as 1 … 4 5 6 … 20).
     */
    private List<Integer> pageWindow(int current, int totalPages) {
        List<Integer> pages = new ArrayList<>();
        if (totalPages <= 0) {
            return pages;
        }
        int window = 2;
        int start = Math.max(1, current - window);
        int end = Math.min(totalPages, current + window);
        pages.add(1);
        if (start > 2) {
            pages.add(0);
        }
        for (int p = Math.max(2, start); p <= Math.min(totalPages - 1, end); p++) {
            pages.add(p);
        }
        if (end < totalPages - 1) {
            pages.add(0);
        }
        if (totalPages > 1) {
            pages.add(totalPages);
        }
        return pages;
    }

    // ------------------------- Book REST API -------------------------

    @GetMapping("/api/books")
    @ResponseBody
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    /**
     * Advanced catalog search used by the dashboard's dynamic filter bar.
     * Returns a serializable page projection (no lazy entity graphs) so the
     * front-end can re-render the table + pagination without a full reload.
     */
    @GetMapping("/api/books/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchBooks(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "5") int size,
                                                            @RequestParam(required = false) String search,
                                                            @RequestParam(required = false) Long category,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String sort) {
        Book.BookStatus statusFilter = parseStatus(status);
        Page<Book> result = bookService.searchBooks(page, size, search, category, statusFilter, sort);

        List<Map<String, Object>> rows = result.getContent().stream().map(b -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", b.getId());
            row.put("bookCode", b.getBookCode());
            row.put("title", b.getTitle());
            row.put("author", b.getAuthor());
            row.put("isbn", b.getIsbn());
            row.put("quantity", b.getQuantity());
            row.put("status", b.getStatus() != null ? b.getStatus().name() : null);
            row.put("category", b.getCategory() != null ? b.getCategory().getName() : "Uncategorized");
            row.put("categoryId", b.getCategory() != null ? b.getCategory().getId() : null);
            return row;
        }).toList();

        Map<String, Object> body = new HashMap<>();
        body.put("content", rows);
        body.put("page", page);
        body.put("size", result.getSize());
        body.put("totalPages", result.getTotalPages());
        body.put("totalElements", result.getTotalElements());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/api/books/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = bookService.getDashboardStatistics();
        stats.put("activeBorrows", borrowService.countActiveBorrows());
        stats.put("totalFines", borrowService.getTotalCollectedFines());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/api/books/{id}")
    @ResponseBody
    public ResponseEntity<Book> getBook(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bookService.getBookById(id));
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/api/books")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createBook(@Valid @RequestBody BookRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Book saved = bookService.createBook(toEntity(request));
            response.put("success", true);
            response.put("message", "Book added successfully");
            response.put("book", saved);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @PutMapping("/api/books/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateBook(@PathVariable Long id,
                                                          @Valid @RequestBody BookRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Book saved = bookService.updateBook(id, toEntity(request));
            response.put("success", true);
            response.put("message", "Book updated successfully");
            response.put("book", saved);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/api/books/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteBook(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            bookService.deleteBook(id);
            response.put("success", true);
            response.put("message", "Book deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    private Book toEntity(BookRequest request) {
        Book book = new Book();
        book.setBookCode(request.getBookCode());
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setIsbn(request.getIsbn());
        book.setQuantity(request.getQuantity());
        Category category = new Category();
        category.setId(request.getCategoryId());
        book.setCategory(category);
        return book;
    }

    // ------------------------- Excel Upload -------------------------

    @PostMapping("/api/upload-excel")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadExcel(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("message", "Please select a file to upload");
                return ResponseEntity.badRequest().body(response);
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null
                    || !(fileName.toLowerCase().endsWith(".xlsx")
                      || fileName.toLowerCase().endsWith(".xls")
                      || fileName.toLowerCase().endsWith(".csv"))) {
                response.put("success", false);
                response.put("message", "Only Excel files (.xlsx, .xls) and .csv are allowed");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> processResult = excelUploadService.processUploadFile(file);

            response.put("success", processResult.get("success"));
            response.put("message", processResult.get("message"));
            response.put("data", processResult);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error processing file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/api/download-template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> downloadTemplate() throws IOException {
        byte[] excelData = excelUploadService.generateExcelTemplate();
        ByteArrayResource resource = new ByteArrayResource(excelData);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=books_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excelData.length)
                .body(resource);
    }
}
