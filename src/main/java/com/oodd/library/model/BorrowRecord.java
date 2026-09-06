package com.oodd.library.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "borrow_records")
@Getter
@Setter
public class BorrowRecord {

    public static final int LOAN_PERIOD_DAYS = 14;
    public static final double FINE_PER_DAY = 1.00;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private BorrowStatus status;

    @Column(name = "fine_amount")
    private Double fineAmount = 0.0;

    public enum BorrowStatus {
        ISSUED, RETURNED, OVERDUE
    }

    /**
     * Days past the due date. Uses the actual return date for returned records,
     * otherwise counts from today (for live overdue display on ISSUED/OVERDUE records).
     */
    public long getOverdueDays() {
        if (dueDate == null) {
            return 0;
        }
        LocalDate reference = status == BorrowStatus.RETURNED && returnDate != null
                ? returnDate
                : LocalDate.now();
        long days = ChronoUnit.DAYS.between(dueDate, reference);
        return Math.max(days, 0);
    }

    /**
     * Fine actually charged (returned records) or currently accruing (issued/overdue).
     */
    public double getAccruedFine() {
        if (status == BorrowStatus.RETURNED) {
            return fineAmount == null ? 0.0 : fineAmount;
        }
        return getOverdueDays() * FINE_PER_DAY;
    }
}
