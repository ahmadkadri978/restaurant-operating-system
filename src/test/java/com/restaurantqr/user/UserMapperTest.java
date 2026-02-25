package com.restaurantqr.user;

import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.user.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapperImpl();

    @Test
    @DisplayName("toResponse – maps all User fields correctly")
    void toResponse_mapsAllFields() {
        // given
        Restaurant restaurant = new Restaurant();
        restaurant.setId(5L);

        User user = new User();
        user.setId(42L);
        user.setUsername("testuser");
        user.setRole(Role.STAFF);
        user.setRestaurant(restaurant);
        user.setActive(true);
        user.setCreatedAt(Instant.parse("2026-01-15T10:00:00Z"));

        // when
        UserResponse response = mapper.toResponse(user);

        // then
        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.username()).isEqualTo("testuser");
        assertThat(response.role()).isEqualTo("STAFF");
        assertThat(response.restaurantId()).isEqualTo(5L);
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
    }

    @Test
    @DisplayName("toResponse – inactive user maps active=false")
    void toResponse_inactiveUser_mapsFalse() {
        // given
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);

        User user = new User();
        user.setId(10L);
        user.setUsername("disabled");
        user.setRole(Role.KITCHEN);
        user.setRestaurant(restaurant);
        user.setActive(false);
        user.setCreatedAt(Instant.now());

        // when
        UserResponse response = mapper.toResponse(user);

        // then
        assertThat(response.active()).isFalse();
        assertThat(response.role()).isEqualTo("KITCHEN");
    }

    @Test
    @DisplayName("toResponse – MANAGER role maps correctly")
    void toResponse_managerRole() {
        // given
        Restaurant restaurant = new Restaurant();
        restaurant.setId(3L);

        User user = new User();
        user.setId(7L);
        user.setUsername("mgr1");
        user.setRole(Role.MANAGER);
        user.setRestaurant(restaurant);
        user.setActive(true);
        user.setCreatedAt(Instant.now());

        // when
        UserResponse response = mapper.toResponse(user);

        // then
        assertThat(response.role()).isEqualTo("MANAGER");
        assertThat(response.restaurantId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("toResponse – null user returns null")
    void toResponse_nullUser_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    @DisplayName("toResponse – null restaurant (SUPER_ADMIN) maps restaurantId to null")
    void toResponse_nullRestaurant_mapsNullRestaurantId() {
        User user = new User();
        user.setId(1L);
        user.setUsername("superadmin");
        user.setRole(Role.SUPER_ADMIN);
        user.setRestaurant(null);
        user.setActive(true);
        user.setCreatedAt(Instant.parse("2026-01-15T10:00:00Z"));

        UserResponse response = mapper.toResponse(user);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("superadmin");
        assertThat(response.role()).isEqualTo("SUPER_ADMIN");
        assertThat(response.restaurantId()).isNull();
        assertThat(response.active()).isTrue();
    }
}

