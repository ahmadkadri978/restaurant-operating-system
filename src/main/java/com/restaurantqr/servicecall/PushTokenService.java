package com.restaurantqr.servicecall;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final StaffPushTokenRepository repo;



    public void saveToken(Long restaurantId, Long userId, String token) {
        if (repo.existsByUserIdAndToken(userId, token)) {
            log.info("Push token already exists. userId={}, restaurantId={}", userId, restaurantId);
            return;
        }

        StaffPushToken entity = new StaffPushToken();
        entity.setRestaurantId(restaurantId);
        entity.setUserId(userId);
        entity.setToken(token);

        repo.save(entity);
        log.info("Push token saved. userId={}, restaurantId={}", userId, restaurantId);
    }

    public void removeToken(Long userId, String token) {
        repo.deleteByUserIdAndToken(userId, token);
        log.info("Push token removed. userId={}", userId);
    }

    public List<String> tokensForRestaurant(Long restaurantId) {
        return repo.findTokensByRestaurantId(restaurantId);
    }
}

