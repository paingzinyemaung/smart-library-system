package com.oodd.library.model;

//Product.java
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "books")
@Data
public class Book {
 
	 @Id
	 @GeneratedValue(strategy = GenerationType.IDENTITY)
	 private Long id;
	 
	 @Column(name = "bookCode", unique = true, nullable = false)
	 private String bookCode;
	 
	 @Column(name = "bookName", nullable = false)
	 private String bookName;
	 
	 @Column(name = "category")
	 private String category;
	 
	 @Column(name = "description", columnDefinition = "TEXT")
	 private String description;
	 
	 @Column(name = "price", precision = 10, scale = 2)
	 private BigDecimal price;
	 
	 @Column(name = "quantity")
	 private Integer quantity;
	 
	 @Column(name = "author", columnDefinition = "TEXT")
	 private String author;
	 
	 @Enumerated(EnumType.STRING)
	 @Column(name = "status")
	 private BookStatus status;
	 
	 @Column(name = "publishDate")
	 private LocalDateTime publishDate;
 
	 @Column(name = "registerDate")
	 private LocalDateTime registerDate;

	 //Getters and Setters
	 
	 	public Long getId() {
			return id;
		}
	
		public void setId(Long id) {
			this.id = id;
		}
	
		public String getBookCode() {
			return bookCode;
		}
	
		public void setBookCode(String bookCode) {
			this.bookCode = bookCode;
		}
	
		public String getBookName() {
			return bookName;
		}
	
		public void setBookName(String bookName) {
			this.bookName = bookName;
		}
	
		public String getCategory() {
			return category;
		}
	
		public void setCategory(String category) {
			this.category = category;
		}
	
		public String getDescription() {
			return description;
		}
	
		public void setDescription(String description) {
			this.description = description;
		}
	
		public BigDecimal getPrice() {
			return price;
		}
	
		public void setPrice(BigDecimal price) {
			this.price = price;
		}
	
		public Integer getQuantity() {
			return quantity;
		}
	
		public void setQuantity(Integer quantity) {
			this.quantity = quantity;
		}
	
		public String getAuthor() {
			return author;
		}
	
		public void setAuthor(String author) {
			this.author = author;
		}
	
		public BookStatus getStatus() {
			return status;
		}
	
		public void setStatus(BookStatus status) {
			this.status = status;
		}
	
		public LocalDateTime getPublishDate() {
			return publishDate;
		}
	
		public void setPublishDate(LocalDateTime publishDate) {
			this.publishDate = publishDate;
		}
	
		public LocalDateTime getRegisterDate() {
			return registerDate;
		}
	
		public void setRegisterDate(LocalDateTime registerDate) {
			this.registerDate = registerDate;
		}
	 
	 //Other Local Assist Functions
		
		@PreUpdate
		protected void onUpdate() {
		    registerDate = LocalDateTime.now();
		}
		
		public enum BookStatus {
		    AVAILABLE, CHECKED_OUT, ON_HOLD, WITHDRAWN;
		    
			/*
			 * public static BookStatus fromString(String value) { if (value == null) return
			 * null; switch (value.trim().toUpperCase().replace(' ', '_')) { case
			 * "AVAILABLE": return AVAILABLE; case "CHECKED_OUT": return CHECKED_OUT; case
			 * "ON_HOLD": return ON_HOLD; case "WITHDRAWN": return WITHDRAWN; default: throw
			 * new IllegalArgumentException("Unknown book status: " + value); } }
			 */
		}	
}