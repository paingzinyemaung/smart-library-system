package com.oodd.library.spec;

import com.oodd.library.model.DigitalResource;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DigitalResourceSpecifications {

    private DigitalResourceSpecifications() {
    }

    /**
     * Filter for the PDF / e-book library: free-text across title / author /
     * description / original filename, plus an optional category.
     */
    public static Specification<DigitalResource> filtered(String search, Long categoryId) {
        return (root, query, cb) -> {
            if (query != null && DigitalResource.class.equals(query.getResultType())) {
                root.fetch("category", JoinType.LEFT);
            }
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("author"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("originalFileName"), "")), like)
                ));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
