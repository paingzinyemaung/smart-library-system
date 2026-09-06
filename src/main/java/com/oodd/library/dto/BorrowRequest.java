package com.oodd.library.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BorrowRequest {

    @NotNull(message = "Member is required")
    private Long userId;

    @NotNull(message = "Book is required")
    private Long bookId;
}
