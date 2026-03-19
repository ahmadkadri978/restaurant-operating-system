package com.restaurantqr.auth;


import com.restaurantqr.auth.dto.LoginRequest;
import com.restaurantqr.auth.dto.LoginResponse;
import com.restaurantqr.exception.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;


@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authManager, JwtService jwtService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest req) {
        log.info("Login attempt for user: {}", req.username());
        try {
            Authentication auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );

            var details = (DbUserDetails) auth.getPrincipal();
            var user = details.getDomainUser();
            
            log.info("User authenticated: id={}, role={}", user.getId(), user.getRole());

            // restaurantId can be null for ADMIN users (system-wide)
            Long restaurantId = user.getRestaurant() != null ? user.getRestaurant().getId() : null;
            
            log.info("Generating token for user: id={}, role={}, restaurantId={}", 
                    user.getId(), user.getRole().name(), restaurantId);

            String token = jwtService.generateToken(
                    user.getId(),
                    user.getRole().name(),
                    restaurantId
            );
            
            log.info("Login successful for user: {}", req.username());

            return new LoginResponse(token, user.getRole().name(), restaurantId, user.getId());

        } catch (BadCredentialsException ex) {
            log.warn("Bad credentials for user: {}", req.username());
            throw new InvalidCredentialsException("wrong username/password");

        } catch (AuthenticationException ex) {
            log.warn("Authentication failed for user: {} - {}", req.username(), ex.getMessage());
            throw new InvalidCredentialsException("wrong username/password");
        } catch (Exception ex) {
            log.error("Unexpected error during login for user: {}", req.username(), ex);
            throw ex;
        }
    }
}

