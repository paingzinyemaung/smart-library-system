package com.oodd.library.service;

import com.oodd.library.model.Book;
import com.oodd.library.repository.BookRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookService {
 
	 @Autowired
	 private BookRepository bookRepository;
	 
	 public List<Book> getAllBooks() {
	     return bookRepository.findAll();
	 }
	 
	 public Page<Book> getBooksWithPagination(int page, int size, String search) {
	     Pageable pageable = PageRequest.of(page - 1, size);
	     
	     if (search == null || search.isEmpty()) {
	         return this.bookRepository.findAll(pageable);
	     } else {
	         // Search
	         return bookRepository.findByBookCodeContainingOrBookNameContainingOrCategoryContaining(
	             search, search, search, pageable);
	     }
	 }
	 
	 public List<Book> getRecentBooks() {
	     // Get latest 10 products
	     return bookRepository.findAll().stream()
	             .sorted((b1, b2) -> b2.getRegisterDate().compareTo(b1.getRegisterDate()))
	             .limit(10)
	             .collect(Collectors.toList());
	 }
	 
	 public Map<String, Long> getBooksByCategory() {
	     List<Object[]> results = bookRepository.countBooksByCategory();
	     Map<String, Long> categoryCounts = new HashMap<>();
	     
	     for (Object[] result : results) {
	         String category = (String) result[0];
	         Long count = (Long) result[1];
	         categoryCounts.put(category, count);
	     }
	     
	     return categoryCounts;
	 }
	 
	 public List<Book> getLowStockBooks() {
	     return bookRepository.findByQuantityLessThan(10);
	 }
	 
	 public int getNumofCategories() {
		 return bookRepository.getNumofCategory();
	 }
	 
	 public Map<String, Object> getDashboardStatistics() {
	     Map<String, Object> stats = new HashMap<>();
	          
	     // Total products
	     long totalBooks = bookRepository.count();
	     stats.put("totalBooks", totalBooks);
	     
	     // Total categories
	     List<Object[]> categories = bookRepository.countBooksByCategory();
	     stats.put("totalCategories", categories.size());
	     
	     // Total inventory value
	     BigDecimal totalValue = bookRepository.getTotalInventoryValue();
	     stats.put("totalValue", totalValue != null ? totalValue : BigDecimal.ZERO);
	     
	     // Low stock count
	     List<Book> lowStock = bookRepository.findByQuantityLessThan(10);
	     stats.put("lowStockCount", lowStock.size());
	     
	     // Out of stock count
	     List<Book> outOfStock = bookRepository.findByStatus(Book.BookStatus.ON_HOLD);
	     stats.put("outOfStockCount", outOfStock.size());
	     
	     return stats;
	 }
	 
	 public void deleteBook(Long id) {
		 bookRepository.deleteById(id);
	 }
	 
	 public Book saveBook(Book book) {
	     return bookRepository.save(book);
	 }
}