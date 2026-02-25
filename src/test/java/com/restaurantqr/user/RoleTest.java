package com.restaurantqr.user;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.user.dto.CreateUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    // ── canCreate permissions ──

    @Nested
    @DisplayName("SUPER_ADMIN permissions")
    class SuperAdminPermissions {

        @Test
        void canCreateManager() {
            assertThat(Role.SUPER_ADMIN.canCreate(Role.MANAGER)).isTrue();
        }

        @Test
        void canCreateStaff() {
            assertThat(Role.SUPER_ADMIN.canCreate(Role.STAFF)).isTrue();
        }

        @Test
        void canCreateKitchen() {
            assertThat(Role.SUPER_ADMIN.canCreate(Role.KITCHEN)).isTrue();
        }

        @Test
        void cannotCreateSuperAdmin() {
            assertThat(Role.SUPER_ADMIN.canCreate(Role.SUPER_ADMIN)).isFalse();
        }
    }

    @Nested
    @DisplayName("MANAGER permissions")
    class ManagerPermissions {

        @Test
        void canCreateStaff() {
            assertThat(Role.MANAGER.canCreate(Role.STAFF)).isTrue();
        }

        @Test
        void canCreateKitchen() {
            assertThat(Role.MANAGER.canCreate(Role.KITCHEN)).isTrue();
        }

        @Test
        void cannotCreateManager() {
            assertThat(Role.MANAGER.canCreate(Role.MANAGER)).isFalse();
        }

        @Test
        void cannotCreateSuperAdmin() {
            assertThat(Role.MANAGER.canCreate(Role.SUPER_ADMIN)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("STAFF cannot create any role")
    void staff_cannotCreateAnyRole(Role target) {
        assertThat(Role.STAFF.canCreate(target)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("KITCHEN cannot create any role")
    void kitchen_cannotCreateAnyRole(Role target) {
        assertThat(Role.KITCHEN.canCreate(target)).isFalse();
    }

    // ── resolveRestaurantId ──

    @Nested
    @DisplayName("resolveRestaurantId")
    class ResolveRestaurantId {

        @Test
        @DisplayName("SUPER_ADMIN – uses restaurantId from request")
        void superAdmin_usesRequestRestaurantId() {
            var request = new CreateUserRequest("u", "pass12", Role.STAFF, 42L);
            var principal = new AuthPrincipal(1L, "SUPER_ADMIN", null);

            assertThat(Role.SUPER_ADMIN.resolveRestaurantId(request, principal)).isEqualTo(42L);
        }

        @Test
        @DisplayName("SUPER_ADMIN – null restaurantId in request – throws")
        void superAdmin_nullRestaurantId_throws() {
            var request = new CreateUserRequest("u", "pass12", Role.STAFF, null);
            var principal = new AuthPrincipal(1L, "SUPER_ADMIN", null);

            assertThatThrownBy(() -> Role.SUPER_ADMIN.resolveRestaurantId(request, principal))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("must specify restaurantId");
        }

        @Test
        @DisplayName("MANAGER – uses restaurantId from JWT principal")
        void manager_usesPrincipalRestaurantId() {
            var request = new CreateUserRequest("u", "pass12", Role.STAFF, 999L);
            var principal = new AuthPrincipal(10L, "MANAGER", 5L);

            assertThat(Role.MANAGER.resolveRestaurantId(request, principal)).isEqualTo(5L);
        }

        @Test
        @DisplayName("MANAGER – null restaurantId in principal – throws")
        void manager_nullPrincipalRestaurantId_throws() {
            var request = new CreateUserRequest("u", "pass12", Role.STAFF, null);
            var principal = new AuthPrincipal(10L, "MANAGER", null);

            assertThatThrownBy(() -> Role.MANAGER.resolveRestaurantId(request, principal))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not associated with a restaurant");
        }

        @Test
        @DisplayName("STAFF – uses restaurantId from JWT principal")
        void staff_usesPrincipalRestaurantId() {
            var request = new CreateUserRequest("u", "pass12", Role.STAFF, null);
            var principal = new AuthPrincipal(20L, "STAFF", 7L);

            assertThat(Role.STAFF.resolveRestaurantId(request, principal)).isEqualTo(7L);
        }

        @Test
        @DisplayName("KITCHEN – uses restaurantId from JWT principal")
        void kitchen_usesPrincipalRestaurantId() {
            var request = new CreateUserRequest("u", "pass12", Role.STAFF, null);
            var principal = new AuthPrincipal(30L, "KITCHEN", 7L);

            assertThat(Role.KITCHEN.resolveRestaurantId(request, principal)).isEqualTo(7L);
        }
    }
}

