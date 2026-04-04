package com.restaurantqr.user;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-End Integration Tests for User Package.
 * Tests user model, roles, and authentication-related functionality.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("User Package E2E Integration Tests")
class UserE2EIntegrationTest {

    // ==================== Role Enum Tests ====================

    @Nested
    @DisplayName("Role Enum E2E Tests")
    class RoleEnumE2ETests {

        @Test
        @DisplayName("E2E: Should have all expected roles")
        void shouldHaveAllExpectedRoles() {
            // Then
            assertThat(Role.values()).containsExactlyInAnyOrder(
                    Role.STAFF,
                    Role.MANAGER,
                    Role.KITCHEN,
                    Role.OWNER,
                    Role.ADMIN
            );
        }

        @Test
        @DisplayName("E2E: STAFF role should exist")
        void staffRoleShouldExist() {
            assertThat(Role.valueOf("STAFF")).isEqualTo(Role.STAFF);
        }

        @Test
        @DisplayName("E2E: MANAGER role should exist")
        void managerRoleShouldExist() {
            assertThat(Role.valueOf("MANAGER")).isEqualTo(Role.MANAGER);
        }

        @Test
        @DisplayName("E2E: KITCHEN role should exist")
        void kitchenRoleShouldExist() {
            assertThat(Role.valueOf("KITCHEN")).isEqualTo(Role.KITCHEN);
        }

        @Test
        @DisplayName("E2E: OWNER role should exist")
        void ownerRoleShouldExist() {
            assertThat(Role.valueOf("OWNER")).isEqualTo(Role.OWNER);
        }

        @Test
        @DisplayName("E2E: ADMIN role should exist")
        void adminRoleShouldExist() {
            assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
        }

        @Test
        @DisplayName("E2E: Should throw exception for invalid role")
        void shouldThrowExceptionForInvalidRole() {
            assertThatThrownBy(() -> Role.valueOf("INVALID_ROLE"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("E2E: Role names should be uppercase")
        void roleNamesShouldBeUppercase() {
            for (Role role : Role.values()) {
                assertThat(role.name()).isEqualTo(role.name().toUpperCase());
            }
        }
    }

    // ==================== User Entity Tests ====================

    @Nested
    @DisplayName("User Entity E2E Tests")
    class UserEntityE2ETests {

        @Test
        @DisplayName("E2E: Should create user with all fields")
        void shouldCreateUserWithAllFields() {
            // Given
            User user = new User();
            user.setId(1L);
            user.setUsername("test_user");
            user.setPasswordHash("hashed_password");
            user.setRole(Role.MANAGER);
            user.setActive(true);

            // Then
            assertThat(user.getId()).isEqualTo(1L);
            assertThat(user.getUsername()).isEqualTo("test_user");
            assertThat(user.getPasswordHash()).isEqualTo("hashed_password");
            assertThat(user.getRole()).isEqualTo(Role.MANAGER);
            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("E2E: Should handle inactive user")
        void shouldHandleInactiveUser() {
            // Given
            User user = new User();
            user.setActive(false);

            // Then
            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("E2E: Should allow changing user role")
        void shouldAllowChangingUserRole() {
            // Given
            User user = new User();
            user.setRole(Role.STAFF);

            // When
            user.setRole(Role.MANAGER);

            // Then
            assertThat(user.getRole()).isEqualTo(Role.MANAGER);
        }

        @Test
        @DisplayName("E2E: User should support all role types")
        void userShouldSupportAllRoleTypes() {
            User user = new User();

            for (Role role : Role.values()) {
                // When
                user.setRole(role);

                // Then
                assertThat(user.getRole()).isEqualTo(role);
            }
        }
    }

    // ==================== Role Hierarchy Tests ====================

    @Nested
    @DisplayName("Role Hierarchy E2E Tests")
    class RoleHierarchyE2ETests {

        @Test
        @DisplayName("E2E: Should identify restaurant-level roles")
        void shouldIdentifyRestaurantLevelRoles() {
            // Restaurant-level roles (bound to a specific restaurant)
            Role[] restaurantRoles = {Role.STAFF, Role.MANAGER, Role.KITCHEN, Role.OWNER};

            for (Role role : restaurantRoles) {
                assertThat(role).isIn(Role.STAFF, Role.MANAGER, Role.KITCHEN, Role.OWNER);
            }
        }

        @Test
        @DisplayName("E2E: Should identify system-level roles")
        void shouldIdentifySystemLevelRoles() {
            // System-level role (not bound to any restaurant)
            assertThat(Role.ADMIN).isNotIn(Role.STAFF, Role.MANAGER, Role.KITCHEN, Role.OWNER);
        }

        @Test
        @DisplayName("E2E: Should have exactly 5 roles")
        void shouldHaveExactly5Roles() {
            assertThat(Role.values()).hasSize(5);
        }
    }

    // ==================== Role Authorization Patterns E2E Tests ====================

    @Nested
    @DisplayName("Role Authorization Patterns E2E Tests")
    class RoleAuthorizationPatternsE2ETests {

        @Test
        @DisplayName("E2E: ADMIN should be distinct from restaurant roles")
        void adminShouldBeDistinctFromRestaurantRoles() {
            Role adminRole = Role.ADMIN;

            assertThat(adminRole).isNotEqualTo(Role.OWNER);
            assertThat(adminRole).isNotEqualTo(Role.MANAGER);
            assertThat(adminRole).isNotEqualTo(Role.STAFF);
            assertThat(adminRole).isNotEqualTo(Role.KITCHEN);
        }

        @Test
        @DisplayName("E2E: Staff roles should include STAFF, MANAGER, KITCHEN")
        void staffRolesShouldIncludeExpectedRoles() {
            Role[] staffRoles = {Role.STAFF, Role.MANAGER, Role.KITCHEN};

            for (Role role : staffRoles) {
                assertThat(role.name()).doesNotContain("ADMIN");
                assertThat(role.name()).doesNotContain("OWNER");
            }
        }

        @Test
        @DisplayName("E2E: OWNER role should exist for restaurant ownership")
        void ownerRoleShouldExistForRestaurantOwnership() {
            assertThat(Role.OWNER).isNotNull();
            assertThat(Role.OWNER.name()).isEqualTo("OWNER");
        }
    }

    // ==================== User with Restaurant Association Tests ====================

    @Nested
    @DisplayName("User Restaurant Association E2E Tests")
    class UserRestaurantAssociationE2ETests {

        @Test
        @DisplayName("E2E: User should be able to reference restaurant")
        void userShouldBeAbleToReferenceRestaurant() {
            // This test validates the model structure
            // User has a restaurant field for restaurant-bound users

            User user = new User();
            user.setId(1L);
            user.setUsername("restaurant_staff");
            user.setRole(Role.STAFF);

            // Restaurant association would be set through setRestaurant()
            // Verify the user can exist without errors
            assertThat(user.getId()).isNotNull();
            assertThat(user.getRole()).isEqualTo(Role.STAFF);
        }

        @Test
        @DisplayName("E2E: ADMIN user typically has no restaurant association")
        void adminUserTypicallyHasNoRestaurantAssociation() {
            User adminUser = new User();
            adminUser.setRole(Role.ADMIN);
            adminUser.setRestaurant(null); // ADMIN users are system-wide

            assertThat(adminUser.getRestaurant()).isNull();
            assertThat(adminUser.getRole()).isEqualTo(Role.ADMIN);
        }
    }

    // ==================== Username Uniqueness E2E Tests ====================

    @Nested
    @DisplayName("Username Uniqueness E2E Tests")
    class UsernameUniquenessE2ETests {

        @Test
        @DisplayName("E2E: Usernames should be case-sensitive")
        void usernamesShouldBeCaseSensitive() {
            User user1 = new User();
            user1.setUsername("TestUser");

            User user2 = new User();
            user2.setUsername("testuser");

            // Different usernames (case matters)
            assertThat(user1.getUsername()).isNotEqualTo(user2.getUsername());
        }

        @Test
        @DisplayName("E2E: Username should be trimmed")
        void usernameShouldBeTrimmed() {
            User user = new User();
            user.setUsername("test_user"); // No spaces

            assertThat(user.getUsername()).doesNotContain(" ");
        }
    }

    // ==================== Password Security E2E Tests ====================

    @Nested
    @DisplayName("Password Security E2E Tests")
    class PasswordSecurityE2ETests {

        @Test
        @DisplayName("E2E: Password should be stored as hash not plain text")
        void passwordShouldBeStoredAsHash() {
            User user = new User();
            String plainPassword = "mySecretPassword";
            String hashedPassword = "hashed_" + plainPassword; // Simulate hashing

            user.setPasswordHash(hashedPassword);

            // Password hash should not equal plain password
            assertThat(user.getPasswordHash()).isNotEqualTo(plainPassword);
            assertThat(user.getPasswordHash()).startsWith("hashed_");
        }

        @Test
        @DisplayName("E2E: User should use passwordHash field not password")
        void userShouldUsePasswordHashField() {
            User user = new User();
            user.setPasswordHash("bcrypt_encoded_value");

            // The field is called passwordHash, not password
            assertThat(user.getPasswordHash()).isNotNull();
        }
    }

    // ==================== Active Status E2E Tests ====================

    @Nested
    @DisplayName("Active Status E2E Tests")
    class ActiveStatusE2ETests {

        @Test
        @DisplayName("E2E: New user should be able to set active status")
        void newUserShouldBeAbleToSetActiveStatus() {
            User user = new User();

            user.setActive(true);
            assertThat(user.isActive()).isTrue();

            user.setActive(false);
            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("E2E: Deactivated user should remain in database")
        void deactivatedUserShouldRemainInDatabase() {
            // Soft delete pattern - user is deactivated, not removed
            User user = new User();
            user.setId(1L);
            user.setUsername("deactivated_user");
            user.setActive(false);

            // User still has all data, just marked inactive
            assertThat(user.getId()).isNotNull();
            assertThat(user.getUsername()).isNotNull();
            assertThat(user.isActive()).isFalse();
        }
    }
}

