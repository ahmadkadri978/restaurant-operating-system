package com.restaurantqr.order;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.RateLimitException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.menu.MenuItem;
import com.restaurantqr.menu.MenuItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.sse.OrderCreatedPayload;
import com.restaurantqr.sse.RestaurantEventPublisher;
import com.restaurantqr.table.PublicSessionService;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int RATE_LIMIT_SECONDS = 60;
    private static final int MAX_ITEMS_PER_ORDER = 200; // حماية ضد abuse
    private static final int MAX_QTY_PER_ITEM = 50;      // تقدير منطقي - عدله حسب مشروعك

    private final RestaurantTableRepository tableRepository;
    private final MenuItemRepository menuItemRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RestaurantOrderCounterRepository orderCounterRepository;
    private final PublicSessionService publicSessionService;
    private final RestaurantEventPublisher eventPublisher;

    public OrderService(RestaurantTableRepository tableRepository,
                        MenuItemRepository menuItemRepository,
                        CustomerOrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        RestaurantOrderCounterRepository orderCounterRepository,
                        PublicSessionService publicSessionService,
                        RestaurantEventPublisher eventPublisher) {
        this.tableRepository = tableRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderCounterRepository = orderCounterRepository;
        this.publicSessionService = publicSessionService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public SubmitOrderResponse submitOrder(String qrToken, String code, SubmitOrderRequest request) {

        if (qrToken == null || qrToken.isBlank()) {
            throw new BusinessRuleException("qrToken is required");
        }
        if (code == null || code.isBlank()) {
            // controller يرسل code كـ required، لكن نحمي الخدمة أيضًا
            throw new BusinessRuleException("Session code is required");
        }
        if (request == null) {
            throw new BusinessRuleException("Request body is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessRuleException("Order items must not be empty");
        }
        if (request.items().size() > MAX_ITEMS_PER_ORDER) {
            throw new BusinessRuleException("Too many items in one order");
        }

        log.info("PUBLIC_SUBMIT_ORDER | qrToken={} | itemsCount={}", qrToken, request.items().size());

        // 0) Verify session code
        publicSessionService.verifySessionOrThrow(qrToken, code);

        // 1) Resolve table by QR (pessimistic lock already applied in repo method)
        RestaurantTable table = tableRepository.findActiveByQrTokenForUpdate(qrToken)
                .orElseThrow(() -> {
                    log.warn("Submit order failed: invalid/inactive QR token | qrToken={}", qrToken);
                    return new ResourceNotFoundException("Invalid or inactive QR token");
                });

        Restaurant restaurant = table.getRestaurant();
        if (restaurant == null) {
            // حماية إضافية
            throw new BusinessRuleException("Table has no restaurant");
        }
        if (!restaurant.isActive()) {
            log.warn("Submit order failed: restaurant inactive | restaurantId={} | qrToken={}", restaurant.getId(), qrToken);
            throw new BusinessRuleException("Restaurant is inactive");
        }

        // 2) Rate limit: one order per minute per table
        Instant threshold = Instant.now().minusSeconds(RATE_LIMIT_SECONDS);
        if (orderRepository.existsByTableIdAndCreatedAtAfter(table.getId(), threshold)) {
            log.warn("Rate limit violated | tableId={} | threshold={}", table.getId(), threshold);
            throw new RateLimitException("يمكن إرسال طلب واحد فقط لكل طاولة خلال دقيقة. انتظر قليلًا ثم حاول مرة أخرى");
        }

        Long restaurantId = restaurant.getId();

        // 3) Validate quantities + collect requested ids

        List<Long> requestedIds = new ArrayList<>(request.items().size());
        for (SubmitOrderItemRequest it : request.items()) {
            if (it == null) {
                throw new BusinessRuleException("Order item must not be null");
            }
            if (it.menuItemId() == null) {
                throw new BusinessRuleException("menuItemId is required");
            }
            if (it.quantity() == null || it.quantity() <= 0) {
                throw new BusinessRuleException("quantity must be greater than 0 for item " + it.menuItemId());
            }
            if (it.quantity() > MAX_QTY_PER_ITEM) {
                throw new BusinessRuleException("quantity too large for item " + it.menuItemId());
            }
            requestedIds.add(it.menuItemId());
        }

        List<Long> distinctIds = requestedIds.stream().distinct().toList();

        // 4) Load menu items (must belong to restaurant + active)
        List<MenuItem> foundItems = menuItemRepository
                .findByIdInAndRestaurantIdAndIsActiveTrue(distinctIds, restaurantId);

        Map<Long, MenuItem> itemsById = foundItems.stream()
                .collect(Collectors.toMap(MenuItem::getId, it -> it));

        // 5) Validate existence + availability
        for (Long id : distinctIds) {
            MenuItem mi = itemsById.get(id);
            if (mi == null) {
                log.warn("Menu item not found or not in restaurant | menuItemId={} | restaurantId={}", id, restaurantId);
                throw new ResourceNotFoundException("Menu item not found: " + id);
            }
            // isActiveTrue already filtered, لكن نترك حماية
            if (!mi.isActive()) {
                throw new BusinessRuleException("Menu item is inactive: " + id);
            }
            if (!mi.isAvailable()) {
                throw new BusinessRuleException("Menu item is not available: " + id);
            }
            if (mi.getPrice() == null || mi.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("Invalid price for menu item: " + id);
            }
        }

        // 6) Create order
        CustomerOrder order = new CustomerOrder();
        order.setRestaurant(restaurant);
        order.setTable(table);
        order.setNote(request.note());
        order.setStatus(OrderStatus.NEW);
        order.setSentToKitchenAt(null);

        // 6a) Generate restaurant-specific display order number (atomic)
        Integer displayOrderNumber = generateNextOrderNumber(restaurantId);
        order.setRestaurantOrderNumber(displayOrderNumber);

        // مهم: totalAmount non-null في DB
        order.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        CustomerOrder savedOrder = orderRepository.save(order);

        // 7) Build order items + compute total (DB price snapshot)
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<OrderItem> orderItems = new ArrayList<>(request.items().size());

        for (SubmitOrderItemRequest reqItem : request.items()) {
            MenuItem mi = itemsById.get(reqItem.menuItemId());

            BigDecimal unitPrice = mi.getPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice
                    .multiply(BigDecimal.valueOf(reqItem.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            totalAmount = totalAmount.add(lineTotal).setScale(2, RoundingMode.HALF_UP);

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

        // 8) Update total
        savedOrder.setTotalAmount(totalAmount);
        CustomerOrder finalOrder = orderRepository.save(savedOrder);

        log.info("Order created | orderId={} | displayOrderNumber={} | restaurantId={} | tableId={} | total={}",
                finalOrder.getId(), displayOrderNumber, restaurantId, table.getId(), totalAmount);

        // 9) Publish SSE event for real-time updates
        eventPublisher.publishOrderCreated(
                restaurantId,
                new OrderCreatedPayload(
                        finalOrder.getId(),
                        finalOrder.getRestaurantOrderNumber(),
                        table.getTableNumber(),
                        totalAmount,
                        finalOrder.getNote(),
                        finalOrder.getCreatedAt()
                )
        );

        return new SubmitOrderResponse(
                finalOrder.getId(),
                finalOrder.getRestaurantOrderNumber(),
                finalOrder.getCreatedAt(),
                finalOrder.getTotalAmount()
        );
    }

    /**
     * Atomically generates the next order number for a restaurant.
     * Uses pessimistic locking on the counter row to prevent duplicates.
     *
     * @param restaurantId the restaurant ID
     * @return the next display order number
     */
    private Integer generateNextOrderNumber(Long restaurantId) {
        RestaurantOrderCounter counter = orderCounterRepository
                .findByIdForUpdate(restaurantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Order counter not found for restaurant: " + restaurantId +
                        ". Ensure counter is created when restaurant is set up."));

        int nextNumber = counter.getLastOrderNumber() + 1;
        counter.setLastOrderNumber(nextNumber);
        orderCounterRepository.save(counter);

        log.debug("Generated display order number {} for restaurant {}", nextNumber, restaurantId);
        return nextNumber;
    }

    /**
     * Retrieves all orders belonging to the current active session of a table.
     * Session scope is determined by table.sessionOpenedAt — only orders created
     * at or after that timestamp are returned.
     *
     * @param qrToken the table's QR token
     * @param code    the 4-digit session code (verified against the active session)
     * @return list of orders for the current session
     */
    @Transactional(readOnly = true)
    public SessionOrdersResponse getSessionOrders(String qrToken, String code) {
        if (qrToken == null || qrToken.isBlank()) {
            throw new BusinessRuleException("qrToken is required");
        }
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("Session code is required");
        }

        // 1) Verify session is still active and code matches
        publicSessionService.verifySessionOrThrow(qrToken, code);

        // 2) Load table (read path — no lock needed)
        RestaurantTable table = tableRepository.findActiveByQrTokenFetchRestaurant(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or inactive QR token"));

        // 3) Determine session boundary
        Instant sessionStart = table.getSessionOpenedAt();
        if (sessionStart == null) {
            // Session was just closed between verify and here (edge case)
            return new SessionOrdersResponse(List.of());
        }

        // 4) Fetch orders created during this session
        List<CustomerOrder> orders = orderRepository.findByTableIdAndCreatedAtSince(
                table.getId(), sessionStart
        );

        if (orders.isEmpty()) {
            return new SessionOrdersResponse(List.of());
        }

        // 5) Batch-load order items to avoid N+1 queries
        List<Long> orderIds = orders.stream().map(CustomerOrder::getId).toList();
        List<OrderItem> allItems = orderItemRepository.findByOrderIdInWithMenuItem(orderIds);

        // Group items by order ID
        Map<Long, List<SessionOrderItemDto>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(
                        oi -> oi.getOrder().getId(),
                        Collectors.mapping(
                                oi -> new SessionOrderItemDto(
                                        oi.getMenuItem().getName(),
                                        oi.getQuantity(),
                                        oi.getUnitPrice(),
                                        oi.getTotalPrice(),
                                        oi.getNote()
                                ),
                                Collectors.toList()
                        )
                ));

        // 6) Map to DTOs
        List<SessionOrderDto> dtos = orders.stream()
                .map(o -> new SessionOrderDto(
                        o.getId(),
                        o.getRestaurantOrderNumber(),
                        o.getStatus(),
                        o.getCreatedAt(),
                        o.getTotalAmount(),
                        o.getNote(),
                        itemsByOrderId.getOrDefault(o.getId(), List.of())
                ))
                .toList();

        log.debug("Session orders | qrToken={} | tableId={} | sessionStart={} | count={}",
                qrToken, table.getId(), sessionStart, dtos.size());

        return new SessionOrdersResponse(dtos);
    }
}

