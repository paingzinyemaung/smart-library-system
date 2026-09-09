package com.oodd.library.controller;

import com.oodd.library.repository.BookRepository;
import com.oodd.library.repository.CategoryRepository;
import com.oodd.library.repository.DigitalResourceRepository;
import com.oodd.library.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private DigitalResourceRepository digitalResourceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/")
    public String showLandingPage(Model model) {
        model.addAttribute("totalBooks", bookRepository.count());
        model.addAttribute("totalResources", digitalResourceRepository.count());
        model.addAttribute("totalCategories", categoryRepository.count());
        model.addAttribute("totalMembers", userRepository.count());
        return "index";
    }
}
