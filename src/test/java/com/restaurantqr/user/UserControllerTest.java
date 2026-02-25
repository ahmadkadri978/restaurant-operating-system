package com.restaurantqr.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ForbiddenOperationException;
import com.restaurantqr.exception.GlobalExceptionHandler;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.user.dto.CreateUserRequest;
import com.restaurantqr.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private static final String BASE_URL = "/api/v1/users";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UsernamePasswordAuthenticationToken managerAuth() {
        AuthPrincipal principal = new AuthPrincipal(10L, "MANAGER", 1L);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_MANAGER")));
    }

    private UsernamePasswordAuthenticationToken superAdminAuth() {
        AuthPrincipal principal = new AuthPrincipal(1L, "SUPER_ADMIN", null);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
    }

    // ───────────────────────────────────────────────────────
    //  MANAGER controller tests
    // ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("MANAGER via POST /api/v1/users")
    class ManagerTests {

        @Test
        @DisplayName("201 – manager creates STAFF successfully")
        void createStaff_returns201() throws Exception {
            CreateUserRequest request = new CreateUserRequest("newstaff", "secret123", Role.STAFF, null);
            UserResponse response = new UserResponse(99L, "newstaff", "STAFF", 1L, true, Instant.now());

            when(userService.createUser(any(CreateUserRequest.class), any(AuthPrincipal.class)))
                    .thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(99))
                    .andExpect(jsonPath("$.username").value("newstaff"))
                    .andExpect(jsonPath("$.role").value("STAFF"))
                    .andExpect(jsonPath("$.restaurantId").value(1))
                    .andExpect(jsonPath("$.active").value(true));

            verify(userService).createUser(any(CreateUserRequest.class), any(AuthPrincipal.class));
        }

        @Test
        @DisplayName("403 – manager tries to create MANAGER role")
        void createManager_returns403() throws Exception {
            CreateUserRequest request = new CreateUserRequest("mgr2", "secret123", Role.MANAGER, null);

            when(userService.createUser(any(CreateUserRequest.class), any(AuthPrincipal.class)))
                    .thenThrow(new ForbiddenOperationException("MANAGER cannot create users with role MANAGER"));

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("400 – username already taken")
        void usernameTaken_returns400() throws Exception {
            CreateUserRequest request = new CreateUserRequest("existing", "secret123", Role.STAFF, null);

            when(userService.createUser(any(CreateUserRequest.class), any(AuthPrincipal.class)))
                    .thenThrow(new BusinessRuleException("Username 'existing' is already taken"));

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value("Username 'existing' is already taken"));
        }

        @Test
        @DisplayName("409 – concurrent duplicate username")
        void concurrentDuplicate_returns409() throws Exception {
            CreateUserRequest request = new CreateUserRequest("raceuser", "secret123", Role.STAFF, null);

            when(userService.createUser(any(CreateUserRequest.class), any(AuthPrincipal.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_ENTRY"));
        }
    }

    // ───────────────────────────────────────────────────────
    //  SUPER_ADMIN controller tests
    // ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("SUPER_ADMIN via POST /api/v1/users")
    class SuperAdminTests {

        @Test
        @DisplayName("201 – super admin creates MANAGER for a restaurant")
        void createManager_returns201() throws Exception {
            CreateUserRequest request = new CreateUserRequest("mgr1", "password1", Role.MANAGER, 1L);
            UserResponse response = new UserResponse(101L, "mgr1", "MANAGER", 1L, true, Instant.now());

            when(userService.createUser(any(CreateUserRequest.class), any(AuthPrincipal.class)))
                    .thenReturn(response);

            mockMvc.perform(post(BASE_URL)
                            .principal(superAdminAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(101))
                    .andExpect(jsonPath("$.role").value("MANAGER"))
                    .andExpect(jsonPath("$.restaurantId").value(1));
        }

        @Test
        @DisplayName("400 – super admin omits restaurantId")
        void missingRestaurantId_returns400() throws Exception {
            CreateUserRequest request = new CreateUserRequest("mgr1", "password1", Role.MANAGER, null);

            when(userService.createUser(any(CreateUserRequest.class), any(AuthPrincipal.class)))
                    .thenThrow(new BusinessRuleException("SUPER_ADMIN must specify restaurantId"));

            mockMvc.perform(post(BASE_URL)
                            .principal(superAdminAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.message").value("SUPER_ADMIN must specify restaurantId"));
        }

        @Test
        @DisplayName("404 – restaurant not found")
        void restaurantNotFound_returns404() throws Exception {
            CreateUserRequest request = new CreateUserRequest("mgr1", "password1", Role.MANAGER, 999L);

            when(userService.createUser(any(CreateUserRequest.class), any(AuthPrincipal.class)))
                    .thenThrow(new ResourceNotFoundException("Restaurant with id 999 not found"));

            mockMvc.perform(post(BASE_URL)
                            .principal(superAdminAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("403 – super admin tries to create SUPER_ADMIN")
        void createSuperAdmin_returns403() throws Exception {
            CreateUserRequest request = new CreateUserRequest("admin2", "password1", Role.SUPER_ADMIN, 1L);

            when(userService.createUser(any(CreateUserRequest.class), any(AuthPrincipal.class)))
                    .thenThrow(new ForbiddenOperationException("SUPER_ADMIN cannot create users with role SUPER_ADMIN"));

            mockMvc.perform(post(BASE_URL)
                            .principal(superAdminAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // ───────────────────────────────────────────────────────
    //  Validation tests (role-agnostic)
    // ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("Request validation")
    class ValidationTests {

        @Test
        @DisplayName("400 – blank username")
        void blankUsername_returns400() throws Exception {
            CreateUserRequest request = new CreateUserRequest("", "secret123", Role.STAFF, null);

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verify(userService, never()).createUser(any(), any());
        }

        @Test
        @DisplayName("400 – username too short")
        void usernameTooShort_returns400() throws Exception {
            CreateUserRequest request = new CreateUserRequest("ab", "secret123", Role.STAFF, null);

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verify(userService, never()).createUser(any(), any());
        }

        @Test
        @DisplayName("400 – password too short")
        void passwordTooShort_returns400() throws Exception {
            CreateUserRequest request = new CreateUserRequest("newstaff", "12345", Role.STAFF, null);

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verify(userService, never()).createUser(any(), any());
        }

        @Test
        @DisplayName("400 – null password")
        void nullPassword_returns400() throws Exception {
            String json = """
                    { "username": "newstaff", "role": "STAFF" }
                    """;

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            verify(userService, never()).createUser(any(), any());
        }

        @Test
        @DisplayName("400 – null role")
        void nullRole_returns400() throws Exception {
            String json = """
                    { "username": "newstaff", "password": "secret123" }
                    """;

            mockMvc.perform(post(BASE_URL)
                            .principal(managerAuth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).createUser(any(), any());
        }
    }
}
