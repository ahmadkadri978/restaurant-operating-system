package com.restaurantqr.user;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.user.dto.CreateUserRequest;

import java.util.Set;

public enum Role {

    SUPER_ADMIN(Set.of("MANAGER", "STAFF", "KITCHEN")) {
        @Override
        public Long resolveRestaurantId(CreateUserRequest request, AuthPrincipal principal) {
            if (request.restaurantId() == null) {
                throw new BusinessRuleException("SUPER_ADMIN must specify restaurantId");
            }
            return request.restaurantId();
        }
    },

    MANAGER(Set.of("STAFF", "KITCHEN")),
    STAFF(Set.of()),
    KITCHEN(Set.of());

    private final Set<String> creatableRoles;

    Role(Set<String> creatableRoles) {
        this.creatableRoles = creatableRoles;
    }

    public boolean canCreate(Role target) {
        return creatableRoles.contains(target.name());
    }

    public Long resolveRestaurantId(CreateUserRequest request, AuthPrincipal principal) {
        if (principal.restaurantId() == null) {
            throw new BusinessRuleException("Your account is not associated with a restaurant");
        }
        return principal.restaurantId();
    }
}
