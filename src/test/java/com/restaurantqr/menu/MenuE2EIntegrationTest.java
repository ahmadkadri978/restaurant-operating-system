package com.restaurantqr.menu;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.table.PublicSessionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-End Integration Tests for Menu Package.
 * Tests complete menu retrieval flow through controller layer.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Menu Package E2E Integration Tests")
class MenuE2EIntegrationTest {

    private MenuController controller;

    @Mock
    private MenuService menuService;

    @Mock
    private PublicSessionService publicSessionService;

    @BeforeEach
    void setUp() {
        controller = new MenuController(menuService, publicSessionService);
    }

    // ==================== Get Menu E2E Tests ====================

    @Nested
    @DisplayName("Get Menu E2E Tests")
    class GetMenuE2ETests {

        @Test
        @DisplayName("E2E: Should return complete menu for valid QR token")
        void shouldReturnCompleteMenuForValidQrToken() {
            // Given
            String qrToken = "valid-qr-token";
            String sessionCode = "1234";
            MenuResponse mockMenu = createMockMenuResponse();

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, sessionCode);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(mockMenu);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, sessionCode);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().restaurantName()).isEqualTo("Test Restaurant");
            assertThat(response.getBody().tableNumber()).isEqualTo(5);
            assertThat(response.getBody().categories()).hasSize(2);
            assertThat(response.getBody().items()).hasSize(5);
        }

        @Test
        @DisplayName("E2E: Should verify session before returning menu")
        void shouldVerifySessionBeforeReturningMenu() {
            // Given
            String qrToken = "valid-qr-token";
            String sessionCode = "5678";

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, sessionCode);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(createMockMenuResponse());

            // When
            controller.getMenu(qrToken, sessionCode);

            // Then - verify session was checked first
            InOrder inOrder = inOrder(publicSessionService, menuService);
            inOrder.verify(publicSessionService).verifySessionOrThrow(qrToken, sessionCode);
            inOrder.verify(menuService).getMenuByQrToken(qrToken);
        }

        @Test
        @DisplayName("E2E: Should throw exception for invalid session code")
        void shouldThrowExceptionForInvalidSessionCode() {
            // Given
            String qrToken = "valid-qr-token";
            String invalidCode = "9999";

            doThrow(new BusinessRuleException("Invalid session code"))
                    .when(publicSessionService).verifySessionOrThrow(qrToken, invalidCode);

            // When & Then
            assertThatThrownBy(() -> controller.getMenu(qrToken, invalidCode))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid session code");

            // Menu service should not be called
            verify(menuService, never()).getMenuByQrToken(any());
        }

        @Test
        @DisplayName("E2E: Should throw 404 for invalid QR token")
        void shouldThrow404ForInvalidQrToken() {
            // Given
            String invalidQrToken = "invalid-qr";
            String code = "1234";

            doThrow(new ResourceNotFoundException("Invalid or inactive QR token"))
                    .when(publicSessionService).verifySessionOrThrow(invalidQrToken, code);

            // When & Then
            assertThatThrownBy(() -> controller.getMenu(invalidQrToken, code))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Invalid or inactive QR token");
        }

        @Test
        @DisplayName("E2E: Should throw 404 for inactive restaurant")
        void shouldThrow404ForInactiveRestaurant() {
            // Given
            String qrToken = "inactive-restaurant-qr";
            String code = "1234";

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken))
                    .thenThrow(new ResourceNotFoundException("Invalid or inactive QR token"));

            // When & Then
            assertThatThrownBy(() -> controller.getMenu(qrToken, code))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Menu Content E2E Tests ====================

    @Nested
    @DisplayName("Menu Content E2E Tests")
    class MenuContentE2ETests {

        @Test
        @DisplayName("E2E: Should return categories sorted by display order")
        void shouldReturnCategoriesSortedByDisplayOrder() {
            // Given
            String qrToken = "valid-qr-token";
            String code = "1234";

            List<MenuCategoryDto> categories = List.of(
                    new MenuCategoryDto(1L, "Main Dishes", 1),
                    new MenuCategoryDto(2L, "Appetizers", 2),
                    new MenuCategoryDto(3L, "Desserts", 3)
            );

            MenuResponse mockMenu = new MenuResponse(
                    "Test Restaurant",
                    5,
                    categories,
                    List.of()
            );

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(mockMenu);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, code);

            // Then
            List<MenuCategoryDto> resultCategories = response.getBody().categories();
            assertThat(resultCategories).hasSize(3);
            assertThat(resultCategories.get(0).displayOrder()).isEqualTo(1);
            assertThat(resultCategories.get(1).displayOrder()).isEqualTo(2);
            assertThat(resultCategories.get(2).displayOrder()).isEqualTo(3);
        }

        @Test
        @DisplayName("E2E: Should return only available items")
        void shouldReturnOnlyAvailableItems() {
            // Given
            String qrToken = "valid-qr-token";
            String code = "1234";

            List<MenuItemDto> items = List.of(
                    new MenuItemDto(1L, 1L, "Burger", "Delicious", new BigDecimal("12.50"), true, 1, null),
                    new MenuItemDto(2L, 1L, "Pizza", "Italian", new BigDecimal("15.00"), true, 2, null)
            );

            MenuResponse mockMenu = new MenuResponse(
                    "Test Restaurant",
                    5,
                    List.of(new MenuCategoryDto(1L, "Main", 1)),
                    items
            );

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(mockMenu);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, code);

            // Then - all items should be available
            response.getBody().items().forEach(item -> {
                assertThat(item.isAvailable()).isTrue();
            });
        }

        @Test
        @DisplayName("E2E: Should include item details (price, description, image)")
        void shouldIncludeItemDetails() {
            // Given
            String qrToken = "valid-qr-token";
            String code = "1234";

            MenuItemDto itemWithDetails = new MenuItemDto(
                    1L, 1L, "Premium Burger",
                    "Juicy beef patty with special sauce",
                    new BigDecimal("18.99"),
                    true,
                    1,
                    "/uploads/menu/burger.jpg"
            );

            MenuResponse mockMenu = new MenuResponse(
                    "Test Restaurant",
                    5,
                    List.of(new MenuCategoryDto(1L, "Main", 1)),
                    List.of(itemWithDetails)
            );

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(mockMenu);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, code);

            // Then
            MenuItemDto resultItem = response.getBody().items().get(0);
            assertThat(resultItem.name()).isEqualTo("Premium Burger");
            assertThat(resultItem.description()).isEqualTo("Juicy beef patty with special sauce");
            assertThat(resultItem.price()).isEqualByComparingTo(new BigDecimal("18.99"));
            assertThat(resultItem.imageUrl()).isEqualTo("/uploads/menu/burger.jpg");
        }

        @Test
        @DisplayName("E2E: Should return correct table number")
        void shouldReturnCorrectTableNumber() {
            // Given
            String qrToken = "table-7-qr-token";
            String code = "1234";

            MenuResponse mockMenu = new MenuResponse(
                    "Test Restaurant",
                    7,
                    List.of(),
                    List.of()
            );

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(mockMenu);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, code);

            // Then
            assertThat(response.getBody().tableNumber()).isEqualTo(7);
        }
    }

    // ==================== Empty Menu E2E Tests ====================

    @Nested
    @DisplayName("Empty Menu E2E Tests")
    class EmptyMenuE2ETests {

        @Test
        @DisplayName("E2E: Should handle restaurant with no categories")
        void shouldHandleNoCategories() {
            // Given
            String qrToken = "empty-menu-qr";
            String code = "1234";

            MenuResponse emptyMenu = new MenuResponse(
                    "New Restaurant",
                    1,
                    List.of(),
                    List.of()
            );

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(emptyMenu);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, code);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().categories()).isEmpty();
            assertThat(response.getBody().items()).isEmpty();
        }

        @Test
        @DisplayName("E2E: Should handle category with no items")
        void shouldHandleCategoryWithNoItems() {
            // Given
            String qrToken = "no-items-qr";
            String code = "1234";

            MenuResponse menuWithEmptyCategory = new MenuResponse(
                    "Restaurant",
                    1,
                    List.of(new MenuCategoryDto(1L, "Coming Soon", 1)),
                    List.of()
            );

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(menuWithEmptyCategory);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, code);

            // Then
            assertThat(response.getBody().categories()).hasSize(1);
            assertThat(response.getBody().items()).isEmpty();
        }
    }

    // ==================== Multi-Category Menu E2E Tests ====================

    @Nested
    @DisplayName("Multi-Category Menu E2E Tests")
    class MultiCategoryMenuE2ETests {

        @Test
        @DisplayName("E2E: Should return items with correct category IDs")
        void shouldReturnItemsWithCorrectCategoryIds() {
            // Given
            String qrToken = "multi-cat-qr";
            String code = "1234";

            List<MenuCategoryDto> categories = List.of(
                    new MenuCategoryDto(10L, "Starters", 1),
                    new MenuCategoryDto(20L, "Main Course", 2),
                    new MenuCategoryDto(30L, "Desserts", 3)
            );

            List<MenuItemDto> items = List.of(
                    new MenuItemDto(1L, 10L, "Soup", "Hot soup", new BigDecimal("5.00"), true, 1, null),
                    new MenuItemDto(2L, 20L, "Steak", "Beef steak", new BigDecimal("25.00"), true, 1, null),
                    new MenuItemDto(3L, 20L, "Fish", "Fresh fish", new BigDecimal("22.00"), true, 2, null),
                    new MenuItemDto(4L, 30L, "Cake", "Chocolate", new BigDecimal("8.00"), true, 1, null)
            );

            MenuResponse mockMenu = new MenuResponse("Restaurant", 1, categories, items);

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(mockMenu);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, code);

            // Then - verify items are linked to correct categories
            Map<Long, List<MenuItemDto>> itemsByCategory = new HashMap<>();
            response.getBody().items().forEach(item -> {
                itemsByCategory.computeIfAbsent(item.categoryId(), k -> new ArrayList<>()).add(item);
            });

            assertThat(itemsByCategory.get(10L)).hasSize(1); // Starters
            assertThat(itemsByCategory.get(20L)).hasSize(2); // Main Course
            assertThat(itemsByCategory.get(30L)).hasSize(1); // Desserts
        }
    }

    // ==================== Price Validation E2E Tests ====================

    @Nested
    @DisplayName("Price Validation E2E Tests")
    class PriceValidationE2ETests {

        @Test
        @DisplayName("E2E: Should return prices with correct decimal precision")
        void shouldReturnPricesWithCorrectPrecision() {
            // Given
            String qrToken = "price-test-qr";
            String code = "1234";

            List<MenuItemDto> items = List.of(
                    new MenuItemDto(1L, 1L, "Item 1", null, new BigDecimal("10.50"), true, 1, null),
                    new MenuItemDto(2L, 1L, "Item 2", null, new BigDecimal("99.99"), true, 2, null),
                    new MenuItemDto(3L, 1L, "Item 3", null, new BigDecimal("5.00"), true, 3, null)
            );

            MenuResponse mockMenu = new MenuResponse(
                    "Restaurant",
                    1,
                    List.of(new MenuCategoryDto(1L, "Items", 1)),
                    items
            );

            doNothing().when(publicSessionService).verifySessionOrThrow(qrToken, code);
            when(menuService.getMenuByQrToken(qrToken)).thenReturn(mockMenu);

            // When
            ResponseEntity<MenuResponse> response = controller.getMenu(qrToken, code);

            // Then
            assertThat(response.getBody().items().get(0).price())
                    .isEqualByComparingTo(new BigDecimal("10.50"));
            assertThat(response.getBody().items().get(1).price())
                    .isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(response.getBody().items().get(2).price())
                    .isEqualByComparingTo(new BigDecimal("5.00"));
        }
    }

    // ==================== Helper Methods ====================

    private MenuResponse createMockMenuResponse() {
        List<MenuCategoryDto> categories = List.of(
                new MenuCategoryDto(1L, "Main Dishes", 1),
                new MenuCategoryDto(2L, "Drinks", 2)
        );

        List<MenuItemDto> items = List.of(
                new MenuItemDto(1L, 1L, "Burger", "Beef burger", new BigDecimal("12.50"), true, 1, null),
                new MenuItemDto(2L, 1L, "Pizza", "Margherita", new BigDecimal("15.00"), true, 2, null),
                new MenuItemDto(3L, 1L, "Pasta", "Carbonara", new BigDecimal("14.00"), true, 3, null),
                new MenuItemDto(4L, 2L, "Cola", "Soft drink", new BigDecimal("3.00"), true, 1, null),
                new MenuItemDto(5L, 2L, "Water", "Mineral", new BigDecimal("2.00"), true, 2, null)
        );

        return new MenuResponse("Test Restaurant", 5, categories, items);
    }
}

