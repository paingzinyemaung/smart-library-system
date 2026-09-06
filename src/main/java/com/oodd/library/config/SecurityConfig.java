package com.oodd.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.oodd.library.repository.UserRepository;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
private final UserRepository userRepository;
    
    // Constructor injection သုံးပါ
    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)
                .map(user -> new org.springframework.security.core.userdetails.User(
                    user.getEmail(),
                    user.getPassword(),
                    user.isEnabled(),
                    true, true, true,
                    java.util.Collections.singletonList(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(
                            "ROLE_" + user.getRole().name()
                        )
                    )
                ))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
    
    /*@Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }*/
    
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(passwordEncoder());
        authProvider.setUserDetailsService(userDetailsService());
        return authProvider;
    }
    
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
         .authorizeHttpRequests(authz -> authz
                 .requestMatchers("/", "/register", "/api/register", "/login",
                    "/css/**", "/js/**", "/images/**").permitAll()
                 // RBAC: mutations are ADMIN only, ROLE_USER gets read-only access
                 .requestMatchers(HttpMethod.GET, "/api/books/**", "/api/books", "/api/categories/**", "/api/categories", "/api/resources", "/api/resources/**").authenticated()
                 // Digital resources: members can browse / read / download PDFs; upload & delete stay ADMIN only
                 .requestMatchers(HttpMethod.POST, "/api/resources/**").hasRole("ADMIN")
                 .requestMatchers(HttpMethod.DELETE, "/api/resources/**").hasRole("ADMIN")
                 // RBAC: issuing / returning / loan management is ADMIN only
                 .requestMatchers(HttpMethod.GET, "/api/borrow/**").hasRole("ADMIN")
                 .requestMatchers(HttpMethod.POST, "/api/books", "/api/books/**", "/api/categories", "/api/categories/**", "/api/upload-excel", "/api/borrow/**").hasRole("ADMIN")
                 .requestMatchers(HttpMethod.PUT, "/api/books/**", "/api/categories/**").hasRole("ADMIN")
                 .requestMatchers(HttpMethod.DELETE, "/api/books/**", "/api/categories/**").hasRole("ADMIN")
                 .requestMatchers(HttpMethod.GET, "/api/download-template").hasRole("ADMIN")
                 .requestMatchers("/dashboard", "/profile").authenticated()
                 .anyRequest().authenticated()
            ).formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/api/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            // Allow same-origin framing so the in-browser PDF reader <iframe> renders
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**") // Allow API calls without CSRF
            );
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}