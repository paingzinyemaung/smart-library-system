package com.oodd.library.controller;

import com.oodd.library.model.User;
import com.oodd.library.repository.UserRepository;
import com.oodd.library.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/admin/users")
    public String listUsers(Principal principal, Model model) {
        User current = principal == null ? null
                : userService.findByEmail(principal.getName()).orElse(null);
        model.addAttribute("user", current);
        model.addAttribute("isAdmin", current != null && current.getRole() == User.UserRole.ADMIN);
        model.addAttribute("users", userRepository.findAll());
        return "admin-users";
    }

    @PostMapping("/admin/users/create")
    public String createUser(@RequestParam("firstName") String firstName,
                             @RequestParam("lastName") String lastName,
                             @RequestParam("email") String email,
                             @RequestParam("password") String password,
                             @RequestParam("role") String role) {
        String cleanFirst = firstName == null ? "" : firstName.trim();
        String cleanLast = lastName == null ? "" : lastName.trim();
        String cleanEmail = email == null ? "" : email.trim();

        if (cleanFirst.isBlank() || cleanLast.isBlank()
                || cleanEmail.isBlank() || !cleanEmail.contains("@")
                || password == null || password.length() < 6) {
            return "redirect:/admin/users?error=invalid";
        }

        try {
            if (userRepository.existsByEmail(cleanEmail)) {
                return "redirect:/admin/users?error=duplicate";
            }
            User user = new User();
            user.setFirstName(cleanFirst);
            user.setLastName(cleanLast);
            user.setEmail(cleanEmail);
            user.setUsername(generateUsername(cleanEmail));
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(mapRole(role));
            user.setEnabled(true);
            userRepository.save(user);
        } catch (RuntimeException e) {
            return "redirect:/admin/users?error=duplicate";
        }
        return "redirect:/admin/users?success";
    }

    // The form posts ROLE_ADMIN / ROLE_MEMBER; ROLE_MEMBER maps to the existing USER enum value
    private User.UserRole mapRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase().replace("ROLE_", "");
        return "ADMIN".equals(normalized) ? User.UserRole.ADMIN : User.UserRole.USER;
    }

    private String generateUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9._-]", "");
        while (base.length() < 3) {
            base = base.isEmpty() ? "user" : base + "user";
        }
        String username = base;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + (++suffix);
        }
        return username;
    }
}
