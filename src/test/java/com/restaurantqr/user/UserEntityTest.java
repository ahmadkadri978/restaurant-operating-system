package com.restaurantqr.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

    @Test
    @DisplayName("onCreate sets createdAt and updatedAt to current time")
    void onCreate_setsTimestamps() {
        User user = new User();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();

        Instant before = Instant.now();
        user.onCreate();
        Instant after = Instant.now();

        assertThat(user.getCreatedAt()).isBetween(before, after);
        assertThat(user.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("onUpdate sets updatedAt to current time without changing createdAt")
    void onUpdate_setsOnlyUpdatedAt() {
        User user = new User();
        user.onCreate();
        Instant originalCreatedAt = user.getCreatedAt();

        user.onUpdate();

        assertThat(user.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(originalCreatedAt);
    }
}

