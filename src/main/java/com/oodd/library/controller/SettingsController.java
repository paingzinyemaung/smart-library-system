package com.oodd.library.controller;

import com.oodd.library.model.User;
import com.oodd.library.service.SystemSettingsService;
import com.oodd.library.service.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class SettingsController {

    @Autowired
    private UserService userService;

    @Autowired
    private SystemSettingsService systemSettingsService;

    @GetMapping("/settings")
    public String showSettings(Principal principal, Model model) {
        User current = principal == null ? null
                : userService.findByEmail(principal.getName()).orElse(null);
        model.addAttribute("user", current);
        model.addAttribute("isAdmin", current != null && current.getRole() == User.UserRole.ADMIN);
        model.addAttribute("settings", systemSettingsService.getSettings());
        return "settings";
    }

    @PostMapping("/settings/update")
    public String updateSettings(@RequestParam("maxLoanDays") Integer maxLoanDays,
                                 @RequestParam("fineRate") Double fineRate,
                                 @RequestParam("maxBorrowLimit") Integer maxBorrowLimit) {
        if (maxLoanDays == null || maxLoanDays < 1 || maxLoanDays > 365
                || fineRate == null || fineRate < 0 || fineRate > 1000
                || maxBorrowLimit == null || maxBorrowLimit < 1 || maxBorrowLimit > 100) {
            return "redirect:/settings?error=invalid";
        }
        systemSettingsService.updateSettings(maxLoanDays, fineRate, maxBorrowLimit);
        return "redirect:/settings?success";
    }
}
