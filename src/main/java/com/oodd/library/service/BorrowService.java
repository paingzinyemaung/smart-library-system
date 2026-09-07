package com.oodd.library.service;

import com.oodd.library.exception.ResourceNotFoundException;
import com.oodd.library.model.Book;
import com.oodd.library.model.BorrowRecord;
import com.oodd.library.model.User;
import com.oodd.library.repository.BookRepository;
import com.oodd.library.repository.BorrowRecordRepository;
import com.oodd.library.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BorrowService {

    public static final int MAX_ACTIVE_BORROWS = 3;

    private static final List<BorrowRecord.BorrowStatus> ACTIVE_STATUSES =
            List.of(BorrowRecord.BorrowStatus.ISSUED, BorrowRecord.BorrowStatus.OVERDUE);

    private final BorrowRecordRepository borrowRecordRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public BorrowService(BorrowRecordRepository borrowRecordRepository,
                         BookRepository bookRepository,
                         UserRepository userRepository) {
        this.borrowRecordRepository = borrowRecordRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public BorrowRecord issueBook(Long userId, Long bookId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", userId));
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Member account is disabled: " + user.getUsername());
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));
        if (book.getQuantity() == null || book.getQuantity() <= 0) {
            throw new IllegalArgumentException("\"" + book.getTitle() + "\" is out of stock (quantity: 0)");
        }
        if (borrowRecordRepository.existsByUserIdAndBookIdAndStatusIn(userId, bookId, ACTIVE_STATUSES)) {
            throw new IllegalArgumentException(user.getUsername() + " already has this book on loan");
        }
        long activeCount = borrowRecordRepository
                .findByUserIdAndStatusInOrderByIssueDateDesc(userId, ACTIVE_STATUSES).size();
        if (activeCount >= MAX_ACTIVE_BORROWS) {
            throw new IllegalArgumentException("Member already has " + MAX_ACTIVE_BORROWS
                    + " active borrows (limit reached)");
        }

        LocalDate today = LocalDate.now();
        BorrowRecord record = new BorrowRecord();
        record.setUser(user);
        record.setBook(book);
        record.setIssueDate(today);
        record.setDueDate(today.plusDays(BorrowRecord.LOAN_PERIOD_DAYS));
        record.setStatus(BorrowRecord.BorrowStatus.ISSUED);
        record.setFineAmount(0.0);

        book.setQuantity(book.getQuantity() - 1);
        book.recalculateStatus();
        bookRepository.save(book);

        return borrowRecordRepository.save(record);
    }

    @Transactional
    public BorrowRecord returnBook(Long recordId) {
        BorrowRecord record = borrowRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow record", recordId));
        if (record.getStatus() == BorrowRecord.BorrowStatus.RETURNED) {
            throw new IllegalArgumentException("This book has already been returned");
        }

        LocalDate today = LocalDate.now();
        record.setReturnDate(today);
        record.setFineAmount(record.getOverdueDays() * BorrowRecord.FINE_PER_DAY);
        record.setStatus(BorrowRecord.BorrowStatus.RETURNED);

        Book book = record.getBook();
        book.setQuantity((book.getQuantity() == null ? 0 : book.getQuantity()) + 1);
        book.recalculateStatus();
        bookRepository.save(book);

        return borrowRecordRepository.save(record);
    }

    /**
     * Flips every ISSUED record whose due date has passed to OVERDUE.
     * Returns the number of records updated.
     */
    @Transactional
    public int refreshOverdueRecords() {
        List<BorrowRecord> overdue = borrowRecordRepository
                .findByStatusAndDueDateBefore(BorrowRecord.BorrowStatus.ISSUED, LocalDate.now());
        for (BorrowRecord record : overdue) {
            record.setStatus(BorrowRecord.BorrowStatus.OVERDUE);
        }
        return overdue.size();
    }

    @Transactional(readOnly = true)
    public List<BorrowRecord> getRecordsForUser(Long userId) {
        return borrowRecordRepository.findHistoryForUser(userId);
    }

    @Transactional(readOnly = true)
    public List<BorrowRecord> getActiveRecords() {
        return borrowRecordRepository.findActiveWithRelations(ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public long countActiveBorrows() {
        return borrowRecordRepository.countByStatusIn(ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public double getTotalCollectedFines() {
        return borrowRecordRepository.sumFineByStatus(BorrowRecord.BorrowStatus.RETURNED);
    }

    @Transactional(readOnly = true)
    public BorrowRecord getRecordById(Long id) {
        return borrowRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrow record", id));
    }
}
