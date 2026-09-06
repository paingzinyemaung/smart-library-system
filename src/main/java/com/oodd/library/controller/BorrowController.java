package com.oodd.library.controller;

import com.oodd.library.dto.BorrowRequest;
import com.oodd.library.model.BorrowRecord;
import com.oodd.library.model.User;
import com.oodd.library.repository.UserRepository;
import com.oodd.library.service.BorrowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/borrow")
public class BorrowController {

    private final BorrowService borrowService;
    private final UserRepository userRepository;

    public BorrowController(BorrowService borrowService, UserRepository userRepository) {
        this.borrowService = borrowService;
        this.userRepository = userRepository;
    }

    @PostMapping("/issue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> issueBook(@Valid @RequestBody BorrowRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            BorrowRecord saved = borrowService.issueBook(request.getUserId(), request.getBookId());
            response.put("success", true);
            response.put("message", "\"" + saved.getBook().getTitle() + "\" issued to "
                    + saved.getUser().getUsername() + " (due " + saved.getDueDate() + ")");
            response.put("record", toMap(saved));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> returnBook(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            BorrowRecord saved = borrowService.returnBook(id);
            double fine = saved.getFineAmount() == null ? 0.0 : saved.getFineAmount();
            response.put("success", true);
            response.put("message", fine > 0
                    ? "\"" + saved.getBook().getTitle() + "\" returned. Fine collected: $" + String.format("%.2f", fine)
                    : "\"" + saved.getBook().getTitle() + "\" returned on time. No fine.");
            response.put("record", toMap(saved));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getActiveRecords() {
        borrowService.refreshOverdueRecords();
        List<Map<String, Object>> records = borrowService.getActiveRecords().stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(records);
    }

    @GetMapping("/members")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getMembers() {
        List<Map<String, Object>> members = userRepository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream()
                .filter(User::isEnabled)
                .map(user -> {
                    String full = ((user.getFirstName() != null ? user.getFirstName() + " " : "")
                            + (user.getLastName() != null ? user.getLastName() : "")).trim();
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", user.getId());
                    m.put("username", user.getUsername());
                    m.put("email", user.getEmail());
                    m.put("displayName", full.isEmpty() ? user.getUsername() : full);
                    m.put("role", user.getRole() != null ? user.getRole().name() : null);
                    return m;
                })
                .toList();
        return ResponseEntity.ok(members);
    }

    private Map<String, Object> toMap(BorrowRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("issueDate", record.getIssueDate() != null ? record.getIssueDate().toString() : null);
        map.put("dueDate", record.getDueDate() != null ? record.getDueDate().toString() : null);
        map.put("returnDate", record.getReturnDate() != null ? record.getReturnDate().toString() : null);
        map.put("status", record.getStatus() != null ? record.getStatus().name() : null);
        map.put("fineAmount", record.getFineAmount());
        map.put("accruedFine", record.getAccruedFine());
        map.put("overdueDays", record.getOverdueDays());
        if (record.getBook() != null) {
            map.put("bookId", record.getBook().getId());
            map.put("bookCode", record.getBook().getBookCode());
            map.put("bookTitle", record.getBook().getTitle());
        }
        if (record.getUser() != null) {
            map.put("userId", record.getUser().getId());
            map.put("username", record.getUser().getUsername());
            map.put("memberName", ((record.getUser().getFirstName() != null ? record.getUser().getFirstName() + " " : "")
                    + (record.getUser().getLastName() != null ? record.getUser().getLastName() : "")).trim());
            map.put("memberEmail", record.getUser().getEmail());
        }
        return map;
    }
}
