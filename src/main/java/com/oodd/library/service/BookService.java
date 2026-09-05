package com.oodd.library.service;

import com.oodd.library.model.Book;
import com.oodd.library.model.Category;
import com.oodd.library.repository.BookRepository;
import com.oodd.library.repository.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;

    public BookService(BookRepository bookRepository, CategoryRepository categoryRepository) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Book> getAllBooks() {
        return bookRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    @Transactional(readOnly = true)
    public Book getBookById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Book> getBooksWithPagination(int page, int size, String search) {
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"));
        if (search == null || search.isBlank()) {
            return bookRepository.findAll(pageable);
        }
        return bookRepository.searchBooks(search.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Book> getRecentBooks() {
        return bookRepository.findAll(PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"))).getContent();
    }

    @Transactional(readOnly = true)
    public List<Book> getLowStockBooks() {
        return bookRepository.findByStatus(Book.BookStatus.LOW_STOCK);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getBooksByCategory() {
        Map<String, Long> categoryCounts = new HashMap<>();
        for (Object[] result : bookRepository.countBooksByCategory()) {
            categoryCounts.put((String) result[0], (Long) result[1]);
        }
        return categoryCounts;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBooks", bookRepository.count());
        stats.put("activeCategories", categoryRepository.count());
        stats.put("lowStockCount", bookRepository.countByStatus(Book.BookStatus.LOW_STOCK));
        stats.put("outOfStockCount", bookRepository.countByStatus(Book.BookStatus.OUT_OF_STOCK));
        stats.put("totalQuantity", bookRepository.getTotalQuantity());
        return stats;
    }

    @Transactional
    public Book createBook(Book book) {
        String code = requireText(book.getBookCode(), "Book code is required");
        requireText(book.getTitle(), "Title is required");
        if (book.getQuantity() == null) {
            book.setQuantity(0);
        }
        if (book.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        if (bookRepository.existsByBookCode(code)) {
            throw new IllegalArgumentException("Book code already exists: " + code);
        }
        book.setBookCode(code);
        book.setTitle(book.getTitle().trim());
        book.setCategory(resolveCategory(book));
        book.recalculateStatus();
        return bookRepository.save(book);
    }

    @Transactional
    public Book updateBook(Long id, Book incoming) {
        Book existing = getBookById(id);
        String code = requireText(incoming.getBookCode(), "Book code is required");
        requireText(incoming.getTitle(), "Title is required");
        if (incoming.getQuantity() == null || incoming.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity must be zero or greater");
        }
        if (bookRepository.existsByBookCodeAndIdNot(code, id)) {
            throw new IllegalArgumentException("Book code already exists: " + code);
        }
        existing.setBookCode(code);
        existing.setTitle(incoming.getTitle().trim());
        existing.setAuthor(trimToNull(incoming.getAuthor()));
        existing.setQuantity(incoming.getQuantity());
        existing.setCategory(resolveCategory(incoming));
        existing.recalculateStatus();
        return bookRepository.save(existing);
    }

    @Transactional
    public void deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new IllegalArgumentException("Book not found with id: " + id);
        }
        bookRepository.deleteById(id);
    }

    /**
     * Derives the stock status from the current quantity:
     * 0 -> OUT_OF_STOCK, 1..5 -> LOW_STOCK, otherwise AVAILABLE.
     */
    public Book applyQuantityStatus(Book book) {
        book.recalculateStatus();
        return book;
    }

    private Category resolveCategory(Book book) {
        Long categoryId = book.getCategory() != null ? book.getCategory().getId() : null;
        if (categoryId == null) {
            throw new IllegalArgumentException("Category is required");
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + categoryId));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
