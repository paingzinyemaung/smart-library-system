package com.oodd.library.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookRequest {

    @NotBlank(message = "Book code is required")
    private String bookCode;

    @NotBlank(message = "Title is required")
    private String title;

    private String author;

    @Size(max = 20, message = "ISBN must be at most 20 characters")
    private String isbn;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    private Integer quantity;

    @NotNull(message = "Category is required")
    private Long categoryId;
}
