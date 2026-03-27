package com.restaurantqr.servicecall;


import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushService {

    private final PushTokenService tokenService;



    public void notifyServiceCall(Long restaurantId, Long callId, int tableNumber, String callTypeAr) {
        List<String> tokens = tokenService.tokensForRestaurant(restaurantId);
        if (tokens.isEmpty()) {
            // This is expected if staff haven't enabled push notifications
            // SSE is the primary notification mechanism, FCM is optional fallback
            log.debug("No push tokens found for restaurantId={} (staff will receive via SSE instead)", restaurantId);
            return;
        }

        String title = "نداء جديد 🔔";
        String body  = "طاولة " + tableNumber + " تطلب: " + callTypeAr;

        for (String token : tokens) {
            try {
                Message msg = Message.builder()
                        .setToken(token)
                        .putData("event", "SERVICE_CALL")
                        .putData("callId", String.valueOf(callId))
                        .putData("restaurantId", String.valueOf(restaurantId))
                        .putData("tableNumber", String.valueOf(tableNumber))
                        .putData("callType", callTypeAr)
                        .putData("title", title)
                        .putData("body", body)
                        .setWebpushConfig(WebpushConfig.builder()
                                .putHeader("Urgency", "high")
                                .putHeader("TTL", "60")
                                .build())
                        .build();

                String messageId = FirebaseMessaging.getInstance().send(msg);
                log.info("Push sent successfully. restaurantId={}, token={}, messageId={}",
                        restaurantId, token, messageId);

                FirebaseMessaging.getInstance().sendAsync(msg);
            } catch (Exception e) {
                log.warn("Failed to send push. token={} restaurantId={}", token, restaurantId, e);
            }
        }
    }
}


