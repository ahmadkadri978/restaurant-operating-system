package com.restaurantqr.owner;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.finance.SessionBillRepository;
import com.restaurantqr.finance.SessionPaymentRepository;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.servicecall.ServiceCallRepository;
import com.restaurantqr.table.RestaurantTableRepository;
import com.restaurantqr.table.TableSession;
import com.restaurantqr.table.TableSessionRepository;
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
    private final TableSessionRepository sessionRepository;
    private final SessionBillRepository billRepository;
    private final SessionPaymentRepository paymentRepository;

    public OwnerDashboardServiceImpl(
            RestaurantRepository restaurantRepository,
            CustomerOrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ServiceCallRepository serviceCallRepository,
            RestaurantTableRepository tableRepository,
            TableSessionRepository sessionRepository,
            SessionBillRepository billRepository,
            SessionPaymentRepository paymentRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.serviceCallRepository = serviceCallRepository;
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
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
            log.warn("Invalid timezone provided: {} | error: {}", zone, e.getMessage());
            throw new BusinessRuleException(
                    String.format("Invalid timezone '%s'. Use standard IANA timezone IDs (e.g., 'Asia/Damascus', 'America/New_York')", zone)
            );
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

        // Query session analytics
        SessionAnalyticsDto sessionAnalytics = querySessionAnalytics(restaurantId, startDate, endDate);

        // Query charges breakdown from finalized bills
        List<ChargeBreakdownDto> chargesBreakdown = billRepository.findChargesBreakdownByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );

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
                peakHours,
                sessionAnalytics,
                chargesBreakdown
        );
    }

    // ==================== Repository Query Methods ====================

    /**
     * Query overview statistics from database.
     */
    private OverviewDto queryOverviewStatistics(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying overview statistics | restaurantId={}", restaurantId);

        // Count total orders (all statuses) - null-safe
        Long totalOrders = orderRepository.countOrdersByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        totalOrders = (totalOrders != null) ? totalOrders : 0L;

        // Count orders by each status - null-safe
        Long newOrders = orderRepository.countOrdersByRestaurantAndStatusAndDateRange(
                restaurantId, OrderStatus.NEW, startDate, endDate
        );
        newOrders = (newOrders != null) ? newOrders : 0L;

        Long sentToKitchenOrders = orderRepository.countOrdersByRestaurantAndStatusAndDateRange(
                restaurantId, OrderStatus.SENT_TO_KITCHEN, startDate, endDate
        );
        sentToKitchenOrders = (sentToKitchenOrders != null) ? sentToKitchenOrders : 0L;

        Long doneOrders = orderRepository.countOrdersByRestaurantAndStatusAndDateRange(
                restaurantId, OrderStatus.DONE, startDate, endDate
        );
        doneOrders = (doneOrders != null) ? doneOrders : 0L;

        Long cancelledOrders = orderRepository.countOrdersByRestaurantAndStatusAndDateRange(
                restaurantId, OrderStatus.CANCELLED, startDate, endDate
        );
        cancelledOrders = (cancelledOrders != null) ? cancelledOrders : 0L;

        // Sum gross revenue (excluding CANCELLED orders) - null-safe
        BigDecimal grossRevenue = orderRepository.sumRevenueByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        grossRevenue = (grossRevenue != null) ? grossRevenue : BigDecimal.ZERO;

        long nonCancelledOrders = totalOrders - cancelledOrders;
        BigDecimal averageOrderValue = BigDecimal.ZERO;
        if (nonCancelledOrders > 0) {
            averageOrderValue = grossRevenue
                    .divide(BigDecimal.valueOf(nonCancelledOrders), 2, RoundingMode.HALF_UP);
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

        // Count active tables in restaurant (not time-scoped) - null-safe
        Long activeTablesCount = tableRepository.countActiveTablesByRestaurant(restaurantId);
        activeTablesCount = (activeTablesCount != null) ? activeTablesCount : 0L;

        // Count tables that have orders in this period - null-safe
        Long tablesWithOrdersCount = orderRepository.countDistinctTablesWithOrders(
                restaurantId, startDate, endDate
        );
        tablesWithOrdersCount = (tablesWithOrdersCount != null) ? tablesWithOrdersCount : 0L;

        // Count service calls in period - null-safe
        Long serviceCallsCount = serviceCallRepository.countServiceCallsByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        serviceCallsCount = (serviceCallsCount != null) ? serviceCallsCount : 0L;

        // ── Billing revenue from finalized session bills ──
        BigDecimal totalBilledRevenue = billRepository.sumGrandTotalByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        totalBilledRevenue = (totalBilledRevenue != null) ? totalBilledRevenue : BigDecimal.ZERO;

        BigDecimal totalChargesRevenue = billRepository.sumChargesTotalByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        totalChargesRevenue = (totalChargesRevenue != null) ? totalChargesRevenue : BigDecimal.ZERO;

        Long finalizedBillsCount = billRepository.countFinalizedBillsByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        finalizedBillsCount = (finalizedBillsCount != null) ? finalizedBillsCount : 0L;

        BigDecimal averageBillValue = BigDecimal.ZERO;
        if (finalizedBillsCount > 0) {
            averageBillValue = totalBilledRevenue
                    .divide(BigDecimal.valueOf(finalizedBillsCount), 2, RoundingMode.HALF_UP);
        }

        // ── Tip revenue from session payments ──
        BigDecimal totalTipRevenue = paymentRepository.sumTipByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        totalTipRevenue = (totalTipRevenue != null) ? totalTipRevenue : BigDecimal.ZERO;

        // ── Items subtotal from finalized bills (billing-consistent source) ──
        BigDecimal billedItemsRevenue = billRepository.sumItemsSubtotalByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        billedItemsRevenue = (billedItemsRevenue != null) ? billedItemsRevenue : BigDecimal.ZERO;

        // ── Total collected = billed + tips ──
        BigDecimal totalCollected = totalBilledRevenue.add(totalTipRevenue);

        log.debug("Overview calculated | totalOrders={} | doneOrders={} | cancelledOrders={} | grossRevenue={} | billedRevenue={} | chargesRevenue={} | tipRevenue={} | totalCollected={} | completionRate={}% | cancellationRate={}%",
                totalOrders, doneOrders, cancelledOrders, grossRevenue, totalBilledRevenue, totalChargesRevenue, totalTipRevenue, totalCollected, completionRate, cancellationRate);

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
                serviceCallsCount,
                totalBilledRevenue,
                totalChargesRevenue,
                averageBillValue,
                finalizedBillsCount,
                totalTipRevenue,
                billedItemsRevenue,
                totalCollected
        );
    }

    /**
     * Query revenue trend based on period.
     * Returns time-bucketed revenue data with zero-safe handling.
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
                // Hourly aggregation for DAY (24 potential buckets)
                log.debug("Fetching hourly revenue trend for DAY period");
                projections = orderRepository.findRevenueTrendByHour(
                        restaurantId, 
                        startDate.getEpochSecond(), 
                        endDate.getEpochSecond()
                );
            }
            case WEEK, MONTH -> {
                // Daily aggregation for WEEK (7 buckets) and MONTH (28-31 buckets)
                log.debug("Fetching daily revenue trend for {} period", period);
                projections = orderRepository.findRevenueTrendByDay(
                        restaurantId, 
                        startDate.getEpochSecond(), 
                        endDate.getEpochSecond()
                );
            }
            case YEAR -> {
                // Monthly aggregation for YEAR (12 buckets)
                log.debug("Fetching monthly revenue trend for YEAR period");
                projections = orderRepository.findRevenueTrendByMonth(
                        restaurantId, 
                        startDate.getEpochSecond(), 
                        endDate.getEpochSecond()
                );
            }
            default -> {
                log.warn("Unsupported period for revenue trend: {}", period);
                return new ArrayList<>();
            }
        }

        // Handle null or empty results
        if (projections == null || projections.isEmpty()) {
            log.debug("No revenue trend data found for period {} | restaurantId={}", period, restaurantId);
            return new ArrayList<>();
        }

        // Convert projections to DTOs with null-safe handling
        // Convert Unix timestamp (Long seconds) to Instant
        List<RevenuePointDto> result = projections.stream()
                .filter(p -> p != null && p.getTimestamp() != null)
                .map(p -> new RevenuePointDto(
                        Instant.ofEpochSecond(p.getTimestamp()),  // Convert Long to Instant
                        p.getRevenue() != null ? p.getRevenue() : BigDecimal.ZERO,
                        p.getOrderCount() != null ? p.getOrderCount() : 0L
                ))
                .toList();

        log.debug("Revenue trend returned {} data points for {} period", result.size(), period);

        return result;
    }

    /**
     * Query orders grouped by status.
     */
    private List<StatusCountDto> queryOrdersByStatus(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying orders by status | restaurantId={}", restaurantId);

        List<StatusCountDto> result = orderRepository.countOrdersGroupedByStatus(restaurantId, startDate, endDate);
        
        // Handle null result
        if (result == null) {
            log.debug("No orders by status data found | restaurantId={}", restaurantId);
            return new ArrayList<>();
        }

        return result;
    }

    /**
     * Query top items by quantity sold.
     */
    private List<TopItemDto> queryTopItemsByQuantity(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying top items by quantity | restaurantId={}", restaurantId);

        Pageable topN = PageRequest.of(0, TOP_ITEMS_LIMIT);
        List<TopItemDto> result = orderItemRepository.findTopItemsByQuantity(restaurantId, startDate, endDate, topN);
        
        return result != null ? result : new ArrayList<>();
    }

    /**
     * Query top items by revenue.
     */
    private List<TopItemDto> queryTopItemsByRevenue(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying top items by revenue | restaurantId={}", restaurantId);

        Pageable topN = PageRequest.of(0, TOP_ITEMS_LIMIT);
        List<TopItemDto> result = orderItemRepository.findTopItemsByRevenue(restaurantId, startDate, endDate, topN);
        
        return result != null ? result : new ArrayList<>();
    }

    /**
     * Query top tables by order count.
     */
    private List<TopTableDto> queryTopTables(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying top tables | restaurantId={}", restaurantId);

        Pageable topN = PageRequest.of(0, TOP_TABLES_LIMIT);
        List<TopTableDto> result = orderRepository.findTopTablesByOrderCount(restaurantId, startDate, endDate, topN);
        
        return result != null ? result : new ArrayList<>();
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

        List<ServiceCallTypeCountDto> result = serviceCallRepository.countServiceCallsGroupedByType(
                restaurantId, startDate, endDate
        );
        
        return result != null ? result : new ArrayList<>();
    }

    /**
     * Query peak hours from order timestamps.
     * Returns hours sorted by order count (descending).
     */
    private List<PeakHourDto> queryPeakHours(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying peak hours | restaurantId={}", restaurantId);

        List<PeakHourProjection> projections = orderRepository.findPeakHoursByOrderCount(
                restaurantId, startDate.getEpochSecond(), endDate.getEpochSecond()
        );

        // Handle null or empty results
        if (projections == null || projections.isEmpty()) {
            log.debug("No peak hours data found | restaurantId={}", restaurantId);
            return new ArrayList<>();
        }

        // Convert projections to DTOs with null-safe handling
        // Already sorted by orderCount DESC from query
        List<PeakHourDto> result = projections.stream()
                .filter(p -> p != null && p.getHour() != null)
                .map(p -> new PeakHourDto(
                        p.getHour(),
                        p.getOrderCount() != null ? p.getOrderCount() : 0L,
                        p.getRevenue() != null ? p.getRevenue() : BigDecimal.ZERO
                ))
                .toList();

        log.debug("Peak hours returned {} hours | restaurantId={}", result.size(), restaurantId);

        return result;
    }

    // ==================== Session Analytics ====================

    /**
     * Query comprehensive session analytics for the owner dashboard.
     */
    private SessionAnalyticsDto querySessionAnalytics(Long restaurantId, Instant startDate, Instant endDate) {
        log.debug("Querying session analytics | restaurantId={}", restaurantId);

        // Count totals
        Long totalSessions = sessionRepository.countSessionsByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        totalSessions = (totalSessions != null) ? totalSessions : 0L;

        Long openSessionsCount = sessionRepository.countOpenSessionsByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        openSessionsCount = (openSessionsCount != null) ? openSessionsCount : 0L;

        Long closedSessionsCount = sessionRepository.countClosedSessionsByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        closedSessionsCount = (closedSessionsCount != null) ? closedSessionsCount : 0L;

        // Duration statistics (closed sessions only)
        Double avgDuration = sessionRepository.avgSessionDurationByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        Integer avgSessionDurationMinutes = (avgDuration != null) ? (int) Math.round(avgDuration) : null;

        Integer longestSessionMinutes = sessionRepository.maxSessionDurationByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );

        Integer shortestSessionMinutes = sessionRepository.minSessionDurationByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );

        Long totalOccupiedMinutes = sessionRepository.sumSessionDurationByRestaurantAndDateRange(
                restaurantId, startDate, endDate
        );
        totalOccupiedMinutes = (totalOccupiedMinutes != null) ? totalOccupiedMinutes : 0L;

        // Top tables by sessions
        Pageable topN = PageRequest.of(0, TOP_TABLES_LIMIT);
        List<SessionTopTableDto> topTablesBySessions = sessionRepository.findTopTablesBySessionCount(
                restaurantId, startDate, endDate, topN
        );
        topTablesBySessions = (topTablesBySessions != null) ? topTablesBySessions : new ArrayList<>();

        // Peak session hours
        List<SessionPeakHourProjection> peakProjections = sessionRepository.findPeakSessionHours(
                restaurantId, startDate.getEpochSecond(), endDate.getEpochSecond()
        );
        List<SessionPeakHourDto> peakSessionHours;
        if (peakProjections == null || peakProjections.isEmpty()) {
            peakSessionHours = new ArrayList<>();
        } else {
            peakSessionHours = peakProjections.stream()
                    .filter(p -> p != null && p.getHour() != null)
                    .map(p -> new SessionPeakHourDto(
                            p.getHour(),
                            p.getSessionCount() != null ? p.getSessionCount() : 0L
                    ))
                    .toList();
        }

        // Live snapshot: currently open tables
        List<TableSession> openSessions = sessionRepository.findCurrentlyOpenSessions(restaurantId);
        Instant now = Instant.now();
        List<OpenTableInfoDto> currentOpenTables;
        if (openSessions == null || openSessions.isEmpty()) {
            currentOpenTables = new ArrayList<>();
        } else {
            currentOpenTables = openSessions.stream()
                    .map(s -> new OpenTableInfoDto(
                            s.getTable().getId(),
                            s.getTable().getTableNumber(),
                            s.getOpenedAt(),
                            java.time.Duration.between(s.getOpenedAt(), now).toMinutes()
                    ))
                    .toList();
        }

        log.debug("Session analytics | totalSessions={} | open={} | closed={} | avgDuration={} | totalOccupied={}",
                totalSessions, openSessionsCount, closedSessionsCount, avgSessionDurationMinutes, totalOccupiedMinutes);

        return new SessionAnalyticsDto(
                totalSessions,
                openSessionsCount,
                closedSessionsCount,
                avgSessionDurationMinutes,
                longestSessionMinutes,
                shortestSessionMinutes,
                totalOccupiedMinutes,
                topTablesBySessions,
                peakSessionHours,
                currentOpenTables
        );
    }
}

