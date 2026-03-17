package com.restaurantqr.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    // ✅ Stable ordering even if displayOrder is null
    @Query("""
        select c
        from MenuCategory c
        where c.restaurant.id = :restaurantId
          and c.isActive = true
        order by 
          case when c.displayOrder is null then 1 else 0 end,
          c.displayOrder asc,
          c.id asc
    """)
    List<MenuCategory> findActiveByRestaurantOrderByDisplayOrder(
            @Param("restaurantId") Long restaurantId
    );

    /**
     * Find all categories for a restaurant (including inactive)
     */
    @Query("""
        select c
        from MenuCategory c
        where c.restaurant.id = :restaurantId
        order by 
          case when c.displayOrder is null then 1 else 0 end,
          c.displayOrder asc,
          c.id asc
    """)
    List<MenuCategory> findAllByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Count categories for a restaurant
     */
    @Query("SELECT COUNT(c) FROM MenuCategory c WHERE c.restaurant.id = :restaurantId")
    int countByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Delete all categories for a restaurant
     */
    @Modifying
    @Query("DELETE FROM MenuCategory c WHERE c.restaurant.id = :restaurantId")
    void deleteByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Soft delete all categories for a restaurant
     */
    @Modifying
    @Query("UPDATE MenuCategory c SET c.isActive = false WHERE c.restaurant.id = :restaurantId")
    void deactivateByRestaurantId(@Param("restaurantId") Long restaurantId);
}
