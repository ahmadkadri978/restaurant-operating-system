package com.restaurantqr.menu;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MenuService Unit Tests")
class MenuServiceTest {

    @Mock private RestaurantTableRepository tableRepository;
    @Mock private MenuCategoryRepository categoryRepository;
    @Mock private MenuItemRepository itemRepository;

    private MenuService menuService;

    private Restaurant testRestaurant;
    private RestaurantTable testTable;

    private static final String QR_TOKEN = "QR-TOKEN-1";
    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        menuService = new MenuService(tableRepository, categoryRepository, itemRepository);

        testRestaurant = new Restaurant();
        testRestaurant.setId(RESTAURANT_ID);
        testRestaurant.setName("Test Restaurant");
        testRestaurant.setActive(true);

        testTable = new RestaurantTable();
        testTable.setId(5L);
        testTable.setTableNumber(5);
        testTable.setRestaurant(testRestaurant);
        testTable.setActive(true);
    }

    // ==================== Validation Tests ====================

    @Nested
    @DisplayName("Input Validation")
    class ValidationTests {

        @Test
        @DisplayName("Should throw when qrToken is null")
        void shouldThrowWhenQrTokenNull() {
            assertThatThrownBy(() -> menuService.getMenuByQrToken(null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken");
        }

        @Test
        @DisplayName("Should throw when qrToken is blank")
        void shouldThrowWhenQrTokenBlank() {
            assertThatThrownBy(() -> menuService.getMenuByQrToken("  "))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken");
        }

        @Test
        @DisplayName("Should throw when table not found for qrToken")
        void shouldThrowWhenTableNotFound() {
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> menuService.getMenuByQrToken(QR_TOKEN))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("QR token");
        }

        @Test
        @DisplayName("Should throw when restaurant is null on table")
        void shouldThrowWhenRestaurantNull() {
            testTable.setRestaurant(null);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(testTable));

            assertThatThrownBy(() -> menuService.getMenuByQrToken(QR_TOKEN))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw when restaurant is inactive")
        void shouldThrowWhenRestaurantInactive() {
            testRestaurant.setActive(false);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(testTable));

            assertThatThrownBy(() -> menuService.getMenuByQrToken(QR_TOKEN))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Success Cases ====================

    @Nested
    @DisplayName("Successful Menu Retrieval")
    class SuccessTests {

        @BeforeEach
        void setUpSuccess() {
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(testTable));
        }

        @Test
        @DisplayName("Should return correct restaurant name and table number")
        void shouldReturnRestaurantNameAndTableNumber() {
            when(categoryRepository.findActiveByRestaurantOrderByDisplayOrder(RESTAURANT_ID)).thenReturn(List.of());
            when(itemRepository.findActiveMenuForRestaurantFetchCategory(RESTAURANT_ID)).thenReturn(List.of());

            MenuResponse response = menuService.getMenuByQrToken(QR_TOKEN);

            assertThat(response.restaurantName()).isEqualTo("Test Restaurant");
            assertThat(response.tableNumber()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should map categories correctly")
        void shouldMapCategories() {
            MenuCategory cat1 = new MenuCategory();
            cat1.setId(10L);
            cat1.setName("مشروبات");
            cat1.setDisplayOrder(1);

            MenuCategory cat2 = new MenuCategory();
            cat2.setId(20L);
            cat2.setName("وجبات");
            cat2.setDisplayOrder(2);

            when(categoryRepository.findActiveByRestaurantOrderByDisplayOrder(RESTAURANT_ID))
                    .thenReturn(List.of(cat1, cat2));
            when(itemRepository.findActiveMenuForRestaurantFetchCategory(RESTAURANT_ID)).thenReturn(List.of());

            MenuResponse response = menuService.getMenuByQrToken(QR_TOKEN);

            assertThat(response.categories()).hasSize(2);
            assertThat(response.categories().get(0).id()).isEqualTo(10L);
            assertThat(response.categories().get(0).name()).isEqualTo("مشروبات");
            assertThat(response.categories().get(0).displayOrder()).isEqualTo(1);
            assertThat(response.categories().get(1).id()).isEqualTo(20L);
        }

        @Test
        @DisplayName("Should map items with all fields correctly")
        void shouldMapItems() {
            MenuCategory cat = new MenuCategory();
            cat.setId(10L);

            MenuItem item = new MenuItem();
            item.setId(100L);
            item.setCategory(cat);
            item.setName("Cola");
            item.setDescription("Cold drink");
            item.setPrice(new BigDecimal("2500.00"));
            item.setAvailable(true);
            item.setActive(true);
            item.setDisplayOrder(1);
            item.setImageUrl("http://img.test/cola.jpg");
            item.setUpdatedAt(Instant.ofEpochMilli(1700000000000L));

            when(categoryRepository.findActiveByRestaurantOrderByDisplayOrder(RESTAURANT_ID)).thenReturn(List.of());
            when(itemRepository.findActiveMenuForRestaurantFetchCategory(RESTAURANT_ID))
                    .thenReturn(List.of(item));

            MenuResponse response = menuService.getMenuByQrToken(QR_TOKEN);

            assertThat(response.items()).hasSize(1);
            MenuItemDto dto = response.items().get(0);
            assertThat(dto.id()).isEqualTo(100L);
            assertThat(dto.categoryId()).isEqualTo(10L);
            assertThat(dto.name()).isEqualTo("Cola");
            assertThat(dto.description()).isEqualTo("Cold drink");
            assertThat(dto.price()).isEqualByComparingTo("2500.00");
            assertThat(dto.isAvailable()).isTrue();
            assertThat(dto.isActive()).isTrue();
            assertThat(dto.displayOrder()).isEqualTo(1);
            assertThat(dto.imageUrl()).isEqualTo("http://img.test/cola.jpg");
            assertThat(dto.updatedAt()).isEqualTo(1700000000000L);
        }

        @Test
        @DisplayName("Should return null updatedAt when MenuItem updatedAt is null")
        void shouldReturnNullUpdatedAtWhenNull() {
            MenuCategory cat = new MenuCategory();
            cat.setId(10L);

            MenuItem item = new MenuItem();
            item.setId(101L);
            item.setCategory(cat);
            item.setName("Water");
            item.setPrice(new BigDecimal("1000.00"));
            item.setAvailable(true);
            item.setActive(true);
            item.setUpdatedAt(null);

            when(categoryRepository.findActiveByRestaurantOrderByDisplayOrder(RESTAURANT_ID)).thenReturn(List.of());
            when(itemRepository.findActiveMenuForRestaurantFetchCategory(RESTAURANT_ID))
                    .thenReturn(List.of(item));

            MenuResponse response = menuService.getMenuByQrToken(QR_TOKEN);

            assertThat(response.items().get(0).updatedAt()).isNull();
        }

        @Test
        @DisplayName("Should return empty categories and items when none exist")
        void shouldReturnEmptyWhenNone() {
            when(categoryRepository.findActiveByRestaurantOrderByDisplayOrder(RESTAURANT_ID)).thenReturn(List.of());
            when(itemRepository.findActiveMenuForRestaurantFetchCategory(RESTAURANT_ID)).thenReturn(List.of());

            MenuResponse response = menuService.getMenuByQrToken(QR_TOKEN);

            assertThat(response.categories()).isEmpty();
            assertThat(response.items()).isEmpty();
        }
    }
}



