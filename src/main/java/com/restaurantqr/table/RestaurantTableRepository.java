package com.restaurantqr.table;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    Optional<RestaurantTable> findByQrTokenAndIsActiveTrue(String qrToken);

    Optional<RestaurantTable> findByQrToken(String qrToken);

    boolean existsByQrToken(String qrToken);

    List<RestaurantTable> findByRestaurantIdOrderByTableNumberAsc(Long restaurantId);

    boolean existsByRestaurantIdAndSessionCode(Long restaurantId, String sessionCode);

    // ✅ READ path (menu / verify): fetch restaurant without locks -> avoids contention
    @Query("""
        select t
        from RestaurantTable t
        join fetch t.restaurant r
        where t.qrToken = :qrToken
          and t.isActive = true
    """)
    Optional<RestaurantTable> findActiveByQrTokenFetchRestaurant(@Param("qrToken") String qrToken);

    // ✅ WRITE path (submit order lock / open-close session if needed)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("""
        select t
        from RestaurantTable t
        join fetch t.restaurant r
        where t.qrToken = :qrToken
          and t.isActive = true
    """)
    Optional<RestaurantTable> findActiveByQrTokenForUpdate(@Param("qrToken") String qrToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select t
        from RestaurantTable t
        join fetch t.restaurant r
        where t.qrToken = :qrToken
          and t.isActive = true
    """)
    Optional<RestaurantTable> findByQrTokenForUpdate(@Param("qrToken") String qrToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> findByIdForUpdate(@Param("id") Long id);

    // ==================== OWNER DASHBOARD QUERIES ====================

    /**
     * Count active tables for restaurant.
     */
    @Query("""
        select count(t)
        from RestaurantTable t
        where t.restaurant.id = :restaurantId
          and t.isActive = true
    """)
    Long countActiveTablesByRestaurant(@Param("restaurantId") Long restaurantId);

    // ==================== ADMIN QUERIES ====================

    /**
     * Count all tables for a restaurant
     */
    @Query("SELECT COUNT(t) FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId")
    int countByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Delete all tables for a restaurant
     */
    @Modifying
    @Query("DELETE FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId")
    void deleteByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Soft delete all tables for a restaurant
     */
    @Modifying
    @Query("UPDATE RestaurantTable t SET t.isActive = false WHERE t.restaurant.id = :restaurantId")
    void deactivateByRestaurantId(@Param("restaurantId") Long restaurantId);
}
