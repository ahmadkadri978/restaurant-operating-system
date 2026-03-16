package com.restaurantqr.admin;

import com.restaurantqr.admin.dto.*;
import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRestaurantController Tests")
class AdminRestaurantControllerTest {

    @Mock
    private RestaurantSetupService restaurantSetupService;

    @InjectMocks
    private AdminRestaurantController controller;

    private Authentication adminAuth;
    private CreateRestaurantSetupRequest validRequest;
    private RestaurantSetupResponse mockResponse;

    @BeforeEach
    void setUp() {
        // Setup admin authentication
        AuthPrincipal adminPrincipal = new AuthPrincipal(1L, "ADMIN", null);
        adminAuth = new UsernamePasswordAuthenticationToken(
                adminPrincipal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        validRequest = createValidRequest();
        mockResponse = createMockResponse();
    }

    @Nested
    @DisplayName("Successful Request Tests")
    class SuccessfulRequestTests {

        @Test
        @DisplayName("Should return 201 CREATED on successful setup")
        void shouldReturn201OnSuccess() {
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(mockResponse);

            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(validRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().restaurantId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should pass request to service")
        void shouldPassRequestToService() {
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(mockResponse);

            controller.createRestaurantSetup(validRequest);

            verify(restaurantSetupService).createRestaurantSetup(validRequest);
        }

        @Test
        @DisplayName("Should return complete response body")
        void shouldReturnCompleteResponseBody() {
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(mockResponse);

            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(validRequest);

            RestaurantSetupResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.restaurantName()).isEqualTo("Bella Italia");
            assertThat(body.restaurantCode()).isEqualTo("BELLA-001");
            assertThat(body.createdCategories()).isEqualTo(2);
            assertThat(body.createdMenuItems()).isEqualTo(5);
            assertThat(body.createdTables()).hasSize(3);
            assertThat(body.createdUsers()).hasSize(3);
            
            // Verify tables have QR tokens
            assertThat(body.createdTables()).allSatisfy(table -> {
                assertThat(table.tableId()).isNotNull();
                assertThat(table.qrToken()).isNotBlank();
            });
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should propagate BusinessRuleException from service")
        void shouldPropagateBusinessRuleException() {
            when(restaurantSetupService.createRestaurantSetup(any()))
                    .thenThrow(new BusinessRuleException("Restaurant code already exists"));

            assertThatThrownBy(() -> controller.createRestaurantSetup(validRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Restaurant code already exists");
        }

        @Test
        @DisplayName("Should propagate RuntimeException from service")
        void shouldPropagateRuntimeException() {
            when(restaurantSetupService.createRestaurantSetup(any()))
                    .thenThrow(new RuntimeException("Database error"));

            assertThatThrownBy(() -> controller.createRestaurantSetup(validRequest))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database error");
        }
    }

    @Nested
    @DisplayName("Response Content Tests")
    class ResponseContentTests {

        @Test
        @DisplayName("Should include all created users in response")
        void shouldIncludeAllCreatedUsers() {
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(mockResponse);

            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(validRequest);

            List<CreatedUserResponse> users = response.getBody().createdUsers();
            assertThat(users).hasSize(3);
            assertThat(users)
                    .extracting(CreatedUserResponse::role)
                    .containsExactlyInAnyOrder("MANAGER", "STAFF", "KITCHEN");
        }

        @Test
        @DisplayName("Should not include passwords in response")
        void shouldNotIncludePasswordsInResponse() {
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(mockResponse);

            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(validRequest);

            // CreatedUserResponse record doesn't have password field - verify structure
            List<CreatedUserResponse> users = response.getBody().createdUsers();
            users.forEach(user -> {
                assertThat(user.userId()).isNotNull();
                assertThat(user.username()).isNotBlank();
                assertThat(user.role()).isNotBlank();
                // No password field exists - this is verified by compilation
            });
        }
    }

    // Helper methods
    private CreateRestaurantSetupRequest createValidRequest() {
        return new CreateRestaurantSetupRequest(
                "Bella Italia",
                "BELLA-001",
                List.of(
                        new CreateCategoryRequest("Pizzas", 1, List.of(
                                new CreateMenuItemRequest("Margherita", "Classic", new BigDecimal("12.50"), null, 1, true)
                        )),
                        new CreateCategoryRequest("Drinks", 2, List.of())
                ),
                List.of(
                        new CreateTableRequest(1, null),
                        new CreateTableRequest(2, "Window"),
                        new CreateTableRequest(3, null)
                ),
                new CreateUsersRequest(
                        null, // owner optional
                        new CreateUserRequest("bella_manager", "pass123"),
                        new CreateUserRequest("bella_staff", "pass123"),
                        new CreateUserRequest("bella_kitchen", "pass123")
                )
        );
    }

    private RestaurantSetupResponse createMockResponse() {
        return new RestaurantSetupResponse(
                1L,
                "Bella Italia",
                "BELLA-001",
                2,
                5,
                List.of(
                        new CreatedTableResponse(20L, 1, "BELLA-001-T1-550e8400-e29b-41d4-a716-446655440000"),
                        new CreatedTableResponse(21L, 2, "BELLA-001-T2-660e8500-e39b-51e4-b816-556655550111"),
                        new CreatedTableResponse(22L, 3, "BELLA-001-T3-770e8600-e49b-61f4-c916-666655660222")
                ),
                List.of(
                        new CreatedUserResponse(10L, "bella_manager", "MANAGER"),
                        new CreatedUserResponse(11L, "bella_staff", "STAFF"),
                        new CreatedUserResponse(12L, "bella_kitchen", "KITCHEN")
                ),
                Instant.now()
        );
    }
}




