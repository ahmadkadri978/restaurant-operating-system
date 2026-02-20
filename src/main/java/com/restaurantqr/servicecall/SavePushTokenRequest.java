package com.restaurantqr.servicecall;



import jakarta.validation.constraints.NotBlank;

public record SavePushTokenRequest(
        @NotBlank String token
) {}
