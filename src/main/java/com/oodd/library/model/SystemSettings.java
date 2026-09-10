package com.oodd.library.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "system_settings")
@Data
public class SystemSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "max_loan_days", nullable = false)
    private int maxLoanDays = 14;

    @Column(name = "fine_rate", nullable = false)
    private double fineRate = 1.00;

    @Column(name = "max_borrow_limit", nullable = false)
    private int maxBorrowLimit = 5;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getMaxLoanDays() {
        return maxLoanDays;
    }

    public void setMaxLoanDays(int maxLoanDays) {
        this.maxLoanDays = maxLoanDays;
    }

    public double getFineRate() {
        return fineRate;
    }

    public void setFineRate(double fineRate) {
        this.fineRate = fineRate;
    }

    public int getMaxBorrowLimit() {
        return maxBorrowLimit;
    }

    public void setMaxBorrowLimit(int maxBorrowLimit) {
        this.maxBorrowLimit = maxBorrowLimit;
    }
}
