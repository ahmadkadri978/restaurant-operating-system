package com.restaurantqr.owner;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.servicecall.ServiceCallRepository;
import com.restaurantqr.table.RestaurantTableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class OwnerDashboardServiceImpl implements OwnerDashboardService {

    private static final Logger log = LoggerFactory.getLogger(OwnerDashboardServiceImpl.class);
    private static final int TOP_ITEMS_LIMIT = 10;
    private static final int TOP_TABLES_LIMIT = 10;

    private final RestaurantRepository restaurantRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ServiceCallRepository serviceCallRepository;
    private final RestaurantTableRepository tableRepository;

    public OwnerDashboardServiceImpl(
            RestaurantRepository restaurantRepository,
            CustomerOrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ServiceCallRepository serviceCallRepository,
            RestaurantTableRepository tableRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.serviceCallRepository = serviceCallRepository;
        this.tableRepository = tableRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerDashboardResponse getDashboard(
            Long restaurantId,
            DashboardPeriod period,
            LocalDate anchorDate,
            String zone
    ) {
        // Validate required parameters
        validateParameters(restaurantId, period, zone);

        // Parse and validate timezone
        ZoneId zoneId = parseZoneId(zone);

        // Resolve anchor date: use provided date or current date in the given timezone
        LocalDate effectiveAnchorDate = resolveAnchorDate(anchorDate, zoneId);

        log.info("Get owner dashboard | restaurantId={} | period={} | anchorDate={} | zone={}",
                restaurantId, period, effectiveAnchorDate, zone);

        // Fetch and validate restaurant
        Restaurant restaurant = fetchRestaurant(restaurantId);

        // Calculate date range based on period and timezone
        DateRangeDto dateRange = calculateDateRange(period, effectiveAnchorDate, zoneId);

        log.debug("Calculated date range | start={} | end={} | period={}",
                dateRange.startDate(), dateRange.endDate(), period);

        // Build dashboard response with data from repositories
        return buildDashboardResponse(restaurant, period, dateRange);
    }

    /**
     * Validates required input parameters.
     */
    private void validateParameters(Long restaurantId, DashboardPeriod period, String zone) {
        if (restaurantId == null) {
            throw new BusinessRuleException("restaurantId is required");
        }
        if (period == null) {
            throw new BusinessRuleException("period is required");
        }
        if (zone == null || zone.isBlank()) {
            throw new BusinessRuleException("zone is required");
        }
    }

    /**
     * Parses and validates timezone ID.
     *
     * @throws BusinessRuleException if zone ID is invalid
     */
    private ZoneId parseZoneId(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            log.warn("Invalid timezone provided: {}", zone);
            throw new BusinessRuleException("Invalid timezone: " + zone);
        }
    }

    /**
     * Resolves the anchor date: uses provided date or current date in the given timezone.
     */
    private LocalDate resolveAnchorDate(LocalDate anchorDate, ZoneId zoneId) {
        if (anchorDate != null) {
            return anchorDate;
        }
        // Use current date in the provided timezone
        return LocalDate.now(zoneId);
    }

    /**
     * Fetches restaurant by ID and validates existence.
     */
    private Restaurant fetchRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> {
                    log.error("Restaurant not found: {}", restaurantId);
                    return new BusinessRuleException("Restaurant not found: " + restaurantId);
                });
    }

    /**
     * Calculates date range based on period type and timezone.
     * Returns start (inclusive) and end (exclusive) as Instants for database queries.
     */
    private DateRangeDto calculateDateRange(DashboardPeriod period, LocalDate anchorDate, ZoneId zoneId) {
        Instant startDate;
        Instant endDate;

        switch (period) {
            case DAY -> {
                // Single day: from 00:00:00 to 23:59:59.999
                startDate = anchorDate.atStartOfDay(zoneId).toInstant();
                endDate = anchorDate.plusDays(1).atStartOfDay(zoneId).toInstant();
            }
            case WEEK -> {
                // Week: Monday 00:00:00 to next Monday 00:00:00
                LocalDate startOfWeek = anchorDate.with(DayOfWeek.MONDAY);
                startDate = startOfWeek.atStartOfDay(zoneId).toInstant();
                endDate = startOfWeek.plusWeeks(1).atStartOfDay(zoneId).toInstant();
            }
            case MONTH -> {
                // Month: 1st day 00:00:00 to 1st day of next month 00:00:00
                LocalDate startOfMonth = anchorDate.withDayOfMonth(1);
                startDate = startOfMonth.atStartOfDay(zoneId).toInstant();
                endDate = startOfMonth.plusMonths(1).atStartOfDay(zoneId).toInstant();
            }
            case YEAR -> {
                // Year: Jan 1st 00:00:00 to next Jan 1st 00:00:00
                LocalDate startOfYear = anchorDate.withDayOfYear(1);
                startDate = startOfYear.atStartOfDay(zoneId).toInstant();
                endDate = startOfYear.plusYears(1).atStartOfDay(zoneId).toInstant();
            }
            default -> {
                log.error("Unsupported dashboard period: {}", period);
                throw new BusinessRuleException("Unsupported period: " + period);
            }
        }

        return new DateRangeDto(startDate, endDate, period);
    }

    /**
     * Builds the complete dashboard response with all statistics.
     */
    private OwnerDashboardResponse buildDashboardResponse(
            Restaurant restaurant,
            DashboardPeriod period,
            DateRangeDto dateRange
    ) {
        Long restaurantId = restaurant.getId();
        Instant startDate = dateRange.startDate();
        Instant endDate = dateRange.endDate();

        // Query overview statistics
        OverviewDto overview = queryOverviewStatistics(restaurantId, startDate, endDate);

        // Query revenue trend
        List<RevenuePointDto> revenueTrend = queryRevenueTrend(restaurantId, startDate, endDate, period);

        // Query orders by status
        List<StatusCountDto> ordersByStatus = queryOrdersByStatus(restaurantId, startDate, endDate);

        // Query top items
        List<TopItemDto> topItemsByQuantity = queryTopItemsByQuantity(restaurantId, startDate, endDate);
        List<TopItemDto> topItemsByRevenue = queryTopItemsByRevenue(restaurantId, startDate, endDate);

        // Query top tables
        List<TopTableDto> topTables = queryTopTables(restaurantId, startDate, endDate);

        // Query service calls by type
        List<ServiceCallTypeCountDto> serviceCallsByType = queryServiceCallsByType(restaurantId, startDate, endDate);

        // Query peak hours
        List<PeakHourDto> peakHours = queryPeakHours(restaurantId, startDate, endDate);

        return new OwnerDashboardResponse(
                restaurantId,
                restaurant.getName(),
                period,
                dateRange,
                overview,
                revenueTrend,
                ordersByStatus,
                topItemsByQuantity,
                topItemsByRevenue,
                topTables,
                serviceCallsByType,
                peakHours
        );
    }

    // ==================== Repository Query Methods ====================

    /**
     * Query overview statistics from database.
     */
    private OverviewDto queryOverviewStatistics(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying overview statistics | restaurantId={}", restaurantId);

        // Count total orders (all statuses)
        Long totalOrders = orderRepository.countOrdersByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );

        // Count orders by each status
        Long newOrders = orderRepository.countOrdersByRestaurantAndStatusAndDateRange(
                restaurantId, OrderStatus.NEW, startDate, endDate
        );

        Long sentToKitchenOrders = orderRepository.countOrdersByRestaurantAndStatusAndDateRange(
                restaurantId, OrderStatus.SENT_TO_KITCHEN, startDate, endDate
        );

        Long doneOrders = orderRepository.countOrdersByRestaurantAndStatusAndDateRange(
                restaurantId, OrderStatus.DONE, startDate, endDate
        );

        Long cancelledOrders = orderRepository.countOrdersByRestaurantAndStatusAndDateRange(
                restaurantId, OrderStatus.CANCELLED, startDate, endDate
        );

        // Sum gross revenue (excluding CANCELLED orders)
        BigDecimal grossRevenue = orderRepository.sumRevenueByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        // Ensure non-null
        if (grossRevenue == null) {
            grossRevenue = BigDecimal.ZERO;
        }

        // Calculate average order value (revenue / done orders)
        BigDecimal averageOrderValue = BigDecimal.ZERO;
        if (doneOrders > 0) {
            averageOrderValue = grossRevenue
                    .divide(BigDecimal.valueOf(doneOrders), 2, RoundingMode.HALF_UP);
        }

        // Calculate completion rate: DONE / (DONE + CANCELLED) * 100
        BigDecimal completionRate = BigDecimal.ZERO;
        long completedAndCancelled = doneOrders + cancelledOrders;
        if (completedAndCancelled > 0) {
            completionRate = BigDecimal.valueOf(doneOrders)
                    .divide(BigDecimal.valueOf(completedAndCancelled), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Calculate cancellation rate: CANCELLED / total * 100
        BigDecimal cancellationRate = BigDecimal.ZERO;
        if (totalOrders > 0) {
            cancellationRate = BigDecimal.valueOf(cancelledOrders)
                    .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Count active tables in restaurant (not time-scoped)
        Long activeTablesCount = tableRepository.countActiveTablesByRestaurant(restaurantId);

        // Count tables that have orders in this period
        Long tablesWithOrdersCount = orderRepository.countDistinctTablesWithOrders(
                restaurantId, startDate, endDate
        );

        // Count service calls in period
        Long serviceCallsCount = serviceCallRepository.countServiceCallsByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );

        log.debug("Overview calculated | totalOrders={} | doneOrders={} | cancelledOrders={} | grossRevenue={} | completionRate={}% | cancellationRate={}%",
                totalOrders, doneOrders, cancelledOrders, grossRevenue, completionRate, cancellationRate);

        return new OverviewDto(
                totalOrders,
                newOrders,
                sentToKitchenOrders,
                doneOrders,
                cancelledOrders,
                completionRate,
                cancellationRate,
                grossRevenue,
                averageOrderValue,
                activeTablesCount,
                tablesWithOrdersCount,
                serviceCallsCount
        );
    }

    /**
     * Query revenue trend based on period.
     */
    private List<RevenuePointDto> queryRevenueTrend(
            Long restaurantId,
            Instant startDate,
            Instant endDate,
            DashboardPeriod period
    ) {
        log.debug("Querying revenue trend | restaurantId={} | period={}", restaurantId, period);

        List<RevenueTrendProjection> projections;

        switch (period) {
            case DAY -> {
                // Hourly aggregation for DAY
                projections = orderRepository.findRevenueTrendByHour(restaurantId, startDate, endDate);
            }
            case WEEK, MONTH -> {
                // Daily aggregation for WEEK and MONTH
                projections = orderRepository.findRevenueTrendByDay(restaurantId, startDate, endDate);
            }
            case YEAR -> {
                // Monthly aggregation for YEAR
                projections = orderRepository.findRevenueTrendByMonth(restaurantId, startDate, endDate);
            }
            default -> {
                log.warn("Unsupported period for revenue trend: {}", period);
                return new ArrayList<>();
            }
        }

        // Convert projections to DTOs
        return projections.stream()
                .map(p -> new RevenuePointDto(
                        p.getTimestamp(),
                        p.getRevenue(),
                        p.getOrderCount()
                ))
                .toList();
    }

    /**
     * Query orders grouped by status.
     */
    private List<StatusCountDto> queryOrdersByStatus(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying orders by status | restaurantId={}", restaurantId);

        return orderRepository.countOrdersGroupedByStatus(restaurantId, startDate, endDate);
    }

    /**
     * Query top items by quantity sold.
     */
    private List<TopItemDto> queryTopItemsByQuantity(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying top items by quantity | restaurantId={}", restaurantId);

        Pageable topN = PageRequest.of(0, TOP_ITEMS_LIMIT);
        return orderItemRepository.findTopItemsByQuantity(restaurantId, startDate, endDate, topN);
    }

    /**
     * Query top items by revenue.
     */
    private List<TopItemDto> queryTopItemsByRevenue(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying top items by revenue | restaurantId={}", restaurantId);

        Pageable topN = PageRequest.of(0, TOP_ITEMS_LIMIT);
        return orderItemRepository.findTopItemsByRevenue(restaurantId, startDate, endDate, topN);
    }

    /**
     * Query top tables by order count.
     */
    private List<TopTableDto> queryTopTables(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying top tables | restaurantId={}", restaurantId);

        Pageable topN = PageRequest.of(0, TOP_TABLES_LIMIT);
        return orderRepository.findTopTablesByOrderCount(restaurantId, startDate, endDate, topN);
    }

    /**
     * Query service calls grouped by type.
     */
    private List<ServiceCallTypeCountDto> queryServiceCallsByType(
            Long restaurantId,
            Instant startDate,
            Instant endDate
    ) {
        log.debug("Querying service calls by type | restaurantId={}", restaurantId);

        return serviceCallRepository.countServiceCallsGroupedByType(restaurantId, startDate, endDate);
    }

    /**
     * Query peak hours from order timestamps.
     */
    private List<PeakHourDto> queryPeakHours(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying peak hours | restaurantId={}", restaurantId);

        List<PeakHourProjection> projections = orderRepository.findPeakHoursByOrderCount(
                restaurantId, startDate, endDate
        );

        // Convert projections to DTOs
        return projections.stream()
                .map(p -> new PeakHourDto(
                        p.getHour(),
                        p.getOrderCount(),
                        p.getRevenue()
                ))
                .toList();
    }
}


