package com.restaurantqr.table;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable,Long> {
    Optional<RestaurantTable> findByQrTokenAndIsActiveTrue(String qrToken);
    boolean existsByQrToken(String qrToken);

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
}
