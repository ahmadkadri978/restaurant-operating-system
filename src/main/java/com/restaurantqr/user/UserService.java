package com.restaurantqr.user;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ForbiddenOperationException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.restaurant.RestaurantRepository;
import com.restaurantqr.user.dto.CreateUserRequest;
import com.restaurantqr.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       RestaurantRepository restaurantRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, AuthPrincipal principal) {
        Role actorRole = Role.valueOf(principal.role());

        validateRolePermission(actorRole, request.role());
        Long restaurantId = actorRole.resolveRestaurantId(request, principal);

        log.info("CREATE_USER | actor={} | restaurantId={} | username={} | role={}",
                actorRole, restaurantId, request.username(), request.role());

        checkUsernameAvailable(request.username(), restaurantId);
        Restaurant restaurant = findRestaurant(restaurantId);
        User saved = userRepository.save(buildUser(request, restaurant));

        log.info("CREATE_USER_SUCCESS | userId={} | restaurantId={} | username={}",
                saved.getId(), restaurantId, saved.getUsername());

        return userMapper.toResponse(saved);
    }

    private void validateRolePermission(Role actorRole, Role targetRole) {
        if (!actorRole.canCreate(targetRole)) {
            throw new ForbiddenOperationException(
                    actorRole + " cannot create users with role " + targetRole);
        }
    }



    private void checkUsernameAvailable(String username, Long restaurantId) {
        if (userRepository.existsByUsername(username)) {
            log.warn("CREATE_USER_REJECTED | restaurantId={} | username={} | reason=USERNAME_TAKEN",
                    restaurantId, username);
            throw new BusinessRuleException("Username '" + username + "' is already taken");
        }
    }

    private Restaurant findRestaurant(Long restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant with id " + restaurantId + " not found"));
    }

    private User buildUser(CreateUserRequest request, Restaurant restaurant) {
        User user = new User();
        user.setRestaurant(restaurant);
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(true);
        return user;
    }
}
