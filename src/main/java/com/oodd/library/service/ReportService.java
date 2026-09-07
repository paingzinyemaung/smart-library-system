package com.oodd.library.service;

import com.oodd.library.model.Book;
import com.oodd.library.model.BorrowRecord;
import com.oodd.library.model.User;
import com.oodd.library.repository.BookRepository;
import com.oodd.library.repository.BorrowRecordRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds downloadable admin reports (Excel .xlsx via Apache POI, PDF via OpenPDF)
 * for the book inventory and the full borrow history.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STAMP_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a");

    private static final byte[] XLSX_HEADER_RGB = {0x4F, 0x46, (byte) 0xE5};  // indigo-600
    private static final byte[] XLSX_TITLE_RGB = {0x1E, 0x1B, 0x4B};          // indigo-950
    private static final byte[] XLSX_ALT_RGB = {(byte) 0xF1, (byte) 0xF5, (byte) 0xF9};
    private static final Color PDF_HEADER_BG = new Color(0x4F, 0x46, 0xE5);
    private static final Color PDF_ALT_ROW_BG = new Color(0xF1, 0xF5, 0xF9);
    private static final Color PDF_STRIPE_BG = new Color(0xFF, 0xFF, 0xFF);

    private static final List<BorrowRecord.BorrowStatus> ACTIVE_STATUSES =
            List.of(BorrowRecord.BorrowStatus.ISSUED, BorrowRecord.BorrowStatus.OVERDUE);

    private final BookRepository bookRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    public ReportService(BookRepository bookRepository, BorrowRecordRepository borrowRecordRepository) {
        this.bookRepository = bookRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    // ------------------------------------------------------------------ rows

    /** Title, Author, ISBN, Category, Quantity (total copies), Available Copies. */
    @Transactional(readOnly = true)
    public List<Object[]> bookInventoryRows() {
        Map<Long, Long> activeLoansByBook = new HashMap<>();
        for (Object[] row : borrowRecordRepository.countActiveLoansByBook(ACTIVE_STATUSES)) {
            activeLoansByBook.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return bookRepository.findAllWithCategory().stream()
                .map(b -> {
                    int available = b.getQuantity() == null ? 0 : b.getQuantity();
                    int onLoan = activeLoansByBook.getOrDefault(b.getId(), 0L).intValue();
                    return new Object[]{
                            b.getTitle(),
                            nullToEmpty(b.getAuthor()),
                            nullToEmpty(b.getIsbn()),
                            b.getCategory() != null ? b.getCategory().getName() : "Uncategorized",
                            available + onLoan,
                            available
                    };
                })
                .toList();
    }

    /** Member Name, Book Title, Issue Date, Due Date, Status, Fine Amount. */
    @Transactional(readOnly = true)
    public List<Object[]> borrowHistoryRows() {
        return borrowRecordRepository.findAllWithRelations().stream()
                .map(r -> new Object[]{
                        memberName(r.getUser()),
                        r.getBook().getTitle(),
                        r.getIssueDate() != null ? r.getIssueDate().format(DATE_FMT) : "",
                        r.getDueDate() != null ? r.getDueDate().format(DATE_FMT) : "",
                        prettyStatus(r.getStatus().name()),
                        r.getAccruedFine()
                })
                .toList();
    }

    // ----------------------------------------------------------------- Excel

    @Transactional(readOnly = true)
    public byte[] buildBookInventoryExcel() {
        return buildExcel("Book Inventory", "Smart Library — Book Inventory Report",
                new String[]{"Title", "Author", "ISBN", "Category", "Quantity", "Available Copies"},
                bookInventoryRows(), new int[]{45, 28, 18, 20, 12, 18});
    }

    @Transactional(readOnly = true)
    public byte[] buildBorrowHistoryExcel() {
        return buildExcel("Borrow History", "Smart Library — Borrow History Report",
                new String[]{"Member Name", "Book Title", "Issue Date", "Due Date", "Status", "Fine Amount"},
                borrowHistoryRows(), new int[]{26, 42, 14, 14, 12, 14});
    }

    private byte[] buildExcel(String sheetName, String title, String[] headers,
                              List<Object[]> rows, int[] widths) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = workbook.createSheet(sheetName);
            CellStyle titleStyle = excelTitleStyle(workbook);
            CellStyle subtitleStyle = excelSubtitleStyle(workbook);
            CellStyle headerStyle = excelHeaderStyle(workbook);
            CellStyle textStyle = excelTextStyle(workbook, false);
            CellStyle textAltStyle = excelTextStyle(workbook, true);
            CellStyle numberStyle = excelNumberStyle(workbook, "0", false);
            CellStyle numberAltStyle = excelNumberStyle(workbook, "0", true);
            CellStyle moneyStyle = excelNumberStyle(workbook, "#,##0.00", false);
            CellStyle moneyAltStyle = excelNumberStyle(workbook, "#,##0.00", true);

            int lastCol = headers.length - 1;

            Row titleRow = sheet.createRow(0);
            titleRow.setHeightInPoints(30);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(title);
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));

            Row stampRow = sheet.createRow(1);
            Cell stampCell = stampRow.createCell(0);
            stampCell.setCellValue("Generated " + LocalDateTime.now().format(STAMP_FMT)
                    + "  ·  " + rows.size() + " record(s)");
            stampCell.setCellStyle(subtitleStyle);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, lastCol));

            Row headerRow = sheet.createRow(2);
            headerRow.setHeightInPoints((short) 20);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 3;
            for (Object[] data : rows) {
                Row row = sheet.createRow(rowIndex);
                boolean alt = rowIndex % 2 == 0;
                for (int i = 0; i < data.length; i++) {
                    Cell cell = row.createCell(i);
                    Object value = data[i];
                    if (value instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                        cell.setCellStyle(isMoneyColumn(headers[i])
                                ? (alt ? moneyAltStyle : moneyStyle)
                                : (alt ? numberAltStyle : numberStyle));
                    } else {
                        cell.setCellValue(value == null ? "" : value.toString());
                        cell.setCellStyle(alt ? textAltStyle : textStyle);
                    }
                }
                rowIndex++;
            }

            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            sheet.createFreezePane(0, 3);
            sheet.setZoom(90);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build Excel report: " + sheetName, e);
        }
    }

    private boolean isMoneyColumn(String header) {
        return header.toLowerCase().contains("fine");
    }

    private CellStyle excelTitleStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        font.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(XLSX_TITLE_RGB, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle excelSubtitleStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setItalic(true);
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.SEA_GREEN.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        ((XSSFCellStyle) style).setBottomBorderColor(
                new XSSFColor(new byte[]{(byte) 0xCB, (byte) 0xD5, (byte) 0xE1}, null));
        return style;
    }

    private CellStyle excelHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, null));
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(XLSX_HEADER_RGB, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyBorders(style, new byte[]{(byte) 0x31, (byte) 0x2E, (byte) 0x64});
        return style;
    }

    private CellStyle excelTextStyle(XSSFWorkbook wb, boolean alt) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(false);
        applyZebra(style, alt);
        applyBorders(style, new byte[]{(byte) 0xCB, (byte) 0xD5, (byte) 0xE1});
        return style;
    }

    private CellStyle excelNumberStyle(XSSFWorkbook wb, String format, boolean alt) {
        CellStyle style = excelTextStyle(wb, alt);
        DataFormat df = wb.createDataFormat();
        style.setDataFormat(df.getFormat(format));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private void applyZebra(CellStyle style, boolean alt) {
        if (alt) {
            style.setFillForegroundColor(new XSSFColor(XLSX_ALT_RGB, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
    }

    private void applyBorders(CellStyle cellStyle, byte[] rgb) {
        XSSFCellStyle style = (XSSFCellStyle) cellStyle;
        XSSFColor color = new XSSFColor(rgb, null);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(color);
        style.setBottomBorderColor(color);
        style.setLeftBorderColor(color);
        style.setRightBorderColor(color);
    }

    // ------------------------------------------------------------------- PDF

    @Transactional(readOnly = true)
    public byte[] buildBookInventoryPdf() {
        return buildPdf("Book Inventory Report",
                new String[]{"Title", "Author", "ISBN", "Category", "Quantity", "Available"},
                new float[]{26f, 18f, 14f, 14f, 9f, 11f},
                bookInventoryRows(),
                new int[]{0, 1, 2, 3});
    }

    @Transactional(readOnly = true)
    public byte[] buildBorrowHistoryPdf() {
        return buildPdf("Borrow History Report",
                new String[]{"Member Name", "Book Title", "Issue Date", "Due Date", "Status", "Fine"},
                new float[]{18f, 28f, 12f, 12f, 13f, 10f},
                borrowHistoryRows(),
                new int[]{0, 1});
    }

    private byte[] buildPdf(String title, String[] headers, float[] widths,
                            List<Object[]> rows, int[] leftAlignedCols) {
        Document document = new Document(PageSize.A4.rotate(), 32, 32, 36, 36);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 17, Font.BOLD, new Color(0x1E, 0x1B, 0x4B));
            Font metaFont = new Font(Font.HELVETICA, 9, Font.ITALIC, new Color(0x64, 0x74, 0x8B));
            Font headFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font cellFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x1E, 0x29, 0x3B));
            Font statusFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(0x43, 0x38, 0xCA));

            document.add(new Paragraph(title, titleFont));
            document.add(new Paragraph(
                    "Smart Library & Digital Resource Management System  ·  Generated "
                            + LocalDateTime.now().format(STAMP_FMT) + "  ·  " + rows.size() + " record(s)",
                    metaFont));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(headers.length);
            table.setWidthPercentage(98);
            table.setWidths(widths);
            table.setSpacingBefore(6f);
            table.setSplitLate(false);
            table.setHeaderRows(1);

            for (String header : headers) {
                table.addCell(pdfHeaderCell(header, headFont));
            }

            int i = 0;
            for (Object[] data : rows) {
                boolean alt = i++ % 2 == 1;
                for (int c = 0; c < data.length; c++) {
                    Object value = data[c];
                    Font font = isStatusHeader(headers[c]) ? statusFont : cellFont;
                    int alignment = isNumeric(value)
                            ? Element.ALIGN_RIGHT
                            : (isLeftAligned(leftAlignedCols, c) ? Element.ALIGN_LEFT : Element.ALIGN_CENTER);
                    table.addCell(pdfDataCell(formatPdfValue(value, headers[c]), font, alignment,
                            alt ? PDF_ALT_ROW_BG : PDF_STRIPE_BG));
                }
            }
            document.add(table);

            document.add(Chunk.NEWLINE);
            Paragraph footer = new Paragraph("© 2026 Smart Library — confidential, for authorised staff only.",
                    new Font(Font.HELVETICA, 8, Font.NORMAL, new Color(0x94, 0xA3, 0xB8)));
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new UncheckedIOException(
                    new IOException("Failed to build PDF report: " + title, e));
        }
    }

    private boolean isStatusHeader(String header) {
        return "Status".equals(header);
    }

    private boolean isLeftAligned(int[] cols, int c) {
        for (int col : cols) {
            if (col == c) {
                return true;
            }
        }
        return false;
    }

    private boolean isNumeric(Object value) {
        return value instanceof Number;
    }

    private String formatPdfValue(Object value, String header) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double d && isMoneyColumn(header)) {
            return String.format("$%.2f", d);
        }
        return String.valueOf(value);
    }

    private PdfPCell pdfHeaderCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(PDF_HEADER_BG);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6f);
        cell.setBorderColorBottom(new Color(0x31, 0x2E, 0x64));
        cell.setBorderColorTop(new Color(0x31, 0x2E, 0x64));
        return cell;
    }

    private PdfPCell pdfDataCell(String text, Font font, int alignment, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5f);
        cell.setBackgroundColor(background);
        cell.setBorderColor(new Color(0xCB, 0xD5, 0xE1));
        return cell;
    }

    // --------------------------------------------------------------- helpers

    private String memberName(User user) {
        String first = nullToEmpty(user.getFirstName()).trim();
        String last = nullToEmpty(user.getLastName()).trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return user.getUsername() != null ? user.getUsername() : "(unknown)";
    }

    private String prettyStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "";
        }
        String lower = status.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
