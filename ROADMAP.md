# Smart Library & Digital Resource Management System - Master Roadmap

## System Architecture & Tech Stack

- Backend: Java 21, Spring Boot 3.5.9, Spring Data JPA, Hibernate
- Frontend: HTML5, Thymeleaf, Tailwind CSS / Custom CSS, JavaScript
- Database: MySQL
- Server Port: 8585

---

## Phase 1: UI/UX Modernization & Full Responsiveness

- [x] Modernize `login.html` and `register.html` with clean glassmorphic/sleek design.
- [x] Make all pages (Dashboard, Auth, Tables, Forms) 100% responsive (Mobile, Tablet, Desktop).
- [x] Add Toast Notifications (Success/Error alerts) for user actions.
- [x] Improve UI layout with side navigation, modern icons, and dynamic statistics cards.

## Phase 2: Role-Based Access & User Profile Management

- [x] Implement User Roles (`ROLE_ADMIN`, `ROLE_MEMBER` / `ROLE_STUDENT`).
- [x] Create User Profile page (View borrowed history, update profile details).
- [x] Restrict Admin-only features (Excel upload, book creation, issue/return management).

## Phase 3: Book & Category Management System

- [x] Full CRUD operations for Books (Add, Edit, View, Delete).
- [x] Category Management (Create/Edit categories, assign books to categories).
- [x] Backend processing for Bulk Book Upload via Excel (`.xlsx`, `.csv`).
- [x] Low-stock alert automation (Highlight books with quantity < threshold).

## Phase 4: OODD Core Business Logic (Book Issuing, Returning & Fines)

- [x] Create `BorrowRecord` Entity & Repository (Track issue date, due date, return date, status).
- [x] Implement **Issue Book Logic**:
  - Check user eligibility & book stock availability.
  - Automatically deduct book quantity by 1.
  - Create active record (`BORROWED`).
- [x] Implement **Return Book Logic**:
  - Increase book stock quantity by 1.
  - Update status to `RETURNED` with actual return date.
- [x] Implement **Overdue & Fine Calculation**:
  - Automatically calculate fine amount for late returns based on overdue days.

## Phase 5: Digital Resources & PDF Reader Integration

- [x] Enable PDF/E-book file upload for books/resources.
- [x] Secure File Storage handling (Local directory or media storage).
- [x] In-browser PDF Viewer / Reader integration for students/members.
- [x] Download resource functionality with security checks.

## Phase 6: Advanced Search, Dynamic Filtering & Pagination

- [ ] Dynamic Search bar (Search by Title, Author, Book Code, ISBN).
- [ ] Multi-criteria Filter (Filter by Category, Availability Status, Digital vs Physical).
- [ ] Backend Server-side Pagination for large book tables.

## Phase 7: Analytics, Reports & Final System Polish

- [ ] Dashboard Charts (Visual statistics for most borrowed books, active members).
- [ ] Export Borrow History & Book inventory to Excel/PDF reports.
- [ ] Global Exception Handling (Custom 404, 500 error pages).
- [ ] Complete End-to-End Testing & Bug fixes.
