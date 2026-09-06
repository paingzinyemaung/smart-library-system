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

    List<BorrowRecord> findByUserIdAndStatusInOrderByIssueDateDesc(Long userId, List<BorrowRecord.BorrowStatus> statuses);

    List<BorrowRecord> findByStatusInOrderByDueDateAsc(List<BorrowRecord.BorrowStatus> statuses);

    List<BorrowRecord> findByStatusAndDueDateBefore(BorrowRecord.BorrowStatus status, LocalDate date);

    long countByStatusIn(List<BorrowRecord.BorrowStatus> statuses);

    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, List<BorrowRecord.BorrowStatus> statuses);

    @Query("SELECT COALESCE(SUM(b.fineAmount), 0.0) FROM BorrowRecord b WHERE b.status = :status")
    double sumFineByStatus(@Param("status") BorrowRecord.BorrowStatus status);

    @Query("SELECT b.book.id, COUNT(b) FROM BorrowRecord b GROUP BY b.book.id ORDER BY COUNT(b) DESC")
    List<Object[]> countBorrowsByBook();
}
