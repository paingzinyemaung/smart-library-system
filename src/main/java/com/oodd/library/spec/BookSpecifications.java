package com.oodd.library.spec;

import com.oodd.library.model.Book;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BookSpecifications {

    private BookSpecifications() {
    }

    /**
     * Composable filter for the catalogue: free-text search across
     * title / author / bookCode / isbn, plus category and stock status.
     * The category association is join-fetched on the content query
     * (Hibernate still applies the fetch for count queries automatically,
     * so no guard is required).
     */
    public static Specification<Book> filtered(String search, Long categoryId, Book.BookStatus status) {
        return (root, query, cb) -> {
            if (query != null && Book.class.equals(query.getResultType())) {
                root.fetch("category", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("author"), "")), like),
                        cb.like(cb.lower(root.get("bookCode")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("isbn"), "")), like)
                ));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
