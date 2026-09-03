package com.oodd.library.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.oodd.library.model.Book;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {
 
	 Optional<Book> findByBookCode(String bookCode);
	 
	 List<Book> findByCategory(String category);
	 
	 List<Book> findByStatus(Book.BookStatus status);
	 
	 @Query("SELECT b.category, COUNT(b) FROM Book b GROUP BY b.category")
	 List<Object[]> countBooksByCategory();
	 
	 @Query("SELECT SUM(b.price * b.quantity) FROM Book b")
	 BigDecimal getTotalInventoryValue();
	 
	 List<Book> findByQuantityLessThan(Integer quantity);
	 
	 @Query("SELECT COUNT(DISTINCT b.category) from Book b")
	 int getNumofCategory();
	 
	//Search with pagination
	 @Query("SELECT b FROM Book b WHERE " +
	        "LOWER(b.bookCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
	        "LOWER(b.bookName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
	        "LOWER(b.category) LIKE LOWER(CONCAT('%', :search, '%'))")
	 Page<Book> findByBookCodeContainingOrBookNameContainingOrCategoryContaining(
	     @Param("search") String search1,
	     @Param("search") String search2,
	     @Param("search") String search3,
	     Pageable pageable);
}
