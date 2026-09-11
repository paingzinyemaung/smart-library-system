package com.oodd.library.controller;

import com.oodd.library.model.User;
import com.oodd.library.service.BorrowService;
import com.oodd.library.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private BorrowService borrowService;
    
    // MVC Endpoints for Thymeleaf pages
    
    @GetMapping("/login")
    public String showLoginPage(@RequestParam(value = "error", required = false) String error,
                               @RequestParam(value = "logout", required = false) String logout,
                               Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid username or password!");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }
        return "login";
    }
    
    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }
    
    @GetMapping("/profile")
    public String showProfilePage(java.security.Principal principal, Model model) {
        User user = principal == null ? null
                : userService.findByEmail(principal.getName()).orElse(null);
        borrowService.refreshOverdueRecords();
        model.addAttribute("user", user);
        model.addAttribute("isAdmin", user != null && user.getRole() == User.UserRole.ADMIN);
        model.addAttribute("borrowHistory", user == null
                ? java.util.List.of()
                : borrowService.getRecordsForUser(user.getId()));
        return "profile";
    }
    
    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam("firstName") String firstName,
                                @RequestParam("lastName") String lastName,
                                @RequestParam("email") String email,
                                java.security.Principal principal,
                                HttpServletRequest request) {
        User user = principal == null ? null
                : userService.findByEmail(principal.getName()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }
        
        String newEmail = email == null ? "" : email.trim();
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()
                || newEmail.isBlank() || !newEmail.contains("@")) {
            return "redirect:/profile?error=invalid";
        }
        
        User updated;
        try {
            updated = userService.updateProfileInfo(user.getId(), firstName.trim(), lastName.trim(), newEmail);
        } catch (RuntimeException e) {
            return "redirect:/profile?error=email";
        }
        
        refreshSecurityContext(updated, request);
        return "redirect:/profile?success";
    }
    
    private void refreshSecurityContext(User updated, HttpServletRequest request) {
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        if (currentAuth == null) {
            return;
        }
        
        Object credentials = currentAuth.getCredentials();
        Object details = currentAuth.getDetails();
        
        UsernamePasswordAuthenticationToken refreshed = new UsernamePasswordAuthenticationToken(
                new org.springframework.security.core.userdetails.User(
                        updated.getEmail(),
                        updated.getPassword() == null ? "" : updated.getPassword(),
                        updated.isEnabled(),
                        true, true, true,
                        currentAuth.getAuthorities()),
                credentials,
                currentAuth.getAuthorities());
        refreshed.setDetails(details);
        
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(refreshed);
        
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        }
    }
    
	/*
	 * @GetMapping("/dashboard") public String showDashboard() { return "dashboard";
	 * }
	 */
    
    // REST API Endpoints
    
    @PostMapping("/api/register")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerUser(@Valid @RequestBody User user, 
                                                          BindingResult result) {
        Map<String, Object> response = new HashMap<>();
        
        if (result.hasErrors()) {
            response.put("success", false);
            response.put("message", "Validation failed");
            response.put("errors", result.getFieldErrors());
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            User registeredUser = userService.registerUser(user);
            response.put("success", true);
            response.put("message", "User registered successfully");
            response.put("userId", registeredUser.getId());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
    }
    
    @PostMapping("/api/login")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> loginUser(@RequestBody Map<String, String> credentials) {
        Map<String, Object> response = new HashMap<>();
        
        String email = credentials.get("email");
        String password = credentials.get("password");
        
        try {
            User user = userService.authenticateUser(email, password);
            response.put("success", true);
            response.put("message", "Login successful");
            response.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole()
            ));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
    
    @PostMapping("/api/check-email")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
    
    @PostMapping("/api/check-username")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkUsername(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        boolean exists = userService.existsByUsername(username);
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}