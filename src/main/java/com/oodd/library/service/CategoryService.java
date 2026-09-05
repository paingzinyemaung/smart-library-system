package com.oodd.library.service;

import com.oodd.library.model.Category;
import com.oodd.library.repository.BookRepository;
import com.oodd.library.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    public CategoryService(CategoryRepository categoryRepository, BookRepository bookRepository) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDropdownData() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("id", c.getId());
                    item.put("name", c.getName());
                    return item;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found with id: " + id));
    }

    @Transactional
    public Category createCategory(Category category) {
        String name = normalizeName(category.getName());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Category already exists: " + name);
        }
        Category entity = new Category(name, trimToNull(category.getDescription()));
        return categoryRepository.save(entity);
    }

    @Transactional
    public Category updateCategory(Long id, Category payload) {
        Category existing = getCategoryById(id);
        String name = normalizeName(payload.getName());
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Category already exists: " + name);
        }
        existing.setName(name);
        existing.setDescription(trimToNull(payload.getDescription()));
        return categoryRepository.save(existing);
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = getCategoryById(id);
        long inUse = bookRepository.countByCategory(category);
        if (inUse > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete category '" + category.getName() + "': " + inUse + " book(s) are assigned to it");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public long countCategories() {
        return categoryRepository.count();
    }

    @Transactional
    public Category findOrCreateByName(String name) {
        String clean = normalizeName(name);
        Optional<Category> existing = categoryRepository.findByNameIgnoreCase(clean);
        return existing.orElseGet(() -> categoryRepository.save(new Category(clean, null)));
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name is required");
        }
        return name.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
