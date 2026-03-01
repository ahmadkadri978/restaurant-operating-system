package com.restaurantqr.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
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

}
