package com.restaurantqr.servicecall;


import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final PushTokenService tokenService;



    public void notifyServiceCall(Long restaurantId, int tableNumber, String callTypeAr) {
        List<String> tokens = tokenService.tokensForRestaurant(restaurantId);
        if (tokens.isEmpty()) {
            log.info("No push tokens found for restaurantId={}", restaurantId);
            return;
        }

        String title = "نداء جديد 🔔";
        String body  = "طاولة " + tableNumber + " تطلب: " + callTypeAr;

        for (String token : tokens) {
            try {
                Message msg = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        // optional data payload (useful later)
                        .putData("event", "SERVICE_CALL")
                        .putData("tableNumber", String.valueOf(tableNumber))
                        .putData("callType", callTypeAr)
                        .build();

                FirebaseMessaging.getInstance().sendAsync(msg);
            } catch (Exception e) {
                log.warn("Failed to send push. token={} restaurantId={}", token, restaurantId, e);
            }
        }
    }
}

