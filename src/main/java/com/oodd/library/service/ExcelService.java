package com.oodd.library.service;

import com.oodd.library.model.Book;
import com.oodd.library.model.Book.BookStatus;
import com.oodd.library.repository.BookRepository;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExcelService {
 
 @Autowired
 private BookRepository bookRepository;
 
 public Map<String, Object> processExcelFile(MultipartFile file) throws IOException {
     Map<String, Object> result = new HashMap<>();
     List<Book> books = new ArrayList<>();
     List<String> errors = new ArrayList<>();
     int successCount = 0;
     int errorCount = 0;
     
     try (InputStream inputStream = file.getInputStream();
          Workbook workbook = WorkbookFactory.create(inputStream)) {
         
         Sheet sheet = workbook.getSheetAt(0);
         Iterator<Row> rowIterator = sheet.iterator();
         
         // Skip header row
         if (rowIterator.hasNext()) {
             rowIterator.next();
         }
         
         int rowNum = 2; // Excel row numbers start from 1, header is row 1
         
         while (rowIterator.hasNext()) {
             Row row = rowIterator.next();
             try {
                 Book book = parseBookFromRow(row);
                 
                 // Validate product
                 if (isValidBook(book)) {
                     // Check if product already exists
                     Optional<Book> existingBook = 
                         bookRepository.findByBookCode(book.getBookCode());
                     
                     if (existingBook.isPresent()) {
                         // Update existing product
                         Book existing = existingBook.get();
                         updateBook(existing, book);
                         bookRepository.save(existing);
                     } else {
                         // Save new product
                    	 bookRepository.save(book);
                     }
                     
                     books.add(book);
                     successCount++;
                 } else {
                     errors.add("Row " + rowNum + ": Invalid book data");
                     errorCount++;
                 }
                 
             } catch (Exception e) {
                 errors.add("Row " + rowNum + ": " + e.getMessage());
                 errorCount++;
             }
             rowNum++;
         }
         
         result.put("success", true);
         result.put("message", "File processed successfully");
         result.put("totalRows", rowNum - 2);
         result.put("successCount", successCount);
         result.put("errorCount", errorCount);
         result.put("errors", errors);
         result.put("books", books);
         
     } catch (Exception e) {
         result.put("success", false);
         result.put("message", "Error processing file: " + e.getMessage());
     }
     
     return result;
 }
 
 private Book parseBookFromRow(Row row) {
     Book book = new Book();
     DataFormatter formatter = new DataFormatter();
     
     // Column A (0): Book Code
     String bookCode = getCellValueAsString(row.getCell(0), formatter);
     if (bookCode == null || bookCode.trim().isEmpty()) {
         throw new IllegalArgumentException("Book Code is required");
     }
     book.setBookCode(bookCode.trim());
     
     // Column B (1): Book Name
     String bookName = getCellValueAsString(row.getCell(1), formatter);
     if (bookName == null || bookName.trim().isEmpty()) {
         throw new IllegalArgumentException("Book Name is required");
     }
     book.setBookName(bookName.trim());
     
     // Column C (2): Author
     String author = getCellValueAsString(row.getCell(2), formatter);
     book.setAuthor(author);
     
     // Column D (3): Category
     String category = getCellValueAsString(row.getCell(3), formatter);
     book.setCategory(category);
     
     // Column E (4): Description
     String description = getCellValueAsString(row.getCell(4), formatter);
     book.setDescription(description);
     
     // Column F (5): Price
     String priceStr = getCellValueAsString(row.getCell(5), formatter);
     if (priceStr != null && !priceStr.trim().isEmpty()) {
         try {
             book.setPrice(new BigDecimal(priceStr.trim()));
         } catch (NumberFormatException e) {
             throw new IllegalArgumentException("Invalid price format: " + priceStr);
         }
     }
     
     // Column G (6): PublishDate
     String publishDateStr = getCellValueAsString(row.getCell(6), formatter);
     if (publishDateStr != null && !publishDateStr.trim().isEmpty()) {
         book.setPublishDate(parseDate(publishDateStr.trim()));
     }
     
     // Column H (7): Quantity
     String quantityStr = getCellValueAsString(row.getCell(7), formatter);
     if (quantityStr != null && !quantityStr.trim().isEmpty()) {
         try {
             book.setQuantity(Integer.parseInt(quantityStr.trim()));
         } catch (NumberFormatException e) {
             throw new IllegalArgumentException("Invalid quantity format: " + quantityStr);
         }
     }
     
     // Column I (8): RegisterDate
     String registerDateStr = getCellValueAsString(row.getCell(8), formatter);
     if (registerDateStr != null && !registerDateStr.trim().isEmpty()) {
         book.setRegisterDate(parseDate(registerDateStr.trim()));
     }
     
     // Column J (9): Status
     String statusStr = getCellValueAsString(row.getCell(9), formatter);
     if (statusStr != null && !statusStr.trim().isEmpty()) {
         try {
             String status = statusStr.trim().toUpperCase().replace(' ', '_');
             book.setStatus(BookStatus.valueOf(status));
         } catch (IllegalArgumentException e) {
             throw new IllegalArgumentException("Invalid status: " + statusStr + 
                 " (Available, Check Out, On Hold, Withdrawn)");
         }
     } else {
         book.setStatus(BookStatus.AVAILABLE);
     }
     
     return book;
 }
 
 private String getCellValueAsString(Cell cell, DataFormatter formatter) {
     if (cell == null) return null;
     String value = formatter.formatCellValue(cell);
     return value != null ? value.trim() : null;
 }
 
 private LocalDateTime parseDate(String dateStr) {
     // Support multiple date formats
     DateTimeFormatter[] formatters = {
         DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
         DateTimeFormatter.ofPattern("yyyy-MM-dd"),
         DateTimeFormatter.ofPattern("M/d/yyyy"),
         DateTimeFormatter.ofPattern("yyyy/MM/dd"),
         DateTimeFormatter.ofPattern("dd/MM/yyyy")
     };
     
     for (DateTimeFormatter formatter : formatters) {
         try {
             return LocalDateTime.parse(dateStr, formatter);
         } catch (Exception e) {
             // Try next format
         }
     }
     
     // Try with LocalDate
     for (DateTimeFormatter formatter : formatters) {
         try {
             java.time.LocalDate date = java.time.LocalDate.parse(dateStr, formatter);
             return date.atStartOfDay();
         } catch (Exception e) {
             // Try next format
         }
     }
     
     throw new IllegalArgumentException("Unable to parse date: " + dateStr);
 }
 
 private boolean isValidBook(Book book) {
     return book.getBookCode() != null && !book.getBookCode().isEmpty()
         && book.getBookName() != null && !book.getBookName().isEmpty();
 }
 
 private void updateBook(Book existing, Book newData) {
     existing.setBookName(newData.getBookName());
     existing.setAuthor(newData.getAuthor());
     existing.setCategory(newData.getCategory());
     existing.setDescription(newData.getDescription());
     existing.setPrice(newData.getPrice());
     existing.setPublishDate(newData.getPublishDate());
     existing.setQuantity(newData.getQuantity());
     existing.setRegisterDate(newData.getRegisterDate());
     existing.setStatus(newData.getStatus());
 }
 
 // Generate Excel template for download
 public byte[] generateExcelTemplate() throws IOException {
     Workbook workbook = new XSSFWorkbook();
     Sheet sheet = workbook.createSheet("Books Template");
     
     // Create header row
     Row headerRow = sheet.createRow(0);
     String[] headers = {
         "Book Code*", "Book Name*", "Author", "Category", "Description", 
         "Price*", "Publish_date*", "Quantity*", "Register_date*", "Status"
     };
     
     for (int i = 0; i < headers.length; i++) {
         Cell cell = headerRow.createCell(i);
         cell.setCellValue(headers[i]);
         
         // Style for header
         CellStyle headerStyle = workbook.createCellStyle();
         Font headerFont = workbook.createFont();
         headerFont.setBold(true);
         headerStyle.setFont(headerFont);
         cell.setCellStyle(headerStyle);
     }
     
     // Add sample data row
     Row sampleRow = sheet.createRow(1);
     String[] sampleData = {
    	"B001",	"The Silent Patient", "Alex Michaelides", "Thriller",
    	"A psychological thriller about a woman who stops speaking after killing her husband",
    	"14.99", "2026-01-10", "25","2026-01-10", "AVAILABLE"

     };
     
     for (int i = 0; i < sampleData.length; i++) {
         sampleRow.createCell(i).setCellValue(sampleData[i]);
     }
     
     // Auto size columns
     for (int i = 0; i < headers.length; i++) {
         sheet.autoSizeColumn(i);
     }
     
     // Convert to byte array
     byte[] excelBytes;
     try (var outputStream = new java.io.ByteArrayOutputStream()) {
         workbook.write(outputStream);
         excelBytes = outputStream.toByteArray();
     }
     
     workbook.close();
     return excelBytes;
 }
}