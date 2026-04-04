package com.restaurantqr.table;

import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Multi-tenant isolation tests at the service layer.
 * Verifies that restaurant-scoped operations never leak data across restaurant boundaries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Multi-Tenant Isolation Tests")
class MultiTenantIsolationTest {

    @Mock private RestaurantTableRepository tableRepository;
    @Mock private TableSessionRepository sessionRepository;
    @Mock private com.restaurantqr.finance.BillCalculationService billCalculationService;
    @Mock private com.restaurantqr.finance.InvoiceService invoiceService;
    @Mock private com.restaurantqr.finance.SessionPaymentRepository paymentRepository;
    @Mock private com.restaurantqr.user.UserRepository userRepository;
    @Mock private com.restaurantqr.finance.CashRegisterSessionRepository cashRegisterRepository;

    private StaffTableSessionService service;
    private PublicSessionService publicService;

    private static final Long RESTAURANT_A = 1L;
    private static final Long RESTAURANT_B = 2L;

    @BeforeEach
    void setUp() {
        service = new StaffTableSessionService(
                tableRepository, sessionRepository,
                billCalculationService, invoiceService,
                paymentRepository, userRepository, cashRegisterRepository
        );
        publicService = new PublicSessionService(tableRepository);
    }

    // ==================== listTables Isolation ====================

    @Nested
    @DisplayName("listTables — Restaurant Isolation")
    class ListTablesIsolation {

        @Test
        @DisplayName("Restaurant A should only see its own tables")
        void restaurantAShouldOnlySeeOwnTables() {
            // Given
            RestaurantTable tableA1 = createTable(RESTAURANT_A, 1, "QR-A1");
            RestaurantTable tableA2 = createTable(RESTAURANT_A, 2, "QR-A2");
            when(tableRepository.findByRestaurantIdOrderByTableNumberAsc(RESTAURANT_A))
                    .thenReturn(List.of(tableA1, tableA2));

            // When
            List<StaffTableDto> result = service.listTables(RESTAURANT_A);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(StaffTableDto::qrToken)
                    .containsExactly("QR-A1", "QR-A2");

            verify(tableRepository).findByRestaurantIdOrderByTableNumberAsc(RESTAURANT_A);
            verify(tableRepository, never()).findByRestaurantIdOrderByTableNumberAsc(RESTAURANT_B);
        }

        @Test
        @DisplayName("Restaurant B should only see its own tables")
        void restaurantBShouldOnlySeeOwnTables() {
            // Given
            RestaurantTable tableB1 = createTable(RESTAURANT_B, 1, "QR-B1");
            when(tableRepository.findByRestaurantIdOrderByTableNumberAsc(RESTAURANT_B))
                    .thenReturn(List.of(tableB1));

            // When
            List<StaffTableDto> result = service.listTables(RESTAURANT_B);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).qrToken()).isEqualTo("QR-B1");
        }

        @Test
        @DisplayName("Should not return tables from another restaurant")
        void shouldNotReturnTablesFromAnotherRestaurant() {
            // Given
            when(tableRepository.findByRestaurantIdOrderByTableNumberAsc(RESTAURANT_A))
                    .thenReturn(List.of());

            // When
            List<StaffTableDto> result = service.listTables(RESTAURANT_A);

            // Then
            assertThat(result).isEmpty();
        }
    }

    // ==================== openSession Isolation ====================

    @Nested
    @DisplayName("openSession — Restaurant Isolation")
    class OpenSessionIsolation {

        @Test
        @DisplayName("Should reject openSession when table belongs to different restaurant")
        void shouldRejectOpenSessionForForeignTable() {
            // Given: table belongs to Restaurant B, but we try with Restaurant A
            RestaurantTable tableBelongingToB = createTable(RESTAURANT_B, 1, "QR-B1");
            setTableId(tableBelongingToB, 10L);
            when(tableRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(tableBelongingToB));

            // When & Then
            assertThatThrownBy(() -> service.openSession(RESTAURANT_A, 10L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }

        @Test
        @DisplayName("Should accept openSession for table from same restaurant")
        void shouldAcceptOpenSessionForOwnTable() {
            // Given
            RestaurantTable ownTable = createTable(RESTAURANT_A, 1, "QR-A1");
            setTableId(ownTable, 1L);
            // No active session
            ownTable.setSessionCode(null);
            when(tableRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(ownTable));
            when(tableRepository.existsByRestaurantIdAndSessionCode(eq(RESTAURANT_A), anyString()))
                    .thenReturn(false);

            // When
            OpenSessionResponse response = service.openSession(RESTAURANT_A, 1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.tableId()).isEqualTo(1L);
            assertThat(response.sessionCode()).matches("\\d{4}");
        }
    }

    // ==================== closeSession Isolation ====================

    @Nested
    @DisplayName("closeSession — Restaurant Isolation")
    class CloseSessionIsolation {

        @Test
        @DisplayName("Should reject closeSession when table belongs to different restaurant")
        void shouldRejectCloseSessionForForeignTable() {
            // Given: table belongs to Restaurant B
            RestaurantTable tableBelongingToB = createTable(RESTAURANT_B, 1, "QR-B1");
            setTableId(tableBelongingToB, 10L);
            when(tableRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(tableBelongingToB));

            // When & Then
            assertThatThrownBy(() -> service.closeSession(RESTAURANT_A, 10L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }
    }

    // ==================== verifySession Isolation ====================

    @Nested
    @DisplayName("verifySession — QR Token Validation")
    class VerifySessionIsolation {

        @Test
        @DisplayName("Should only verify sessions for active table with matching QR token")
        void shouldOnlyVerifyMatchingQrToken() {
            // Given
            RestaurantTable table = createTable(RESTAURANT_A, 1, "QR-A1");
            table.setSessionCode("1234");
            when(tableRepository.findActiveByQrTokenFetchRestaurant("QR-A1"))
                    .thenReturn(Optional.of(table));

            // When & Then: valid code should pass
            assertThatCode(() -> publicService.verifySessionOrThrow("QR-A1", "1234"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should throw for non-existent QR token")
        void shouldThrowForNonExistentQrToken() {
            when(tableRepository.findActiveByQrTokenFetchRestaurant("INVALID-TOKEN"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> publicService.verifySessionOrThrow("INVALID-TOKEN", "1234"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }

        @Test
        @DisplayName("Should reject when restaurant is inactive even with valid QR token")
        void shouldRejectWhenRestaurantIsInactive() {
            Restaurant inactiveRestaurant = new Restaurant();
            inactiveRestaurant.setId(RESTAURANT_A);
            inactiveRestaurant.setActive(false);

            RestaurantTable table = new RestaurantTable();
            table.setRestaurant(inactiveRestaurant);
            table.setActive(true);
            table.setSessionCode("1234");
            when(tableRepository.findActiveByQrTokenFetchRestaurant("QR-INACTIVE"))
                    .thenReturn(Optional.of(table));

            assertThatThrownBy(() -> publicService.verifySessionOrThrow("QR-INACTIVE", "1234"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }
    }

    // ==================== Helpers ====================

    private RestaurantTable createTable(Long restaurantId, int tableNumber, String qrToken) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("Restaurant " + restaurantId);
        restaurant.setActive(true);

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber(tableNumber);
        table.setQrToken(qrToken);
        table.setActive(true);
        return table;
    }

    private void setTableId(RestaurantTable table, Long id) {
        try {
            java.lang.reflect.Field idField = RestaurantTable.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(table, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}


