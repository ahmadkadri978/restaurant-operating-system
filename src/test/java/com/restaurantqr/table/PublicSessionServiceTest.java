package com.restaurantqr.table;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.InvalidSessionCodeException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicSessionService Unit Tests")
class PublicSessionServiceTest {

    @Mock private RestaurantTableRepository tableRepository;

    private PublicSessionService service;

    private static final String QR_TOKEN = "QR-TOKEN-1";
    private static final String CODE = "1234";

    @BeforeEach
    void setUp() {
        service = new PublicSessionService(tableRepository);
    }

    // ==================== Input Validation ====================

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("Should throw BusinessRuleException when qrToken is null")
        void shouldThrowWhenQrTokenNull() {
            assertThatThrownBy(() -> service.verifySessionOrThrow(null, CODE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken is required");
        }

        @Test
        @DisplayName("Should throw BusinessRuleException when qrToken is blank")
        void shouldThrowWhenQrTokenBlank() {
            assertThatThrownBy(() -> service.verifySessionOrThrow("   ", CODE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("qrToken is required");
        }

        @Test
        @DisplayName("Should throw InvalidSessionCodeException when code is null")
        void shouldThrowWhenCodeNull() {
            assertThatThrownBy(() -> service.verifySessionOrThrow(QR_TOKEN, null))
                    .isInstanceOf(InvalidSessionCodeException.class)
                    .hasMessageContaining("Invalid session code");
        }

        @Test
        @DisplayName("Should throw InvalidSessionCodeException when code is blank")
        void shouldThrowWhenCodeBlank() {
            assertThatThrownBy(() -> service.verifySessionOrThrow(QR_TOKEN, ""))
                    .isInstanceOf(InvalidSessionCodeException.class)
                    .hasMessageContaining("Invalid session code");
        }
    }

    // ==================== Table Lookup ====================

    @Nested
    @DisplayName("Table Lookup")
    class TableLookupTests {

        @Test
        @DisplayName("Should throw ResourceNotFoundException when table not found")
        void shouldThrowWhenTableNotFound() {
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifySessionOrThrow(QR_TOKEN, CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }

        @Test
        @DisplayName("Should throw when table is inactive")
        void shouldThrowWhenTableInactive() {
            RestaurantTable table = buildTable(false, true, CODE);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(table));

            assertThatThrownBy(() -> service.verifySessionOrThrow(QR_TOKEN, CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }

        @Test
        @DisplayName("Should throw when restaurant is null")
        void shouldThrowWhenRestaurantNull() {
            RestaurantTable table = buildTable(true, true, CODE);
            table.setRestaurant(null);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(table));

            assertThatThrownBy(() -> service.verifySessionOrThrow(QR_TOKEN, CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }

        @Test
        @DisplayName("Should throw when restaurant is inactive")
        void shouldThrowWhenRestaurantInactive() {
            RestaurantTable table = buildTable(true, false, CODE);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(table));

            assertThatThrownBy(() -> service.verifySessionOrThrow(QR_TOKEN, CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }
    }

    // ==================== Session Code Verification ====================

    @Nested
    @DisplayName("Session Code Verification")
    class SessionCodeTests {

        @Test
        @DisplayName("Should throw when no session is open (sessionCode is null)")
        void shouldThrowWhenNoSessionOpen() {
            RestaurantTable table = buildTable(true, true, null);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(table));

            assertThatThrownBy(() -> service.verifySessionOrThrow(QR_TOKEN, CODE))
                    .isInstanceOf(InvalidSessionCodeException.class)
                    .hasMessageContaining("Session is not open");
        }

        @Test
        @DisplayName("Should throw when code does not match")
        void shouldThrowWhenCodeMismatch() {
            RestaurantTable table = buildTable(true, true, "5678");
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(table));

            assertThatThrownBy(() -> service.verifySessionOrThrow(QR_TOKEN, "9999"))
                    .isInstanceOf(InvalidSessionCodeException.class)
                    .hasMessageContaining("Invalid session code");
        }

        @Test
        @DisplayName("Should succeed when code matches")
        void shouldSucceedWhenCodeMatches() {
            RestaurantTable table = buildTable(true, true, CODE);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(table));

            // Should not throw
            assertThatCode(() -> service.verifySessionOrThrow(QR_TOKEN, CODE))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== Helpers ====================

    private RestaurantTable buildTable(boolean tableActive, boolean restaurantActive, String sessionCode) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setActive(restaurantActive);

        RestaurantTable table = new RestaurantTable();
        table.setId(5L);
        table.setTableNumber(1);
        table.setQrToken(QR_TOKEN);
        table.setActive(tableActive);
        table.setRestaurant(restaurant);
        table.setSessionCode(sessionCode);

        return table;
    }
}

