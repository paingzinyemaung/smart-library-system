package com.oodd.library.controller;

import com.oodd.library.model.Book;
import com.oodd.library.model.User;
import com.oodd.library.repository.UserRepository;
import com.oodd.library.service.ExcelService;
import com.oodd.library.service.BookService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
 public class BookController {
 
 @Autowired
 private ExcelService excelService;
 
 @Autowired
 private BookService bookService;
 
 @Autowired
 private UserRepository userRepository;
 
 private User currentUser() {
     Authentication auth = SecurityContextHolder.getContext().getAuthentication();
     if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
         return null;
     }
     return userRepository.findByEmail(auth.getName()).orElse(null);
 }
 
 @GetMapping("/dashboard")
 public String dashboard(@RequestParam(defaultValue = "1") int page,
	     @RequestParam(defaultValue = "5") int size,
	     @RequestParam(required = false) String search,Model model) {
     User user = currentUser();
     model.addAttribute("user", user);
     model.addAttribute("isAdmin", user != null && user.getRole() == User.UserRole.ADMIN);
     
     // Get dashboard statistics
     Map<String, Object> stats = bookService.getDashboardStatistics();
     model.addAttribute("stats", stats);
     
     // Get recent products
     List<Book> recentBooks = bookService.getRecentBooks();
     model.addAttribute("books", recentBooks);
     
     // Get products by category for chart
     Map<String, Long> categoryCounts = bookService.getBooksByCategory();
     model.addAttribute("categoryData", categoryCounts);
     
     // Get low stock products
     List<Book> lowStockBooks = bookService.getLowStockBooks();
     model.addAttribute("lowStockBooks", lowStockBooks);
     
     // Get number of categories
     int numCategory = bookService.getNumofCategories();
     model.addAttribute("categoryCounts", numCategory);
     
     //---------------------------Pagniation-------------------------------------
     // Get all products 
     List<Book> allBooks = bookService.getAllBooks();
  	  
  	  // Filter by search 
      List<Book> filteredBooks = allBooks; 
      if (search!= null && !search.isEmpty()) { filteredBooks = allBooks.stream()
    		  .filter(p -> (p.getBookCode() != null &&
    		  	p.getBookCode().toLowerCase().contains(search.toLowerCase())) ||
    				  		(p.getBookName() != null &&
    				  			p.getBookName().toLowerCase().contains(search.toLowerCase())) ||
    				  		(p.getCategory() != null &&
    				  			p.getCategory().toLowerCase().contains(search.toLowerCase())) )
    		  					.collect(Collectors.toList()); 
      }
  	  
  	  // Calculate pagination
  	  
  	  Page<Book> bookPage = bookService.getBooksWithPagination(page, size, search);
  	  
  	  // Add to model 
  	  model.addAttribute("books", bookPage.getContent()); 
  	  //current page products 
  	  model.addAttribute("currentPage", page);
  	  model.addAttribute("pageSize", size); 
  	  model.addAttribute("totalPages",bookPage.getTotalPages()); 
  	  model.addAttribute("totalBooks", bookPage.getTotalElements()); 
  	  model.addAttribute("search", search);
  	  
  	  model.addAttribute("stats", bookService.getDashboardStatistics());
  	  model.addAttribute("categoryData", bookService.getBooksByCategory());
     
     return "dashboard";
 }

	
 @PostMapping("/api/upload-excel")
 @ResponseBody
 public ResponseEntity<Map<String, Object>> uploadExcel(@RequestParam("file") MultipartFile file) {
     Map<String, Object> response = new HashMap<>();
     
     try {
         if (file.isEmpty()) {
             response.put("success", false);
             response.put("message", "Please select a file to upload");
             return ResponseEntity.badRequest().body(response);
         }
         
         // Check file type
         String fileName = file.getOriginalFilename();
         if (fileName != null && 
             !(fileName.endsWith(".xlsx") || fileName.endsWith(".xls") || fileName.endsWith(".csv"))) {
             response.put("success", false);
             response.put("message", "Only Excel files (.xlsx, .xls, .csv) are allowed");
             return ResponseEntity.badRequest().body(response);
         }
         
         // Process Excel file
         Map<String, Object> processResult = excelService.processExcelFile(file);
         
         response.put("success", processResult.get("success"));
         response.put("message", processResult.get("message"));
         response.put("data", processResult);
         
         return ResponseEntity.ok(response);
         
     } catch (Exception e) {
         response.put("success", false);
         response.put("message", "Error processing file: " + e.getMessage());
         return ResponseEntity.internalServerError().body(response);
     }
 }
 
 @GetMapping("/api/download-template")
 public ResponseEntity<Resource> downloadTemplate() throws IOException {
     byte[] excelData = excelService.generateExcelTemplate();
     
     ByteArrayResource resource = new ByteArrayResource(excelData);
     
     return ResponseEntity.ok()
             .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=books_template.xlsx")
             .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
             .contentLength(excelData.length)
             .body(resource);
 }
 
 @GetMapping("/api/books")
 @ResponseBody
 public ResponseEntity<List<Book>> getAllBooks() {
     List<Book> books = bookService.getAllBooks();
     return ResponseEntity.ok(books);
 }
 
 @GetMapping("/api/books/stats")
 @ResponseBody
 public ResponseEntity<Map<String, Object>> getStats() {
    
     // Get dashboard statistics
     Map<String, Object> stats = bookService.getDashboardStatistics();
     
     return ResponseEntity.ok(stats);
 }
 
 @DeleteMapping("/api/books/{id}")
 @ResponseBody
 public ResponseEntity<Map<String, Object>> deleteBook(@PathVariable Long id) {
     Map<String, Object> response = new HashMap<>();
     
     try {
         bookService.deleteBook(id);
         response.put("success", true);
         response.put("message", "Book deleted successfully");
         return ResponseEntity.ok(response);
     } catch (Exception e) {
         response.put("success", false);
         response.put("message", "Error deleting book: " + e.getMessage());
         return ResponseEntity.badRequest().body(response);
     }
 }
 
	
	
}
