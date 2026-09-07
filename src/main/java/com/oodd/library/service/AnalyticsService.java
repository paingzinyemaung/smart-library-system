package com.oodd.library.service;

import com.oodd.library.model.BorrowRecord;
import com.oodd.library.repository.BookRepository;
import com.oodd.library.repository.BorrowRecordRepository;
import com.oodd.library.repository.DigitalResourceRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final int TREND_MONTHS = 6;
    private static final List<BorrowRecord.BorrowStatus> ACTIVE_STATUSES =
            List.of(BorrowRecord.BorrowStatus.ISSUED, BorrowRecord.BorrowStatus.OVERDUE);
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final BorrowRecordRepository borrowRecordRepository;
    private final BookRepository bookRepository;
    private final DigitalResourceRepository digitalResourceRepository;

    public AnalyticsService(BorrowRecordRepository borrowRecordRepository,
                            BookRepository bookRepository,
                            DigitalResourceRepository digitalResourceRepository) {
        this.borrowRecordRepository = borrowRecordRepository;
        this.bookRepository = bookRepository;
        this.digitalResourceRepository = digitalResourceRepository;
    }

    /** Chart payload for the dashboard: top books, status split and monthly trends. */
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardAnalytics() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topBooks", getTopBorrowedBooks(5));
        payload.put("statusDistribution", getStatusDistribution());
        payload.put("monthlyTrends", getMonthlyLoanTrends());
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTopBorrowedBooks(int top) {
        List<Object[]> rows = borrowRecordRepository.findTopBorrowedBooks(Limit.of(Math.max(top, 1)));
        List<String> labels = rows.stream().map(r -> (String) r[0]).toList();
        List<Long> data = rows.stream().map(r -> ((Number) r[1]).longValue()).toList();
        return chartSeries(labels, data);
    }

    /**
     * Inventory split shown on the dashboard donut:
     * physical copies on the shelf vs copies currently on loan vs digital items.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStatusDistribution() {
        long available = bookRepository.getAvailableCopies();
        long borrowed = borrowRecordRepository.countActiveLoans(ACTIVE_STATUSES);
        long digital = digitalResourceRepository.count();
        return chartSeries(
                List.of("Available", "Borrowed", "Digital"),
                List.of(available, borrowed, digital));
    }

    /** Loans per calendar month over the recent window, zero-filled for missing months. */
    @Transactional(readOnly = true)
    public Map<String, Object> getMonthlyLoanTrends() {
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : borrowRecordRepository.findMonthlyLoanCounts(TREND_MONTHS)) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        List<String> labels = new java.util.ArrayList<>();
        List<Long> data = new java.util.ArrayList<>();
        YearMonth start = YearMonth.now().minusMonths(TREND_MONTHS - 1L);
        for (int i = 0; i < TREND_MONTHS; i++) {
            YearMonth month = start.plusMonths(i);
            labels.add(month.format(MONTH_LABEL));
            data.add(counts.getOrDefault(month.format(MONTH_KEY), 0L));
        }
        return chartSeries(labels, data);
    }

    private Map<String, Object> chartSeries(List<String> labels, List<Long> data) {
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("labels", labels);
        series.put("data", data);
        return series;
    }
}
