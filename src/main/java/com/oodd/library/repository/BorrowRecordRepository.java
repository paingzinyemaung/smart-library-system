package com.oodd.library.repository;

import com.oodd.library.model.BorrowRecord;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

    List<BorrowRecord> findByUserIdOrderByIssueDateDesc(Long userId);

    /** Profile history: single query with user + book joined (no N+1). */
    @Query("SELECT br FROM BorrowRecord br JOIN FETCH br.user JOIN FETCH br.book "
            + "WHERE br.user.id = :userId ORDER BY br.issueDate DESC, br.id DESC")
    List<BorrowRecord> findHistoryForUser(@Param("userId") Long userId);

    List<BorrowRecord> findByUserIdAndStatusInOrderByIssueDateDesc(Long userId, List<BorrowRecord.BorrowStatus> statuses);

    List<BorrowRecord> findByStatusInOrderByDueDateAsc(List<BorrowRecord.BorrowStatus> statuses);

    /** Active loans: single query with user + book joined (no N+1). */
    @Query("SELECT br FROM BorrowRecord br JOIN FETCH br.user JOIN FETCH br.book "
            + "WHERE br.status IN :statuses ORDER BY br.dueDate ASC")
    List<BorrowRecord> findActiveWithRelations(@Param("statuses") List<BorrowRecord.BorrowStatus> statuses);

    List<BorrowRecord> findByStatusAndDueDateBefore(BorrowRecord.BorrowStatus status, LocalDate date);

    long countByStatusIn(List<BorrowRecord.BorrowStatus> statuses);

    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, List<BorrowRecord.BorrowStatus> statuses);

    @Query("SELECT COALESCE(SUM(b.fineAmount), 0.0) FROM BorrowRecord b WHERE b.status = :status")
    double sumFineByStatus(@Param("status") BorrowRecord.BorrowStatus status);

    @Query("SELECT b.book.id, COUNT(b) FROM BorrowRecord b GROUP BY b.book.id ORDER BY COUNT(b) DESC")
    List<Object[]> countBorrowsByBook();

    /** Top-N most borrowed titles with their lifetime loan counts. */
    @Query("SELECT b.book.title, COUNT(b) FROM BorrowRecord b "
            + "GROUP BY b.book.id, b.book.title ORDER BY COUNT(b) DESC, b.book.title ASC")
    List<Object[]> findTopBorrowedBooks(Limit limit);

    /** Loan counts grouped by calendar month (yyyy-MM), most recent months first. */
    @Query(value = "SELECT DATE_FORMAT(issue_date, '%Y-%m') AS ym, COUNT(*) "
            + "FROM borrow_records "
            + "WHERE issue_date >= DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL :months MONTH) "
            + "GROUP BY ym ORDER BY ym ASC", nativeQuery = true)
    List<Object[]> findMonthlyLoanCounts(@Param("months") int months);

    /** Distribution helper: loans currently out (ISSUED + OVERDUE) counted in one query. */
    @Query("SELECT COUNT(b) FROM BorrowRecord b WHERE b.status IN :statuses")
    long countActiveLoans(@Param("statuses") List<BorrowRecord.BorrowStatus> statuses);
}
