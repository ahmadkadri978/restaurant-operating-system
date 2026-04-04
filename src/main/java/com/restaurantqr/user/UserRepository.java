package com.restaurantqr.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByUsername(String username);
    
    boolean existsByUsername(String username);
    
    boolean existsByUsernameIn(Collection<String> usernames);

    /**
     * Check if username exists for a different user (excluding the one with excludeId)
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username AND u.id != :excludeId")
    boolean existsByUsernameAndIdNot(@Param("username") String username, @Param("excludeId") Long excludeId);

    /**
     * Find all users for a restaurant
     */
    List<User> findByRestaurantIdOrderByRoleAsc(Long restaurantId);

    /**
     * Count users for a restaurant
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.restaurant.id = :restaurantId")
    int countByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Delete all users for a restaurant
     */
    @Modifying
    @Query("DELETE FROM User u WHERE u.restaurant.id = :restaurantId")
    void deleteByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Soft delete all users for a restaurant
     */
    @Modifying
    @Query("UPDATE User u SET u.isActive = false WHERE u.restaurant.id = :restaurantId")
    void deactivateByRestaurantId(@Param("restaurantId") Long restaurantId);
}
