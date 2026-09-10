package com.oodd.library.service;

import com.oodd.library.exception.ResourceNotFoundException;
import com.oodd.library.model.User;
import com.oodd.library.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    public User registerUser(User user) {
        // Check if user already exists
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already taken");
        }
        
        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
        // Set default role if not provided
        if (user.getRole() == null) {
            user.setRole(User.UserRole.USER);
        }
        
        // Save user
        return userRepository.save(user);
    }
    
	
	  public User authenticateUser(String email, String password) { return
	  userRepository.findActiveUserByEmail(email) .filter(user ->
	  passwordEncoder.matches(password, user.getPassword())) .orElseThrow(() -> new
	  RuntimeException("Invalid credentials")); }
	 
    
	    public User updateUserProfile(Long userId, User updatedUser) {
	        return userRepository.findById(userId)
	                .map(user -> {
	                    user.setFirstName(updatedUser.getFirstName());
	                    user.setLastName(updatedUser.getLastName());
	                    user.setUpdatedAt(LocalDateTime.now());
	                    return userRepository.save(user);
	                })
	                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
	    }
	    
	    public User updateProfileInfo(Long userId, String firstName, String lastName, String email) {
	        return userRepository.findById(userId)
	                .map(user -> {
	                    if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmail(email)) {
	                        throw new RuntimeException("Email already registered");
	                    }
	                    user.setFirstName(firstName);
	                    user.setLastName(lastName);
	                    user.setEmail(email);
	                    user.setUpdatedAt(LocalDateTime.now());
	                    return userRepository.save(user);
	                })
	                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
	    }
	    
	    public boolean existsByEmail(String email) {
	        return userRepository.existsByEmail(email);
	    }
	    
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
    
    public java.util.Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}