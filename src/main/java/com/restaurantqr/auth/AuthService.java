package com.restaurantqr.auth;


import com.restaurantqr.auth.dto.LoginRequest;
import com.restaurantqr.auth.dto.LoginResponse;
import com.restaurantqr.exception.InvalidCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;


@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authManager, JwtService jwtService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest req) {
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );

            var details = (DbUserDetails) auth.getPrincipal();
            var user = details.getDomainUser();

            String token = jwtService.generateToken(
                    user.getId(),
                    user.getRole().name(),
                    user.getRestaurant().getId()
            );

            return new LoginResponse(token, user.getRole().name(), user.getRestaurant().getId(), user.getId());

        } catch (BadCredentialsException ex) {
            // username أو password غلط (الرسالة تكون عامة)
            throw new InvalidCredentialsException("wrong username/password");

        } catch (AuthenticationException ex) {
            // أي فشل auth آخر لا نريد يطلع 500 للمستخدم
            throw new InvalidCredentialsException("wrong username/password");
        }
    }
}

