package com.oodd.library.service;

import com.oodd.library.model.Book;
import com.oodd.library.model.Category;
import com.oodd.library.repository.BookRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ExcelUploadService {

    private final BookRepository bookRepository;
    private final CategoryService categoryService;

    public ExcelUploadService(BookRepository bookRepository, CategoryService categoryService) {
        this.bookRepository = bookRepository;
        this.categoryService = categoryService;
    }

    @Transactional
    public Map<String, Object> processUploadFile(MultipartFile file) throws IOException {
        List<String[]> rows = parseRows(file);

        int successCount = 0;
        int errorCount = 0;
        List<String> errors = new ArrayList<>();
        List<Book> savedBooks = new ArrayList<>();

        // Row 0 is the header, business rows start at index 1
        for (int i = 1; i < rows.size(); i++) {
            int rowNum = i + 1;
            String[] columns = rows.get(i);
            try {
                Book book = parseBookFromRow(columns);
                if (book == null) {
                    continue; // blank trailing row
                }
                savedBooks.add(upsertBook(book));
                successCount++;
            } catch (Exception e) {
                errors.add("Row " + rowNum + ": " + e.getMessage());
                errorCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "File processed successfully");
        result.put("totalRows", successCount + errorCount);
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("errors", errors);
        result.put("books", savedBooks);
        return result;
    }

    private List<String[]> parseRows(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase();

        if (fileName.endsWith(".csv")) {
            return parseCsv(file);
        }
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return parseExcel(file);
        }
        throw new IllegalArgumentException("Unsupported file type: only .xlsx, .xls and .csv are allowed");
    }

    private List<String[]> parseExcel(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                String[] columns = new String[6];
                for (int col = 0; col < 6; col++) {
                    Cell cell = row.getCell(col);
                    columns[col] = cell == null ? null : formatter.formatCellValue(cell).trim();
                }
                rows.add(columns);
            }
        }
        return rows;
    }

    private List<String[]> parseCsv(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] raw = line.split(",", -1);
                String[] columns = new String[6];
                for (int col = 0; col < 6; col++) {
                    columns[col] = col < raw.length ? stripQuotes(raw[col]).trim() : null;
                }
                rows.add(columns);
            }
        }
        return rows;
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Column layout: A = Book Code, B = Title, C = Author, D = Category, E = Quantity, F = ISBN.
     */
    private Book parseBookFromRow(String[] columns) {
        String bookCode = columns[0];
        String title = columns[1];

        if (isBlank(bookCode) && isBlank(title)) {
            return null; // skip fully empty rows
        }
        if (isBlank(bookCode)) {
            throw new IllegalArgumentException("Book Code is required");
        }
        if (isBlank(title)) {
            throw new IllegalArgumentException("Title is required");
        }

        Book book = new Book();
        book.setBookCode(bookCode.trim());
        book.setTitle(title.trim());
        book.setAuthor(isBlank(columns[2]) ? null : columns[2].trim());
        book.setIsbn(columns.length > 5 && !isBlank(columns[5]) ? columns[5].trim() : null);

        String category = isBlank(columns[3]) ? "Uncategorized" : columns[3].trim();
        Category cat = new Category();
        cat.setId(null);
        // Stash the category name on the transient entity so upsertBook can resolve/create it.
        cat.setName(category);
        book.setCategory(cat);

        int quantity = 0;
        if (!isBlank(columns[4])) {
            try {
                quantity = (int) Double.parseDouble(columns[4].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid quantity format: " + columns[4]);
            }
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
        book.setQuantity(quantity);
        book.recalculateStatus();
        return book;
    }

    private Book upsertBook(Book book) {
        Category resolved = categoryService.findOrCreateByName(book.getCategory().getName());

        Optional<Book> existing = bookRepository.findByBookCode(book.getBookCode());
        if (existing.isPresent()) {
            Book current = existing.get();
            current.setTitle(book.getTitle());
            current.setAuthor(book.getAuthor());
            if (book.getIsbn() != null) {
                current.setIsbn(book.getIsbn());
            }
            current.setQuantity(book.getQuantity());
            current.setCategory(resolved);
            current.recalculateStatus();
            return bookRepository.save(current);
        }
        book.setCategory(resolved);
        return bookRepository.save(book);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public byte[] generateExcelTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Books Template");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"Book Code*", "Title*", "Author", "Category", "Quantity*", "ISBN"};
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            String[] samples = {
                    "B001", "The Silent Patient", "Alex Michaelides", "Thriller", "25", "978-1250301697",
                    "B002", "Clean Code", "Robert C. Martin", "Programming", "4", "978-0132350884",
                    "B003", "The Pragmatic Programmer", "Hunt & Thomas", "Programming", "0", "978-0201616224"
            };
            int cols = 6;
            for (int r = 0; r < 3; r++) {
                Row sampleRow = sheet.createRow(r + 1);
                for (int c = 0; c < cols; c++) {
                    sampleRow.createCell(c).setCellValue(samples[r * cols + c]);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                workbook.write(outputStream);
                return outputStream.toByteArray();
            }
        }
    }
}
