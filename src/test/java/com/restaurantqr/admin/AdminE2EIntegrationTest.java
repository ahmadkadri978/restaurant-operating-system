package com.restaurantqr.admin;

import com.restaurantqr.admin.dto.*;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Integration Tests for Admin Package.
 * Tests complete restaurant setup flow through controller layer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Admin Package E2E Integration Tests")
class AdminE2EIntegrationTest {

    @Mock
    private RestaurantSetupService restaurantSetupService;

    @InjectMocks
    private AdminRestaurantController controller;

    @Captor
    private ArgumentCaptor<CreateRestaurantSetupRequest> createRequestCaptor;

    @Captor
    private ArgumentCaptor<UpdateRestaurantRequest> updateRequestCaptor;

    // ==================== Complete Restaurant Setup E2E Tests ====================

    @Nested
    @DisplayName("Complete Restaurant Setup E2E Tests")
    class CompleteSetupE2ETests {

        @Test
        @DisplayName("E2E: Should create complete restaurant with all entities")
        void shouldCreateCompleteRestaurantSetup() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            RestaurantSetupResponse mockResponse = createMockSetupResponse();

            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(mockResponse);

            // When
            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();

            RestaurantSetupResponse body = response.getBody();
            assertThat(body.restaurantId()).isEqualTo(1L);
            assertThat(body.restaurantName()).isEqualTo("Test Restaurant");
            assertThat(body.restaurantCode()).isEqualTo("TEST-001");
            assertThat(body.createdCategories()).isEqualTo(2);
            assertThat(body.createdMenuItems()).isEqualTo(5);
            assertThat(body.createdTables()).hasSize(3);
            assertThat(body.createdUsers()).hasSize(3);
        }

        @Test
        @DisplayName("E2E: Should pass complete request to service layer")
        void shouldPassCompleteRequestToService() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(createMockSetupResponse());

            // When
            controller.createRestaurantSetup(request);

            // Then
            verify(restaurantSetupService).createRestaurantSetup(createRequestCaptor.capture());
            CreateRestaurantSetupRequest captured = createRequestCaptor.getValue();

            assertThat(captured.restaurantName()).isEqualTo("Test Restaurant");
            assertThat(captured.restaurantCode()).isEqualTo("TEST-001");
            assertThat(captured.categories()).hasSize(2);
            assertThat(captured.tables()).hasSize(2);
            assertThat(captured.users()).isNotNull();
        }

        @Test
        @DisplayName("E2E: Should generate unique QR tokens for tables")
        void shouldGenerateUniqueQrTokensForTables() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            RestaurantSetupResponse mockResponse = createMockSetupResponse();
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(mockResponse);

            // When
            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(request);

            // Then
            List<CreatedTableResponse> tables = response.getBody().createdTables();
            assertThat(tables).hasSize(3);

            // All QR tokens should be unique
            Set<String> tokens = new HashSet<>();
            tables.forEach(t -> {
                assertThat(t.qrToken()).isNotBlank();
                assertThat(tokens.add(t.qrToken())).isTrue(); // Returns false if duplicate
            });
        }

        @Test
        @DisplayName("E2E: Should return all created users in response")
        void shouldReturnAllCreatedUsers() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(createMockSetupResponse());

            // When
            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(request);

            // Then
            List<CreatedUserResponse> users = response.getBody().createdUsers();
            assertThat(users).hasSize(3);
            assertThat(users)
                    .extracting(CreatedUserResponse::role)
                    .containsExactlyInAnyOrder("MANAGER", "STAFF", "KITCHEN");
        }

        @Test
        @DisplayName("E2E: Should include creation timestamp in response")
        void shouldIncludeCreationTimestamp() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(createMockSetupResponse());

            // When
            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(request);

            // Then
            assertThat(response.getBody().createdAt()).isNotNull();
            assertThat(response.getBody().createdAt()).isBefore(Instant.now().plusSeconds(1));
        }
    }

    // ==================== Validation E2E Tests ====================

    @Nested
    @DisplayName("Validation E2E Tests")
    class ValidationE2ETests {

        @Test
        @DisplayName("E2E: Should reject duplicate restaurant code")
        void shouldRejectDuplicateRestaurantCode() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            when(restaurantSetupService.createRestaurantSetup(any()))
                    .thenThrow(new BusinessRuleException("Restaurant code 'TEST-001' already exists"));

            // When & Then
            assertThatThrownBy(() -> controller.createRestaurantSetup(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("E2E: Should reject duplicate username")
        void shouldRejectDuplicateUsername() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            when(restaurantSetupService.createRestaurantSetup(any()))
                    .thenThrow(new BusinessRuleException("Username already exists: test_manager"));

            // When & Then
            assertThatThrownBy(() -> controller.createRestaurantSetup(request))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Username already exists");
        }

        @Test
        @DisplayName("E2E: Should propagate service layer exceptions")
        void shouldPropagateServiceExceptions() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            when(restaurantSetupService.createRestaurantSetup(any()))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            assertThatThrownBy(() -> controller.createRestaurantSetup(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database connection failed");
        }
    }

    // ==================== List Restaurants E2E Tests ====================

    @Nested
    @DisplayName("List Restaurants E2E Tests")
    class ListRestaurantsE2ETests {

        @Test
        @DisplayName("E2E: Should return paginated list of restaurants")
        void shouldReturnPaginatedList() {
            // Given
            RestaurantListResponse mockResponse = new RestaurantListResponse(
                    List.of(
                            new RestaurantListItemResponse(1L, "Restaurant A", "REST-A", true, 5, 20, 10, 3, Instant.now()),
                            new RestaurantListItemResponse(2L, "Restaurant B", "REST-B", true, 3, 15, 8, 2, Instant.now())
                    ),
                    2, 1, 0, 20
            );
            when(restaurantSetupService.listRestaurants(isNull(), eq(0), eq(20), eq(false)))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<RestaurantListResponse> response = controller.listRestaurants(null, 0, 20, false);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().content()).hasSize(2);
            assertThat(response.getBody().totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("E2E: Should search restaurants by name or code")
        void shouldSearchRestaurants() {
            // Given
            RestaurantListResponse mockResponse = new RestaurantListResponse(
                    List.of(new RestaurantListItemResponse(1L, "Pizza Palace", "PIZZA-001", true, 4, 25, 12, 3, Instant.now())),
                    1, 1, 0, 20
            );
            when(restaurantSetupService.listRestaurants(eq("pizza"), eq(0), eq(20), eq(false)))
                    .thenReturn(mockResponse);

            // When
            ResponseEntity<RestaurantListResponse> response = controller.listRestaurants("pizza", 0, 20, false);

            // Then
            assertThat(response.getBody().content()).hasSize(1);
            assertThat(response.getBody().content().get(0).restaurantName()).isEqualTo("Pizza Palace");
        }

        @Test
        @DisplayName("E2E: Should limit page size to maximum 100")
        void shouldLimitPageSize() {
            // Given
            when(restaurantSetupService.listRestaurants(any(), anyInt(), anyInt(), anyBoolean()))
                    .thenReturn(new RestaurantListResponse(List.of(), 0, 0, 0, 100));

            // When - request 500, should be limited to 100
            controller.listRestaurants(null, 0, 500, false);

            // Then - verify service was called with max 100
            verify(restaurantSetupService).listRestaurants(isNull(), eq(0), eq(100), eq(false));
        }

        @Test
        @DisplayName("E2E: Should filter by active only when requested")
        void shouldFilterByActiveOnly() {
            // Given
            when(restaurantSetupService.listRestaurants(any(), anyInt(), anyInt(), eq(true)))
                    .thenReturn(new RestaurantListResponse(List.of(), 0, 0, 0, 20));

            // When
            controller.listRestaurants(null, 0, 20, true);

            // Then
            verify(restaurantSetupService).listRestaurants(isNull(), eq(0), eq(20), eq(true));
        }
    }

    // ==================== Get Restaurant Details E2E Tests ====================

    @Nested
    @DisplayName("Get Restaurant Details E2E Tests")
    class GetRestaurantDetailsE2ETests {

        @Test
        @DisplayName("E2E: Should return complete restaurant details")
        void shouldReturnCompleteDetails() {
            // Given
            Long restaurantId = 1L;
            RestaurantDetailResponse mockResponse = createMockDetailResponse(restaurantId);
            when(restaurantSetupService.getRestaurantDetails(restaurantId)).thenReturn(mockResponse);

            // When
            ResponseEntity<RestaurantDetailResponse> response = controller.getRestaurantDetails(restaurantId);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().restaurantId()).isEqualTo(restaurantId);
            assertThat(response.getBody().restaurantName()).isEqualTo("Test Restaurant");
            assertThat(response.getBody().restaurantCode()).isEqualTo("TEST-001");
        }

        @Test
        @DisplayName("E2E: Should throw 404 when restaurant not found")
        void shouldThrow404WhenNotFound() {
            // Given
            when(restaurantSetupService.getRestaurantDetails(999L))
                    .thenThrow(new ResourceNotFoundException("Restaurant not found: 999"));

            // When & Then
            assertThatThrownBy(() -> controller.getRestaurantDetails(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("E2E: Should include categories in response")
        void shouldIncludeCategoriesInResponse() {
            // Given
            Long restaurantId = 1L;
            RestaurantDetailResponse mockResponse = createMockDetailResponse(restaurantId);
            when(restaurantSetupService.getRestaurantDetails(restaurantId)).thenReturn(mockResponse);

            // When
            ResponseEntity<RestaurantDetailResponse> response = controller.getRestaurantDetails(restaurantId);

            // Then
            assertThat(response.getBody().categories()).isNotNull();
            assertThat(response.getBody().categories()).hasSize(2);
        }
    }

    // ==================== Update Restaurant E2E Tests ====================

    @Nested
    @DisplayName("Update Restaurant E2E Tests")
    class UpdateRestaurantE2ETests {

        @Test
        @DisplayName("E2E: Should update restaurant basic info")
        void shouldUpdateRestaurantBasicInfo() {
            // Given
            Long restaurantId = 1L;
            UpdateRestaurantRequest request = new UpdateRestaurantRequest(
                    "New Name", "NEW-001", true, List.of(), List.of(), null, true
            );
            RestaurantDetailResponse mockResponse = new RestaurantDetailResponse(
                    restaurantId, "New Name", "NEW-001", true,
                    Instant.now(), Instant.now(), List.of(), List.of(), List.of()
            );
            when(restaurantSetupService.updateRestaurant(eq(restaurantId), any())).thenReturn(mockResponse);

            // When
            ResponseEntity<RestaurantDetailResponse> response = controller.updateRestaurant(restaurantId, request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().restaurantName()).isEqualTo("New Name");
            assertThat(response.getBody().restaurantCode()).isEqualTo("NEW-001");
        }

        @Test
        @DisplayName("E2E: Should pass update request to service")
        void shouldPassUpdateRequestToService() {
            // Given
            Long restaurantId = 1L;
            UpdateRestaurantRequest request = new UpdateRestaurantRequest(
                    "Updated Name", "UPD-001", true, List.of(), List.of(), null, false
            );
            when(restaurantSetupService.updateRestaurant(anyLong(), any()))
                    .thenReturn(createMockDetailResponse(restaurantId));

            // When
            controller.updateRestaurant(restaurantId, request);

            // Then
            verify(restaurantSetupService).updateRestaurant(eq(restaurantId), updateRequestCaptor.capture());
            UpdateRestaurantRequest captured = updateRequestCaptor.getValue();

            assertThat(captured.restaurantName()).isEqualTo("Updated Name");
            assertThat(captured.restaurantCode()).isEqualTo("UPD-001");
        }
    }

    // ==================== Delete Restaurant E2E Tests ====================

    @Nested
    @DisplayName("Delete Restaurant E2E Tests")
    class DeleteRestaurantE2ETests {

        @Test
        @DisplayName("E2E: Soft delete should deactivate restaurant")
        void softDeleteShouldDeactivateRestaurant() {
            // Given
            Long restaurantId = 1L;
            DeleteRestaurantResponse mockResponse = new DeleteRestaurantResponse(
                    "Restaurant TEST-001 successfully deactivated",
                    restaurantId, "TEST-001", false,
                    2, 5, 10, 3, Instant.now()
            );
            when(restaurantSetupService.deleteRestaurant(restaurantId, false)).thenReturn(mockResponse);

            // When
            ResponseEntity<DeleteRestaurantResponse> response = controller.deleteRestaurant(restaurantId, false);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().hardDelete()).isFalse();
            assertThat(response.getBody().message()).contains("deactivated");
        }

        @Test
        @DisplayName("E2E: Hard delete should permanently remove restaurant")
        void hardDeleteShouldRemoveRestaurant() {
            // Given
            Long restaurantId = 1L;
            DeleteRestaurantResponse mockResponse = new DeleteRestaurantResponse(
                    "Restaurant TEST-001 permanently deleted",
                    restaurantId, "TEST-001", true,
                    2, 5, 10, 3, Instant.now()
            );
            when(restaurantSetupService.deleteRestaurant(restaurantId, true)).thenReturn(mockResponse);

            // When
            ResponseEntity<DeleteRestaurantResponse> response = controller.deleteRestaurant(restaurantId, true);

            // Then
            assertThat(response.getBody().hardDelete()).isTrue();
            assertThat(response.getBody().deletedCategories()).isEqualTo(2);
            assertThat(response.getBody().deletedMenuItems()).isEqualTo(5);
            assertThat(response.getBody().deletedTables()).isEqualTo(10);
            assertThat(response.getBody().deletedUsers()).isEqualTo(3);
        }

        @Test
        @DisplayName("E2E: Should throw 404 when deleting non-existent restaurant")
        void shouldThrow404WhenDeletingNonExistent() {
            // Given
            when(restaurantSetupService.deleteRestaurant(999L, false))
                    .thenThrow(new ResourceNotFoundException("Restaurant not found: 999"));

            // When & Then
            assertThatThrownBy(() -> controller.deleteRestaurant(999L, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not found");
        }
    }

    // ==================== Response Content Verification Tests ====================

    @Nested
    @DisplayName("Response Content Verification Tests")
    class ResponseContentTests {

        @Test
        @DisplayName("E2E: Created tables should have table IDs and QR tokens")
        void createdTablesShouldHaveIdsAndQrTokens() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(createMockSetupResponse());

            // When
            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(request);

            // Then
            response.getBody().createdTables().forEach(table -> {
                assertThat(table.tableId()).isNotNull();
                assertThat(table.tableNumber()).isPositive();
                assertThat(table.qrToken()).isNotBlank();
            });
        }

        @Test
        @DisplayName("E2E: Created users should have usernames and roles but no passwords")
        void createdUsersShouldHaveUsernamesAndRoles() {
            // Given
            CreateRestaurantSetupRequest request = createFullSetupRequest();
            when(restaurantSetupService.createRestaurantSetup(any())).thenReturn(createMockSetupResponse());

            // When
            ResponseEntity<RestaurantSetupResponse> response = controller.createRestaurantSetup(request);

            // Then - verify structure (no password field)
            response.getBody().createdUsers().forEach(user -> {
                assertThat(user.userId()).isNotNull();
                assertThat(user.username()).isNotBlank();
                assertThat(user.role()).isNotBlank();
            });
        }
    }

    // ==================== Helper Methods ====================

    private CreateRestaurantSetupRequest createFullSetupRequest() {
        return new CreateRestaurantSetupRequest(
                "Test Restaurant",
                "TEST-001",
                List.of(
                        new CreateCategoryRequest("Main Dishes", 1, List.of(
                                new CreateMenuItemRequest("Burger", "Delicious burger", new BigDecimal("12.50"), null, 1, true),
                                new CreateMenuItemRequest("Pizza", "Italian pizza", new BigDecimal("15.00"), null, 2, true)
                        )),
                        new CreateCategoryRequest("Drinks", 2, List.of(
                                new CreateMenuItemRequest("Cola", "Refreshing cola", new BigDecimal("3.00"), null, 1, true)
                        ))
                ),
                List.of(
                        new CreateTableRequest(1, "Window"),
                        new CreateTableRequest(2, null)
                ),
                new CreateUsersRequest(
                        null,
                        new CreateUserRequest("test_manager", "pass123"),
                        new CreateUserRequest("test_staff", "pass123"),
                        new CreateUserRequest("test_kitchen", "pass123")
                )
        );
    }

    private RestaurantSetupResponse createMockSetupResponse() {
        return new RestaurantSetupResponse(
                1L,
                "Test Restaurant",
                "TEST-001",
                2,
                5,
                List.of(
                        new CreatedTableResponse(10L, 1, "TEST-001-T1-uuid1"),
                        new CreatedTableResponse(11L, 2, "TEST-001-T2-uuid2"),
                        new CreatedTableResponse(12L, 3, "TEST-001-T3-uuid3")
                ),
                List.of(
                        new CreatedUserResponse(20L, "test_manager", "MANAGER"),
                        new CreatedUserResponse(21L, "test_staff", "STAFF"),
                        new CreatedUserResponse(22L, "test_kitchen", "KITCHEN")
                ),
                Instant.now()
        );
    }

    private RestaurantDetailResponse createMockDetailResponse(Long restaurantId) {
        return new RestaurantDetailResponse(
                restaurantId,
                "Test Restaurant",
                "TEST-001",
                true,
                Instant.now().minusSeconds(3600),
                Instant.now(),
                List.of(
                        new CategoryDetailResponse(1L, "Main", 1, true, List.of()),
                        new CategoryDetailResponse(2L, "Drinks", 2, true, List.of())
                ),
                List.of(
                        new TableDetailResponse(1L, 1, "TEST-001-T1-uuid", "Window", true)
                ),
                List.of(
                        new UserDetailResponse(1L, "test_manager", "MANAGER", true)
                )
        );
    }
}


