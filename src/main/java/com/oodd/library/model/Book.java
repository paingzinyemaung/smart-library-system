package com.oodd.library.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "books")
@Getter
@Setter
public class Book {

    public static final int LOW_STOCK_THRESHOLD = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Book code is required")
    @Column(name = "book_code", unique = true, nullable = false)
    private String bookCode;

    @NotBlank(message = "Title is required")
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "author")
    private String author;

    @Column(name = "isbn", length = 20)
    private String isbn;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private BookStatus status;

    @NotNull(message = "Category is required")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    public enum BookStatus {
        AVAILABLE, LOW_STOCK, OUT_OF_STOCK
    }

    public void recalculateStatus() {
        int q = quantity == null ? 0 : quantity;
        if (q <= 0) {
            status = BookStatus.OUT_OF_STOCK;
        } else if (q <= LOW_STOCK_THRESHOLD) {
            status = BookStatus.LOW_STOCK;
        } else {
            status = BookStatus.AVAILABLE;
        }
    }

    @PrePersist
    @PreUpdate
    protected void onFlush() {
        if (quantity == null) {
            quantity = 0;
        }
        recalculateStatus();
    }
}
