package com.oodd.library.controller;

import com.oodd.library.dto.BookRequest;
import com.oodd.library.model.Book;
import com.oodd.library.model.Category;
import com.oodd.library.model.User;
import com.oodd.library.repository.UserRepository;
import com.oodd.library.service.BookService;
import com.oodd.library.service.CategoryService;
import com.oodd.library.service.ExcelUploadService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class BookController {

    private final ExcelUploadService excelUploadService;
    private final BookService bookService;
    private final CategoryService categoryService;
    private final UserRepository userRepository;

    public BookController(ExcelUploadService excelUploadService,
                          BookService bookService,
                          CategoryService categoryService,
                          UserRepository userRepository) {
        this.excelUploadService = excelUploadService;
        this.bookService = bookService;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
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
                            Model model) {
        User user = currentUser();
        model.addAttribute("user", user);
        model.addAttribute("isAdmin", user != null && user.getRole() == User.UserRole.ADMIN);

        // Dashboard statistics (totalBooks, activeCategories, lowStockCount, ...)
        Map<String, Object> stats = bookService.getDashboardStatistics();
        model.addAttribute("stats", stats);

        // Books by category for the chart
        model.addAttribute("categoryData", bookService.getBooksByCategory());

        // Pagination + search
        Page<Book> bookPage = bookService.getBooksWithPagination(page, size, search);
        model.addAttribute("books", bookPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", bookPage.getTotalPages());
        model.addAttribute("totalBooks", bookPage.getTotalElements());
        model.addAttribute("search", search);

        // Category dropdown data for the Add/Edit Book modal
        model.addAttribute("categories", categoryService.getAllCategories());

        return "dashboard";
    }

    // ------------------------- Book REST API -------------------------

    @GetMapping("/api/books")
    @ResponseBody
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    @GetMapping("/api/books/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(bookService.getDashboardStatistics());
    }

    @GetMapping("/api/books/{id}")
    @ResponseBody
    public ResponseEntity<Book> getBook(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(bookService.getBookById(id));
        } catch (IllegalArgumentException e) {
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
