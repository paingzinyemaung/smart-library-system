package com.oodd.library.repository;

import com.oodd.library.model.Book;
import com.oodd.library.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

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

    /** Titles with at least one physical copy on the shelf. */
    long countByQuantityGreaterThan(Integer quantity);

    /** Physical copies currently on the shelf (available for lending). */
    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM Book b WHERE b.quantity > 0")
    long getAvailableCopies();

    /** Single-query fetch of the whole catalogue (avoids N+1 on category). */
    @Query("SELECT b FROM Book b JOIN FETCH b.category ORDER BY b.id DESC")
    List<Book> findAllWithCategory();

    /** Eagerly initialised category so the entity serialises cleanly to JSON. */
    @Query("SELECT b FROM Book b JOIN FETCH b.category WHERE b.id = :id")
    Optional<Book> findByIdWithCategory(@Param("id") Long id);
}
