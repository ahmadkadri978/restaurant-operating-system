package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.SessionBillDto;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BillController Unit Tests")
class BillControllerTest {

    @Mock private BillCalculationService billCalculationService;
    @Mock private PublicSessionService publicSessionService;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private TableSessionRepository sessionRepository;
    @Mock private Authentication authentication;

    private BillController controller;

    private static final Long RESTAURANT_ID = 1L;
    private static final Long TABLE_ID = 5L;
    private static final Long SESSION_ID = 20L;
    private static final String QR_TOKEN = "QR-TOKEN-1";
    private static final String SESSION_CODE = "1234";

    @BeforeEach
    void setUp() {
        controller = new BillController(billCalculationService, publicSessionService,
                tableRepository, sessionRepository);

        AuthPrincipal principal = new AuthPrincipal(10L, "MANAGER", RESTAURANT_ID);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ==================== Public Bill ====================

    @Nested
    @DisplayName("Get Public Bill")
    class GetPublicBillTests {

        @Test
        @DisplayName("Should return 200 with bill on success")
        void shouldReturn200WithBill() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);

            RestaurantTable table = buildTable();
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(table));

            TableSession session = buildSession();
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID))
                    .thenReturn(Optional.of(session));

            SessionBillDto billDto = new SessionBillDto(1L, 1, SESSION_CODE,
                    new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("110.00"),
                    "SYP", BillStatus.DRAFT, List.of(), null);
            when(billCalculationService.getOrComputeBill(SESSION_ID)).thenReturn(billDto);

            ResponseEntity<SessionBillDto> response = controller.getPublicBill(QR_TOKEN, SESSION_CODE);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().grandTotal()).isEqualByComparingTo(new BigDecimal("110.00"));
        }

        @Test
        @DisplayName("Should throw when QR token not found")
        void shouldThrowWhenQrTokenNotFound() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getPublicBill(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Invalid or inactive QR token");
        }

        @Test
        @DisplayName("Should throw when no active session")
        void shouldThrowWhenNoActiveSession() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN))
                    .thenReturn(Optional.of(buildTable()));
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getPublicBill(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No active session");
        }

        @Test
        @DisplayName("Should verify session before computing bill")
        void shouldVerifySessionFirst() {
            doThrow(new BusinessRuleException("Invalid code"))
                    .when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);

            assertThatThrownBy(() -> controller.getPublicBill(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(BusinessRuleException.class);

            verify(tableRepository, never()).findActiveByQrTokenFetchRestaurant(any());
        }
    }

    // ==================== Staff Bill ====================

    @Nested
    @DisplayName("Get Staff Bill")
    class GetStaffBillTests {

        @Test
        @DisplayName("Should return 200 with bill for valid table")
        void shouldReturn200ForValidTable() {
            Restaurant restaurant = new Restaurant();
            restaurant.setId(RESTAURANT_ID);

            TableSession session = buildSession();
            session.setRestaurant(restaurant);
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID))
                    .thenReturn(Optional.of(session));

            SessionBillDto billDto = new SessionBillDto(1L, 1, SESSION_CODE,
                    new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"),
                    "SYP", BillStatus.DRAFT, List.of(), null);
            when(billCalculationService.getOrComputeBill(SESSION_ID)).thenReturn(billDto);

            ResponseEntity<SessionBillDto> response = controller.getStaffBill(authentication, TABLE_ID);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().grandTotal()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("Should throw when no active session for table")
        void shouldThrowWhenNoSession() {
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> controller.getStaffBill(authentication, TABLE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No active session");
        }

        @Test
        @DisplayName("Should throw when session belongs to different restaurant")
        void shouldThrowWhenWrongRestaurant() {
            Restaurant otherRestaurant = new Restaurant();
            otherRestaurant.setId(999L);

            TableSession session = buildSession();
            session.setRestaurant(otherRestaurant);
            when(sessionRepository.findOpenSessionByTableId(TABLE_ID))
                    .thenReturn(Optional.of(session));

            assertThatThrownBy(() -> controller.getStaffBill(authentication, TABLE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }
    }

    // ==================== Helpers ====================

    private RestaurantTable buildTable() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);

        RestaurantTable table = new RestaurantTable();
        table.setId(TABLE_ID);
        table.setTableNumber(1);
        table.setRestaurant(restaurant);
        return table;
    }

    private TableSession buildSession() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);

        TableSession session = new TableSession();
        session.setId(SESSION_ID);
        session.setRestaurant(restaurant);
        return session;
    }
}


