package com.restaurantqr.user;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.user.dto.CreateUserRequest;
import com.restaurantqr.user.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MANAGER')")
public class UserController implements UserApi {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public ResponseEntity<UserResponse> createUser(CreateUserRequest request,
                                                   Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        UserResponse response = userService.createUser(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
