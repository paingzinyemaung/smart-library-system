package com.oodd.library.controller;

import com.oodd.library.service.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Admin-only report downloads. Responses carry Content-Disposition: attachment
 * so the browser triggers a direct file download.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/books/excel")
    public ResponseEntity<byte[]> exportBooksExcel() {
        return download(reportService.buildBookInventoryExcel(), XLSX_MEDIA_TYPE, "book-inventory.xlsx");
    }

    @GetMapping("/books/pdf")
    public ResponseEntity<byte[]> exportBooksPdf() {
        return download(reportService.buildBookInventoryPdf(), MediaType.APPLICATION_PDF, "book-inventory.pdf");
    }

    @GetMapping("/borrows/excel")
    public ResponseEntity<byte[]> exportBorrowsExcel() {
        return download(reportService.buildBorrowHistoryExcel(), XLSX_MEDIA_TYPE, "borrow-history.xlsx");
    }

    @GetMapping("/borrows/pdf")
    public ResponseEntity<byte[]> exportBorrowsPdf() {
        return download(reportService.buildBorrowHistoryPdf(), MediaType.APPLICATION_PDF, "borrow-history.pdf");
    }

    private ResponseEntity<byte[]> download(byte[] content, MediaType mediaType, String filename) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(mediaType)
                .contentLength(content.length)
                .body(content);
    }
}
