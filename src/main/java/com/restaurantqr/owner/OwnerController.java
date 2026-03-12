package com.restaurantqr.owner;

import com.restaurantqr.auth.dto.AuthPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Owner - Dashboard", description = "Owner dashboard APIs (JWT required)")
@RestController
@RequestMapping("/api/v1/owner")
@PreAuthorize("hasRole('OWNER')")
public class OwnerController {

    @GetMapping("/health")
    public Map<String, Object> health(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return Map.of(
                "status", "OK",
                "message", "OWNER role is properly configured",
                "userId", principal.userId(),
                "role", principal.role(),
                "restaurantId", principal.restaurantId()
        );
    }
}

