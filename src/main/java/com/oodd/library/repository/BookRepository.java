package com.oodd.library.repository;

import com.oodd.library.model.Book;
import com.oodd.library.model.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByBookCode(String bookCode);

    boolean existsByBookCode(String bookCode);

    boolean existsByBookCodeAndIdNot(String bookCode, Long id);

    List<Book> findByStatus(Book.BookStatus status);

    long countByStatus(Book.BookStatus status);

    long countByCategory(Category category);

    @Query("SELECT COALESCE(c.name, 'Uncategorized'), COUNT(b) FROM Book b LEFT JOIN b.category c GROUP BY c.name ORDER BY COUNT(b) DESC")
    List<Object[]> countBooksByCategory();

    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM Book b")
    long getTotalQuantity();

    @Query("SELECT b FROM Book b LEFT JOIN b.category c WHERE " +
           "LOWER(b.bookCode) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Book> searchBooks(@Param("search") String search, Pageable pageable);
}
