package com.restaurantqr.user;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ForbiddenOperationException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.user.dto.CreateUserRequest;
import com.restaurantqr.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private Restaurant restaurant;
    private UserResponse dummyResponse;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        dummyResponse = new UserResponse(99L, "newstaff", "STAFF", 1L, true, Instant.now());
    }

    private void stubHappyPath(String username, String rawPassword, String encodedPassword) {
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });
        when(userMapper.toResponse(any(User.class))).thenReturn(dummyResponse);
    }

    // ───────────────────────────────────────────────────────
    //  MANAGER tests
    // ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("MANAGER creates users")
    class ManagerCreatesUsers {

        private final AuthPrincipal managerPrincipal = new AuthPrincipal(10L, "MANAGER", 1L);

        @Test
        @DisplayName("happy path – STAFF – returns response and saves correctly")
        void createStaff_happyPath() {
            CreateUserRequest request = new CreateUserRequest("newstaff", "secret123", Role.STAFF, null);
            stubHappyPath("newstaff", "secret123", "$2a$encoded_hash");

            UserResponse response = userService.createUser(request, managerPrincipal);

            assertThat(response).isSameAs(dummyResponse);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo("newstaff");
            assertThat(saved.getPasswordHash()).isEqualTo("$2a$encoded_hash");
            assertThat(saved.getRole()).isEqualTo(Role.STAFF);
            assertThat(saved.getRestaurant()).isEqualTo(restaurant);
            assertThat(saved.isActive()).isTrue();
        }

        @Test
        @DisplayName("happy path – KITCHEN – saves correctly")
        void createKitchen_happyPath() {
            CreateUserRequest request = new CreateUserRequest("cook1", "password1", Role.KITCHEN, null);
            UserResponse kitchenResponse = new UserResponse(100L, "cook1", "KITCHEN", 1L, true, Instant.now());

            when(userRepository.existsByUsername("cook1")).thenReturn(false);
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(passwordEncoder.encode("password1")).thenReturn("$2a$kitchen_hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(100L);
                return u;
            });
            when(userMapper.toResponse(any(User.class))).thenReturn(kitchenResponse);

            UserResponse response = userService.createUser(request, managerPrincipal);

            assertThat(response.role()).isEqualTo("KITCHEN");
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(Role.KITCHEN);
        }

        @Test
        @DisplayName("MANAGER ignores restaurantId in request body – uses JWT value")
        void managerIgnoresRequestRestaurantId() {
            CreateUserRequest request = new CreateUserRequest("newstaff", "secret123", Role.STAFF, 999L);
            stubHappyPath("newstaff", "secret123", "$2a$encoded_hash");

            userService.createUser(request, managerPrincipal);

            // Should use restaurant 1 (from JWT), not 999 (from request)
            verify(restaurantRepository).findById(1L);
            verify(restaurantRepository, never()).findById(999L);
        }

        @Test
        @DisplayName("MANAGER cannot create MANAGER – throws ForbiddenOperationException")
        void managerCannotCreateManager() {
            CreateUserRequest request = new CreateUserRequest("mgr2", "password1", Role.MANAGER, null);

            assertThatThrownBy(() -> userService.createUser(request, managerPrincipal))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("MANAGER cannot create users with role MANAGER");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("MANAGER cannot create SUPER_ADMIN – throws ForbiddenOperationException")
        void managerCannotCreateSuperAdmin() {
            CreateUserRequest request = new CreateUserRequest("admin2", "password1", Role.SUPER_ADMIN, null);

            assertThatThrownBy(() -> userService.createUser(request, managerPrincipal))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("MANAGER cannot create users with role SUPER_ADMIN");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("username already taken – throws BusinessRuleException")
        void usernameTaken() {
            CreateUserRequest request = new CreateUserRequest("existing", "secret123", Role.STAFF, null);
            when(userRepository.existsByUsername("existing")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request, managerPrincipal))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already taken");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("concurrent duplicate username – propagates DataIntegrityViolationException")
        void concurrentDuplicate() {
            CreateUserRequest request = new CreateUserRequest("raceuser", "secret123", Role.STAFF, null);

            when(userRepository.existsByUsername("raceuser")).thenReturn(false);
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(passwordEncoder.encode("secret123")).thenReturn("$2a$encoded_hash");
            when(userRepository.save(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry 'raceuser'"));

            assertThatThrownBy(() -> userService.createUser(request, managerPrincipal))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("MANAGER with null restaurantId in principal – throws BusinessRuleException")
        void managerWithNullRestaurantId() {
            AuthPrincipal brokenPrincipal = new AuthPrincipal(10L, "MANAGER", null);
            CreateUserRequest request = new CreateUserRequest("newstaff", "secret123", Role.STAFF, null);

            assertThatThrownBy(() -> userService.createUser(request, brokenPrincipal))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not associated with a restaurant");

            verify(userRepository, never()).save(any());
        }
    }

    // ───────────────────────────────────────────────────────
    //  SUPER_ADMIN tests
    // ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("SUPER_ADMIN creates users")
    class SuperAdminCreatesUsers {

        private final AuthPrincipal superAdminPrincipal = new AuthPrincipal(1L, "SUPER_ADMIN", null);

        @Test
        @DisplayName("happy path – creates MANAGER for a restaurant")
        void createManager_happyPath() {
            CreateUserRequest request = new CreateUserRequest("mgr1", "password1", Role.MANAGER, 1L);
            UserResponse mgrResponse = new UserResponse(101L, "mgr1", "MANAGER", 1L, true, Instant.now());

            when(userRepository.existsByUsername("mgr1")).thenReturn(false);
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(passwordEncoder.encode("password1")).thenReturn("$2a$mgr_hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(101L);
                return u;
            });
            when(userMapper.toResponse(any(User.class))).thenReturn(mgrResponse);

            UserResponse response = userService.createUser(request, superAdminPrincipal);

            assertThat(response.role()).isEqualTo("MANAGER");
            assertThat(response.restaurantId()).isEqualTo(1L);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(Role.MANAGER);
            assertThat(captor.getValue().getRestaurant()).isEqualTo(restaurant);
        }

        @Test
        @DisplayName("happy path – creates STAFF for a restaurant")
        void createStaff_happyPath() {
            CreateUserRequest request = new CreateUserRequest("staff1", "password1", Role.STAFF, 1L);
            stubHappyPath("staff1", "password1", "$2a$staff_hash");

            UserResponse response = userService.createUser(request, superAdminPrincipal);

            assertThat(response).isSameAs(dummyResponse);
            verify(restaurantRepository).findById(1L);
        }

        @Test
        @DisplayName("happy path – creates KITCHEN for a restaurant")
        void createKitchen_happyPath() {
            CreateUserRequest request = new CreateUserRequest("cook1", "password1", Role.KITCHEN, 1L);
            UserResponse kitchenResponse = new UserResponse(102L, "cook1", "KITCHEN", 1L, true, Instant.now());

            when(userRepository.existsByUsername("cook1")).thenReturn(false);
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(passwordEncoder.encode("password1")).thenReturn("$2a$kitchen_hash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(102L);
                return u;
            });
            when(userMapper.toResponse(any(User.class))).thenReturn(kitchenResponse);

            UserResponse response = userService.createUser(request, superAdminPrincipal);

            assertThat(response.role()).isEqualTo("KITCHEN");
        }

        @Test
        @DisplayName("SUPER_ADMIN cannot create another SUPER_ADMIN – throws ForbiddenOperationException")
        void cannotCreateSuperAdmin() {
            CreateUserRequest request = new CreateUserRequest("admin2", "password1", Role.SUPER_ADMIN, 1L);

            assertThatThrownBy(() -> userService.createUser(request, superAdminPrincipal))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("SUPER_ADMIN cannot create users with role SUPER_ADMIN");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("SUPER_ADMIN without restaurantId – throws BusinessRuleException")
        void missingRestaurantId() {
            CreateUserRequest request = new CreateUserRequest("mgr1", "password1", Role.MANAGER, null);

            assertThatThrownBy(() -> userService.createUser(request, superAdminPrincipal))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("SUPER_ADMIN must specify restaurantId");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("SUPER_ADMIN with non-existent restaurantId – throws ResourceNotFoundException")
        void restaurantNotFound() {
            CreateUserRequest request = new CreateUserRequest("mgr1", "password1", Role.MANAGER, 999L);

            when(userRepository.existsByUsername("mgr1")).thenReturn(false);
            when(restaurantRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.createUser(request, superAdminPrincipal))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Restaurant with id 999 not found");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("username already taken – throws BusinessRuleException")
        void usernameTaken() {
            CreateUserRequest request = new CreateUserRequest("existing", "password1", Role.MANAGER, 1L);
            when(userRepository.existsByUsername("existing")).thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(request, superAdminPrincipal))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already taken");

            verify(userRepository, never()).save(any());
        }
    }

    // ───────────────────────────────────────────────────────
    //  Unauthorized role tests
    // ───────────────────────────────────────────────────────
    @Nested
    @DisplayName("Unauthorized roles")
    class UnauthorizedRoles {

        @Test
        @DisplayName("STAFF cannot create users – throws ForbiddenOperationException")
        void staffCannotCreateUsers() {
            AuthPrincipal staffPrincipal = new AuthPrincipal(20L, "STAFF", 1L);
            CreateUserRequest request = new CreateUserRequest("newuser", "password1", Role.STAFF, null);

            assertThatThrownBy(() -> userService.createUser(request, staffPrincipal))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("STAFF cannot create users with role STAFF");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("KITCHEN cannot create users – throws ForbiddenOperationException")
        void kitchenCannotCreateUsers() {
            AuthPrincipal kitchenPrincipal = new AuthPrincipal(30L, "KITCHEN", 1L);
            CreateUserRequest request = new CreateUserRequest("newuser", "password1", Role.STAFF, null);

            assertThatThrownBy(() -> userService.createUser(request, kitchenPrincipal))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("KITCHEN cannot create users with role STAFF");

            verify(userRepository, never()).save(any());
        }
    }
}
