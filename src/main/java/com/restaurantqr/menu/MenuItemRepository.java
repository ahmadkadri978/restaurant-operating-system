package com.restaurantqr.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurantIdAndIsActiveTrueAndIsAvailableTrueOrderByDisplayOrderAsc(Long restaurantId);

    List<MenuItem> findByIdInAndRestaurantIdAndIsActiveTrue(List<Long> ids, Long restaurantId);

    // ✅ Public menu load: fetch category to avoid N+1
    @Query("""
        select mi
        from MenuItem mi
        join fetch mi.category c
        where mi.restaurant.id = :restaurantId
          and mi.isActive = true
          and mi.isAvailable = true
          and c.isActive = true
        order by
          case when mi.displayOrder is null then 1 else 0 end,
          mi.displayOrder asc,
          mi.id asc
    """)
    List<MenuItem> findActiveAvailableMenuForRestaurantFetchCategory(
            @Param("restaurantId") Long restaurantId
    );

    // ✅ Submit order flow: you already use this, keep it.
    // (Optional improvement): also ensure category active + item available if required by business
    @Query("""
        select mi
        from MenuItem mi
        where mi.id in :ids
          and mi.restaurant.id = :restaurantId
          and mi.isActive = true
    """)
    List<MenuItem> findActiveByIdsAndRestaurant(
            @Param("ids") List<Long> ids,
            @Param("restaurantId") Long restaurantId
    );

    /**
     * Find all items for a category (including inactive)
     */
    @Query("""
        select mi
        from MenuItem mi
        where mi.category.id = :categoryId
        order by
          case when mi.displayOrder is null then 1 else 0 end,
          mi.displayOrder asc,
          mi.id asc
    """)
    List<MenuItem> findAllByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * Count items for a restaurant
     */
    @Query("SELECT COUNT(mi) FROM MenuItem mi WHERE mi.restaurant.id = :restaurantId")
    int countByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Delete all items for a restaurant
     */
    @Modifying
    @Query("DELETE FROM MenuItem mi WHERE mi.restaurant.id = :restaurantId")
    void deleteByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Soft delete all items for a restaurant
     */
    @Modifying
    @Query("UPDATE MenuItem mi SET mi.isActive = false WHERE mi.restaurant.id = :restaurantId")
    void deactivateByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Delete all items for a category
     */
    @Modifying
    @Query("DELETE FROM MenuItem mi WHERE mi.category.id = :categoryId")
    void deleteByCategoryId(@Param("categoryId") Long categoryId);
}