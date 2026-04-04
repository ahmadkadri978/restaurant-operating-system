package com.restaurantqr.order;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.RateLimitException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.order.staff.OrderStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderController Unit Tests")
class OrderControllerTest {

    @Mock private OrderService orderService;

    private OrderController controller;

    private static final String QR_TOKEN = "QR-TOKEN-1";
    private static final String SESSION_CODE = "1234";

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService);
    }

    // ==================== Submit Order ====================

    @Nested
    @DisplayName("Submit Order")
    class SubmitOrderTests {

        @Test
        @DisplayName("Should return 201 CREATED on successful submission")
        void shouldReturn201OnSuccess() {
            SubmitOrderResponse expected = new SubmitOrderResponse(100L, 1, Instant.now(), new BigDecimal("20.00"));
            SubmitOrderRequest request = new SubmitOrderRequest(
                    List.of(new SubmitOrderItemRequest(1L, 2, null)), null);

            when(orderService.submitOrder(QR_TOKEN, SESSION_CODE, request)).thenReturn(expected);

            ResponseEntity<SubmitOrderResponse> response = controller.submitOrder(QR_TOKEN, SESSION_CODE, request);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().orderId()).isEqualTo(100L);
            assertThat(response.getBody().displayOrderNumber()).isEqualTo(1);
            assertThat(response.getBody().totalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        }

        @Test
        @DisplayName("Should delegate qrToken, code, and request to service")
        void shouldDelegateToService() {
            SubmitOrderRequest request = new SubmitOrderRequest(
                    List.of(new SubmitOrderItemRequest(1L, 1, null)), "note");
            SubmitOrderResponse expected = new SubmitOrderResponse(1L, 1, Instant.now(), BigDecimal.TEN);
            when(orderService.submitOrder(QR_TOKEN, SESSION_CODE, request)).thenReturn(expected);

            controller.submitOrder(QR_TOKEN, SESSION_CODE, request);

            verify(orderService).submitOrder(QR_TOKEN, SESSION_CODE, request);
        }

        @Test
        @DisplayName("Should propagate RateLimitException from service")
        void shouldPropagateRateLimitException() {
            SubmitOrderRequest request = new SubmitOrderRequest(
                    List.of(new SubmitOrderItemRequest(1L, 1, null)), null);
            when(orderService.submitOrder(any(), any(), any()))
                    .thenThrow(new RateLimitException("Rate limited"));

            assertThatThrownBy(() -> controller.submitOrder(QR_TOKEN, SESSION_CODE, request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("Should propagate ResourceNotFoundException from service")
        void shouldPropagateNotFound() {
            SubmitOrderRequest request = new SubmitOrderRequest(
                    List.of(new SubmitOrderItemRequest(1L, 1, null)), null);
            when(orderService.submitOrder(any(), any(), any()))
                    .thenThrow(new ResourceNotFoundException("Invalid QR token"));

            assertThatThrownBy(() -> controller.submitOrder(QR_TOKEN, SESSION_CODE, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== Get Session Orders ====================

    @Nested
    @DisplayName("Get Session Orders")
    class GetSessionOrdersTests {

        @Test
        @DisplayName("Should return 200 with session orders")
        void shouldReturn200WithOrders() {
            SessionOrdersResponse expected = new SessionOrdersResponse(List.of(
                    new SessionOrderDto(1L, 1, OrderStatus.NEW, Instant.now(),
                            new BigDecimal("20.00"), null, List.of())
            ));
            when(orderService.getSessionOrders(QR_TOKEN, SESSION_CODE)).thenReturn(expected);

            ResponseEntity<SessionOrdersResponse> response = controller.getSessionOrders(QR_TOKEN, SESSION_CODE);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().orders()).hasSize(1);
        }

        @Test
        @DisplayName("Should return 200 with empty list when no orders")
        void shouldReturn200WithEmptyList() {
            when(orderService.getSessionOrders(QR_TOKEN, SESSION_CODE))
                    .thenReturn(new SessionOrdersResponse(List.of()));

            ResponseEntity<SessionOrdersResponse> response = controller.getSessionOrders(QR_TOKEN, SESSION_CODE);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().orders()).isEmpty();
        }

        @Test
        @DisplayName("Should delegate to service with correct parameters")
        void shouldDelegateToService() {
            when(orderService.getSessionOrders(QR_TOKEN, SESSION_CODE))
                    .thenReturn(new SessionOrdersResponse(List.of()));

            controller.getSessionOrders(QR_TOKEN, SESSION_CODE);

            verify(orderService).getSessionOrders(QR_TOKEN, SESSION_CODE);
        }

        @Test
        @DisplayName("Should propagate BusinessRuleException from service")
        void shouldPropagateBusinessRuleException() {
            when(orderService.getSessionOrders(any(), any()))
                    .thenThrow(new BusinessRuleException("Invalid session"));

            assertThatThrownBy(() -> controller.getSessionOrders(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}

