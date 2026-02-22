package com.restaurantqr.order;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.RateLimitException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.order.staff.OrderStatusResponse;
import com.restaurantqr.table.PublicSessionService;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final RestaurantTableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PublicSessionService publicSessionService;

    public OrderService(RestaurantTableRepository tableRepository,
                        MenuItemRepository menuItemRepository,
                        CustomerOrderRepository orderRepository,
                        OrderItemRepository orderItemRepository, PublicSessionService publicSessionService) {
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.publicSessionService = publicSessionService;
    }

    @Transactional
    public SubmitOrderResponse submitOrder(String qrToken, String code, SubmitOrderRequest request) {
        log.info("Submit order request received. qrToken={}, codeProvided={}, itemsCount={}",
                qrToken, code != null, request.items().size());

        // 0) Verify session code (NEW)
        publicSessionService.verifySessionOrThrow(qrToken, code);

        // 1) Resolve table by QR
        RestaurantTable table = tableRepository.findActiveByQrTokenForUpdate(qrToken)
                .orElseThrow(() -> {
                    log.warn("Invalid/inactive QR token on submit order. qrToken={}", qrToken);
                    return new ResourceNotFoundException("Invalid or inactive QR token");
                });


        var restaurant = table.getRestaurant();
        if (!restaurant.isActive()) {
            log.warn("Restaurant inactive on submit order. restaurantId={}, qrToken={}", restaurant.getId(), qrToken);
            throw new BusinessRuleException("Restaurant is inactive");
        }

        // 2) Rate limit: one order per minute per table
        Instant threshold = Instant.now().minusSeconds(60);
        boolean rateLimited = orderRepository.existsByTableIdAndCreatedAtAfter(table.getId(), threshold);
        if (rateLimited) {
            log.warn("Rate limit violated. tableId={}, threshold={}", table.getId(), threshold);
            throw new RateLimitException("يمكن إرسال طلب واحد فقط لكل طاولة خلال دقيقة." +
                    "انتظر قليلًا ثم حاول مرة أخرى");
        }

        Long restaurantId = restaurant.getId();

        // 3) Load all requested menu items (must belong to same restaurant)
        List<Long> requestedIds = request.items().stream()
                .map(SubmitOrderItemRequest::menuItemId)
                .distinct()
                .toList();

        List<MenuItem> foundItems = menuItemRepository
                .findByIdInAndRestaurantIdAndIsActiveTrue(requestedIds, restaurantId);

        Map<Long, MenuItem> itemsById = foundItems.stream()
                .collect(Collectors.toMap(MenuItem::getId, it -> it));

        // 4) Validate each requested item exists + available + active
        for (Long id : requestedIds) {
            MenuItem mi = itemsById.get(id);
            if (mi == null) {
                log.warn("Menu item not found or not in restaurant. menuItemId={}, restaurantId={}", id, restaurantId);
                throw new ResourceNotFoundException("Menu item not found: " + id);
            }
            if (!mi.isActive()) {
                log.warn("Menu item inactive. menuItemId={}", id);
                throw new BusinessRuleException("Menu item is inactive: " + id);
            }
            if (!mi.isAvailable()) {
                log.warn("Menu item not available. menuItemId={}", id);
                throw new BusinessRuleException("Menu item is not available: " + id);
            }
        }

        // 5) Create order + calculate totals from DB prices (snapshot)
        CustomerOrder order = new CustomerOrder();
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setNote(request.note());
        order.setStatus(OrderStatus.NEW);
        order.setSentToKitchenAt(null);

        BigDecimal totalAmount = BigDecimal.ZERO;

        order.setTotalAmount(BigDecimal.ZERO);
        CustomerOrder savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = new ArrayList<>();

        for (SubmitOrderItemRequest reqItem : request.items()) {
            MenuItem mi = itemsById.get(reqItem.menuItemId());

            BigDecimal unitPrice = mi.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(reqItem.quantity()));

            totalAmount = totalAmount.add(lineTotal);

            OrderItem oi = new OrderItem();
            oi.setOrder(savedOrder);
            oi.setMenuItem(mi);
            oi.setQuantity(reqItem.quantity());
            oi.setUnitPrice(unitPrice);
            oi.setTotalPrice(lineTotal);
            oi.setNote(reqItem.note());

            orderItems.add(oi);
        }

        orderItemRepository.saveAll(orderItems);

        savedOrder.setTotalAmount(totalAmount);
        CustomerOrder finalOrder = orderRepository.save(savedOrder);

        log.info("Order created successfully. orderId={}, tableId={}, restaurantId={}, totalAmount={}",
                finalOrder.getId(), table.getId(), restaurantId, totalAmount);

        return new SubmitOrderResponse(finalOrder.getId(), finalOrder.getCreatedAt(), finalOrder.getTotalAmount());
    }

}

