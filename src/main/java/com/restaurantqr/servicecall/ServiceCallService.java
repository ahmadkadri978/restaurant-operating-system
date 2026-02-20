package com.restaurantqr.servicecall;


import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.RateLimitException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.management.Notification;
import java.time.Instant;
import java.util.List;

@Service
public class ServiceCallService {

    private static final Logger log = LoggerFactory.getLogger(ServiceCallService.class);

    private final RestaurantTableRepository tableRepository;
    private final ServiceCallRepository serviceCallRepository;
    private final  FcmPushService fcmPushService;

    public ServiceCallService(RestaurantTableRepository tableRepository,
                              ServiceCallRepository serviceCallRepository, FcmPushService fcmPushService) {
        this.tableRepository = tableRepository;
        this.serviceCallRepository = serviceCallRepository;
        this.fcmPushService = fcmPushService;
    }

    @Transactional
    public ServiceCallResponse createCall(String qrToken, CreateServiceCallRequest request) {
        log.info("Create service call request. qrToken={}, callType={}", qrToken, request.callType());

        // ✅ lock table row to avoid spam race condition
        RestaurantTable table = tableRepository.findActiveByQrTokenForUpdate(qrToken)
                .orElseThrow(() -> {
                    log.warn("Invalid/inactive QR token for service call. qrToken={}", qrToken);
                    return new ResourceNotFoundException("Invalid or inactive QR token");
                });

        var restaurant = table.getRestaurant();
        if (!restaurant.isActive()) {
            log.warn("Restaurant inactive for service call. restaurantId={}, qrToken={}", restaurant.getId(), qrToken);
            throw new BusinessRuleException("Restaurant is inactive");
        }

        // Anti-spam cool-down- the customer should wait until the previous request expires before sending new one
        int cooldownSeconds = 180;
        Instant threshold = Instant.now().minusSeconds(cooldownSeconds);

        boolean spam = serviceCallRepository.existsByTableIdAndCreatedAtAfter(table.getId(), threshold);
        if (spam) {
            log.warn("ServiceCall spam blocked. tableId={}, cooldownSeconds={}", table.getId(), cooldownSeconds);
            throw new RateLimitException("انتظر ثلاث دقائق لارسال طلب خدمة جديد.");
        }

        ServiceCall call = new ServiceCall();
        call.setRestaurant(restaurant);
        call.setTable(table);
        call.setCallType(request.callType());

        ServiceCall saved = serviceCallRepository.save(call);

        log.info("Service call created. id={}, restaurantId={}, tableId={}",
                saved.getId(), restaurant.getId(), table.getId());

        String callTypeAr = switch (saved.getCallType()) {
            case WAITER -> "نادل";
            case BILL   -> "حساب";
            case NARA   -> "نارا";
            default     -> "نداء";
        };
        fcmPushService.notifyServiceCall(restaurant.getId(), table.getTableNumber(), callTypeAr);



        return new ServiceCallResponse(
                saved.getId(),
                table.getTableNumber(),
                saved.getCallType(),
                saved.getCreatedAt()
        );
    }


    @Transactional(readOnly = true)
    public List<ServiceCallResponse> getActiveCalls(Long restaurantId, Instant since) {

        Instant activeSince = Instant.now().minusSeconds(180); // 3 minutes
        log.info("Get active service calls. restaurantId={}, since={}", restaurantId, since);

        List<ServiceCall> calls;

        // Polling mode
        if (since != null) {
            calls = serviceCallRepository.findNewActiveCalls(
                    restaurantId,
                    since,
                    activeSince
            );
        }
        // Initial load
        else {
            calls = serviceCallRepository
                    .findByRestaurantIdAndCreatedAtAfterOrderByCreatedAtDesc(
                            restaurantId,
                            activeSince
                    );
        }

        return calls.stream()
                .map(c -> new ServiceCallResponse(
                        c.getId(),
                        c.getTable().getTableNumber(),
                        c.getCallType(),
                        c.getCreatedAt()
                ))
                .toList();
    }

}

