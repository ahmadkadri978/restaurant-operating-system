package com.restaurantqr.admin;

import com.restaurantqr.admin.dto.*;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.menu.MenuCategory;
import com.restaurantqr.menu.MenuCategoryRepository;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RestaurantSetupService Tests")
class RestaurantSetupServiceImplTest {

    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private MenuCategoryRepository categoryRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private RestaurantTableRepository tableRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RestaurantSetupServiceImpl service;

    @Captor
    private ArgumentCaptor<Restaurant> restaurantCaptor;
    @Captor
    private ArgumentCaptor<MenuCategory> categoryCaptor;
    @Captor
    private ArgumentCaptor<MenuItem> menuItemCaptor;
    @Captor
    private ArgumentCaptor<RestaurantTable> tableCaptor;
    @Captor
    private ArgumentCaptor<User> userCaptor;

    private CreateRestaurantSetupRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = createValidRequest();

        // Default mock behavior
        when(restaurantRepository.existsByCode(anyString())).thenReturn(false);
        when(userRepository.existsByUsernameIn(any())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");

        // Mock save to return entities with IDs
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(categoryRepository.save(any(MenuCategory.class))).thenAnswer(inv -> {
            MenuCategory c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(menuItemRepository.save(any(MenuItem.class))).thenAnswer(inv -> {
            MenuItem i = inv.getArgument(0);
            i.setId(200L);
            return i;
        });
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(inv -> {
            RestaurantTable t = inv.getArgument(0);
            t.setId(300L);
            return t;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(400L + (long)(Math.random() * 100));
            return u;
        });
    }

    @Nested
    @DisplayName("Successful Creation Tests")
    class SuccessfulCreationTests {

        @Test
        @DisplayName("Should create restaurant with all components")
        void shouldCreateRestaurantWithAllComponents() {
            RestaurantSetupResponse response = service.createRestaurantSetup(validRequest);

            assertThat(response).isNotNull();
            assertThat(response.restaurantId()).isEqualTo(1L);
            assertThat(response.restaurantName()).isEqualTo("Test Restaurant");
            assertThat(response.restaurantCode()).isEqualTo("TEST-001");
            assertThat(response.createdCategories()).isEqualTo(1);
            assertThat(response.createdMenuItems()).isEqualTo(2);
            assertThat(response.createdTables()).hasSize(2);
            assertThat(response.createdUsers()).hasSize(3);
            assertThat(response.createdAt()).isNotNull();
        }

        @Test
        @DisplayName("Should create users with correct roles")
        void shouldCreateUsersWithCorrectRoles() {
            service.createRestaurantSetup(validRequest);

            verify(userRepository, times(3)).save(userCaptor.capture());
            List<User> savedUsers = userCaptor.getAllValues();

            assertThat(savedUsers)
                    .extracting(User::getRole)
                    .containsExactly(Role.MANAGER, Role.STAFF, Role.KITCHEN);
        }

        @Test
        @DisplayName("Should hash passwords")
        void shouldHashPasswords() {
            service.createRestaurantSetup(validRequest);

            verify(passwordEncoder, times(3)).encode(anyString());
            verify(userRepository, times(3)).save(userCaptor.capture());

            userCaptor.getAllValues().forEach(user ->
                    assertThat(user.getPasswordHash()).isEqualTo("encoded_password")
            );
        }

        @Test
        @DisplayName("Should generate QR tokens for tables")
        void shouldGenerateQrTokensForTables() {
            service.createRestaurantSetup(validRequest);

            verify(tableRepository, times(2)).save(tableCaptor.capture());
            List<RestaurantTable> tables = tableCaptor.getAllValues();

            assertThat(tables).allSatisfy(table -> {
                assertThat(table.getQrToken()).isNotBlank();
                assertThat(table.getQrToken()).contains("TEST-001");
                // Verify UUID format: CODE-T#-UUID (36 chars UUID)
                assertThat(table.getQrToken()).matches("TEST-001-T\\d+-[a-f0-9-]{36}");
            });
        }

        @Test
        @DisplayName("Should return tables with QR tokens in response")
        void shouldReturnTablesWithQrTokensInResponse() {
            RestaurantSetupResponse response = service.createRestaurantSetup(validRequest);

            assertThat(response.createdTables()).hasSize(2);
            assertThat(response.createdTables()).allSatisfy(table -> {
                assertThat(table.tableId()).isNotNull();
                assertThat(table.tableNumber()).isNotNull();
                assertThat(table.qrToken()).isNotBlank();
                assertThat(table.qrToken()).contains("TEST-001-T");
            });
        }

        @Test
        @DisplayName("Should create category with display order")
        void shouldCreateCategoryWithDisplayOrder() {
            service.createRestaurantSetup(validRequest);

            verify(categoryRepository).save(categoryCaptor.capture());
            MenuCategory category = categoryCaptor.getValue();

            assertThat(category.getName()).isEqualTo("Main Dishes");
            assertThat(category.getDisplayOrder()).isEqualTo(1);
            assertThat(category.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw exception when restaurant code exists")
        void shouldThrowExceptionWhenRestaurantCodeExists() {
            when(restaurantRepository.existsByCode("TEST-001")).thenReturn(true);

            assertThatThrownBy(() -> service.createRestaurantSetup(validRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Restaurant code already exists");
        }

        @Test
        @DisplayName("Should throw exception when username exists in database")
        void shouldThrowExceptionWhenUsernameExistsInDb() {
            when(userRepository.existsByUsernameIn(any())).thenReturn(true);
            when(userRepository.existsByUsername("test_manager")).thenReturn(true);

            assertThatThrownBy(() -> service.createRestaurantSetup(validRequest))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Username already exists: test_manager");
        }

        @Test
        @DisplayName("Should throw exception when table numbers are duplicated in request")
        void shouldThrowExceptionWhenTableNumbersDuplicated() {
            CreateRestaurantSetupRequest requestWithDuplicateTables = new CreateRestaurantSetupRequest(
                    "Test",
                    "TEST-002",
                    List.of(createCategory("Cat1", List.of())),
                    List.of(new CreateTableRequest(1, null), new CreateTableRequest(1, null)),
                    createUsersRequest()
            );

            assertThatThrownBy(() -> service.createRestaurantSetup(requestWithDuplicateTables))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Duplicate table number");
        }

        @Test
        @DisplayName("Should throw exception when usernames duplicated in request")
        void shouldThrowExceptionWhenUsernamesDuplicatedInRequest() {
            CreateRestaurantSetupRequest requestWithDuplicateUsers = new CreateRestaurantSetupRequest(
                    "Test",
                    "TEST-003",
                    List.of(createCategory("Cat1", List.of())),
                    List.of(new CreateTableRequest(1, null)),
                    new CreateUsersRequest(
                            null,
                            new CreateUserRequest("same_user", "pass123"),
                            new CreateUserRequest("same_user", "pass456"),
                            new CreateUserRequest("kitchen_user", "pass789")
                    )
            );

            assertThatThrownBy(() -> service.createRestaurantSetup(requestWithDuplicateUsers))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Duplicate username");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle category without items")
        void shouldHandleCategoryWithoutItems() {
            CreateRestaurantSetupRequest requestWithEmptyCategory = new CreateRestaurantSetupRequest(
                    "Test Restaurant",
                    "TEST-004",
                    List.of(createCategory("Empty Category", List.of())),
                    List.of(new CreateTableRequest(1, null)),
                    createUsersRequest()
            );

            RestaurantSetupResponse response = service.createRestaurantSetup(requestWithEmptyCategory);

            assertThat(response.createdCategories()).isEqualTo(1);
            assertThat(response.createdMenuItems()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should lowercase usernames")
        void shouldLowercaseUsernames() {
            CreateRestaurantSetupRequest requestWithUpperCase = new CreateRestaurantSetupRequest(
                    "Test",
                    "TEST-005",
                    List.of(createCategory("Cat", List.of())),
                    List.of(new CreateTableRequest(1, null)),
                    new CreateUsersRequest(
                            null,
                            new CreateUserRequest("Manager_User", "pass"),
                            new CreateUserRequest("Staff_User", "pass"),
                            new CreateUserRequest("Kitchen_User", "pass")
                    )
            );

            service.createRestaurantSetup(requestWithUpperCase);

            verify(userRepository, times(3)).save(userCaptor.capture());
            List<User> users = userCaptor.getAllValues();

            assertThat(users)
                    .extracting(User::getUsername)
                    .containsExactly("manager_user", "staff_user", "kitchen_user");
        }
    }

    // Helper methods
    private CreateRestaurantSetupRequest createValidRequest() {
        return new CreateRestaurantSetupRequest(
                "Test Restaurant",
                "TEST-001",
                List.of(createCategory("Main Dishes", List.of(
                        new CreateMenuItemRequest("Pizza", "Delicious", new BigDecimal("12.50"), null, 1, null),
                        new CreateMenuItemRequest("Pasta", "Tasty", new BigDecimal("10.00"), null, 2, true)
                ))),
                List.of(new CreateTableRequest(1, null), new CreateTableRequest(2, "Window")),
                createUsersRequest()
        );
    }

    private CreateCategoryRequest createCategory(String name, List<CreateMenuItemRequest> items) {
        return new CreateCategoryRequest(name, 1, items);
    }

    private CreateUsersRequest createUsersRequest() {
        return new CreateUsersRequest(
                null, // owner is optional
                new CreateUserRequest("test_manager", "password123"),
                new CreateUserRequest("test_staff", "password123"),
                new CreateUserRequest("test_kitchen", "password123")
        );
    }
}









