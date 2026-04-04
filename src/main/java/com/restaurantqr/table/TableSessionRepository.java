package com.restaurantqr.table;

import com.restaurantqr.owner.SessionPeakHourProjection;
import com.restaurantqr.owner.SessionTopTableDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    /**
     * Find an open session for a specific table (closedAt IS NULL).
     */
    @Query("""
        select s
        from TableSession s
        where s.table.id = :tableId
          and s.closedAt is null
    """)
    Optional<TableSession> findOpenSessionByTableId(@Param("tableId") Long tableId);

    // ==================== OWNER DASHBOARD QUERIES ====================

    /**
     * Count total sessions (opened) in date range.
     */
    @Query("""
        select count(s)
        from TableSession s
        where s.restaurant.id = :restaurantId
          and s.openedAt >= :startDate
          and s.openedAt < :endDate
    """)
    Long countSessionsByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Count open (not yet closed) sessions in date range.
     */
    @Query("""
        select count(s)
        from TableSession s
        where s.restaurant.id = :restaurantId
          and s.openedAt >= :startDate
          and s.openedAt < :endDate
          and s.closedAt is null
    """)
    Long countOpenSessionsByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Count closed sessions in date range.
     */
    @Query("""
        select count(s)
        from TableSession s
        where s.restaurant.id = :restaurantId
          and s.openedAt >= :startDate
          and s.openedAt < :endDate
          and s.closedAt is not null
    """)
    Long countClosedSessionsByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Average duration (minutes) of closed sessions in date range.
     */
    @Query("""
        select avg(s.durationMinutes)
        from TableSession s
        where s.restaurant.id = :restaurantId
          and s.openedAt >= :startDate
          and s.openedAt < :endDate
          and s.closedAt is not null
          and s.durationMinutes is not null
    """)
    Double avgSessionDurationByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Longest closed session (minutes) in date range.
     */
    @Query("""
        select max(s.durationMinutes)
        from TableSession s
        where s.restaurant.id = :restaurantId
          and s.openedAt >= :startDate
          and s.openedAt < :endDate
          and s.closedAt is not null
          and s.durationMinutes is not null
    """)
    Integer maxSessionDurationByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Shortest closed session (minutes) in date range.
     */
    @Query("""
        select min(s.durationMinutes)
        from TableSession s
        where s.restaurant.id = :restaurantId
          and s.openedAt >= :startDate
          and s.openedAt < :endDate
          and s.closedAt is not null
          and s.durationMinutes is not null
    """)
    Integer minSessionDurationByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Sum of all closed session durations (minutes) in date range — total occupied time.
     */
    @Query("""
        select coalesce(sum(s.durationMinutes), 0)
        from TableSession s
        where s.restaurant.id = :restaurantId
          and s.openedAt >= :startDate
          and s.openedAt < :endDate
          and s.closedAt is not null
          and s.durationMinutes is not null
    """)
    Long sumSessionDurationByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Top tables by session count in date range.
     */
    @Query("""
        select new com.restaurantqr.owner.SessionTopTableDto(
            s.table.id,
            s.table.tableNumber,
            count(s),
            coalesce(sum(s.durationMinutes), 0)
        )
        from TableSession s
        where s.restaurant.id = :restaurantId
          and s.openedAt >= :startDate
          and s.openedAt < :endDate
        group by s.table.id, s.table.tableNumber
        order by count(s) desc
    """)
    List<SessionTopTableDto> findTopTablesBySessionCount(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    /**
     * Peak hours by session open count.
     * Uses UNIX_TIMESTAMP to extract hour from opened_at.
     */
    @Query(value = """
        SELECT HOUR(opened_at) AS hour,
               COUNT(*)        AS sessionCount
        FROM table_sessions
        WHERE restaurant_id = :restaurantId
          AND UNIX_TIMESTAMP(opened_at) >= :startEpoch
          AND UNIX_TIMESTAMP(opened_at) <  :endEpoch
        GROUP BY HOUR(opened_at)
        ORDER BY sessionCount DESC
    """, nativeQuery = true)
    List<SessionPeakHourProjection> findPeakSessionHours(
            @Param("restaurantId") Long restaurantId,
            @Param("startEpoch") Long startEpoch,
            @Param("endEpoch") Long endEpoch
    );

    /**
     * Find all currently open sessions for a restaurant (live snapshot).
     */
    @Query("""
        select s
        from TableSession s
        join fetch s.table t
        where s.restaurant.id = :restaurantId
          and s.closedAt is null
        order by s.openedAt asc
    """)
    List<TableSession> findCurrentlyOpenSessions(@Param("restaurantId") Long restaurantId);
}

