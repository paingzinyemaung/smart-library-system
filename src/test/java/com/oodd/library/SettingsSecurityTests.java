package com.oodd.library;

import com.oodd.library.config.SecurityConfig;
import com.oodd.library.controller.SettingsController;
import com.oodd.library.model.SystemSettings;
import com.oodd.library.repository.UserRepository;
import com.oodd.library.service.SystemSettingsService;
import com.oodd.library.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettingsController.class)
@Import(SecurityConfig.class)
class SettingsSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SystemSettingsService systemSettingsService;

    @Test
    @WithMockUser(username = "member@library.com", roles = "USER")
    void memberCannotViewSettings() throws Exception {
        mockMvc.perform(get("/settings").accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@library.com", roles = "USER")
    void memberCannotUpdateSettings() throws Exception {
        mockMvc.perform(post("/settings/update")
                .with(csrf())
                .param("maxLoanDays", "14")
                .param("fineRate", "1.00")
                .param("maxBorrowLimit", "5")
                .accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotViewSettings() throws Exception {
        mockMvc.perform(get("/settings").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin@library.com", roles = "ADMIN")
    void adminCanViewSettings() throws Exception {
        when(userService.findByEmail(anyString())).thenReturn(Optional.empty());
        when(systemSettingsService.getSettings()).thenReturn(new SystemSettings());
        mockMvc.perform(get("/settings").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@library.com", roles = "ADMIN")
    void adminCanUpdateSettings() throws Exception {
        mockMvc.perform(post("/settings/update")
                .with(csrf())
                .param("maxLoanDays", "14")
                .param("fineRate", "1.00")
                .param("maxBorrowLimit", "5"))
                .andExpect(status().is3xxRedirection());
    }
}
