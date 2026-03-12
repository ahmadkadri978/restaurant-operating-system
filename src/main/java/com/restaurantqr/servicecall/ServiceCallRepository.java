package com.restaurantqr.servicecall;

import com.restaurantqr.owner.ServiceCallTypeCountDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ServiceCallRepository extends JpaRepository<ServiceCall, Long> {

    List<ServiceCall> findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long restaurantId,
            Instant threshold
    );

    boolean existsByTableIdAndCreatedAtAfter(Long tableId, Instant threshold);

    //  Polling: get new service calls
    @Query("""
        select c
        from ServiceCall c
        where c.restaurant.id = :restaurantId
          and c.createdAt > :since
          and c.createdAt >= :activeSince
        order by c.createdAt asc
    """)
    List<ServiceCall> findNewActiveCalls(
            Long restaurantId,
            Instant since,
            Instant activeSince
    );

    // ==================== OWNER DASHBOARD QUERIES ====================

    /**
     * Count service calls in date range for restaurant.
     */
    @Query("""
        select count(sc)
        from ServiceCall sc
        where sc.restaurant.id = :restaurantId
          and sc.createdAt >= :startDate
          and sc.createdAt < :endDate
    """)
    Long countServiceCallsByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Get service call counts grouped by call type.
     */
    @Query("""
        select new com.restaurantqr.owner.ServiceCallTypeCountDto(sc.callType, count(sc))
        from ServiceCall sc
        where sc.restaurant.id = :restaurantId
          and sc.createdAt >= :startDate
          and sc.createdAt < :endDate
        group by sc.callType
        order by count(sc) desc
    """)
    List<ServiceCallTypeCountDto> countServiceCallsGroupedByType(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}



