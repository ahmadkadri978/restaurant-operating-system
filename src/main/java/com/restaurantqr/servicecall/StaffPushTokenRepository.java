package com.restaurantqr.servicecall;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffPushTokenRepository extends JpaRepository<StaffPushToken, Long> {

    boolean existsByUserIdAndToken(Long userId, String token);

    void deleteByUserIdAndToken(Long userId, String token);

    // جلب التوكنات الخاصة بمطعم معين
    @org.springframework.data.jpa.repository.Query("""
        select s.token
        from StaffPushToken s
        where s.restaurantId = :restaurantId
    """)
    java.util.List<String> findTokensByRestaurantId(Long restaurantId);
}
