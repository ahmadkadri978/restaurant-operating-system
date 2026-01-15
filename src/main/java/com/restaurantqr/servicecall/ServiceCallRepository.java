package com.restaurantqr.servicecall;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface ServiceCallRepository extends JpaRepository<ServiceCall, Long> {

    // الوضع الحالي (initial load)
    List<ServiceCall> findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtDesc(
            Long restaurantId,
            Instant threshold
    );

    // منع spam (موجود عندك)
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
}

