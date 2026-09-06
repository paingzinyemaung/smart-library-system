package com.oodd.library.repository;

import com.oodd.library.model.BorrowRecord;
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
}
