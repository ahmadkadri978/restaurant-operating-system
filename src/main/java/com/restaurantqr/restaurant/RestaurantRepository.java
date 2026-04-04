package com.restaurantqr.restaurant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    boolean existsByCode(String code);

    /**
     * Check if another restaurant with the given code exists (excluding the one with excludeId)
     */
    @Query("SELECT COUNT(r) > 0 FROM Restaurant r WHERE r.code = :code AND r.id != :excludeId")
    boolean existsByCodeAndIdNot(@Param("code") String code, @Param("excludeId") Long excludeId);

    /**
     * Find all restaurants with optional search filter
     */
    @Query("""
        SELECT r FROM Restaurant r
        WHERE (:search IS NULL OR :search = ''
               OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(r.code) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY r.createdAt DESC
    """)
    Page<Restaurant> findAllWithSearch(@Param("search") String search, Pageable pageable);

    /**
     * Find all active restaurants with optional search filter
     */
    @Query("""
        SELECT r FROM Restaurant r
        WHERE r.isActive = true
        AND (:search IS NULL OR :search = ''
             OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(r.code) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY r.createdAt DESC
    """)
    Page<Restaurant> findActiveWithSearch(@Param("search") String search, Pageable pageable);

    /**
     * Find restaurant by code
     */
    Optional<Restaurant> findByCode(String code);
}
