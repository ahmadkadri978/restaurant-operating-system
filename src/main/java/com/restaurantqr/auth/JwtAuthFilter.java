package com.restaurantqr.auth;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = null;
        
        // First, try to get token from Authorization header
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }
        
        // If no header token and this is an SSE endpoint, try query parameter
        // This is needed because EventSource API doesn't support custom headers
        if (token == null && request.getRequestURI().startsWith("/api/v1/sse/")) {
            token = request.getParameter("token");
        }
        
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            var jws = jwtService.parse(token);
            Claims claims = jws.getPayload();

            String userIdStr = claims.getSubject();
            Long userId = Long.valueOf(userIdStr);
            String role = claims.get("role", String.class);
            Long restaurantId = claims.get("restaurantId", Long.class);


            var user = userRepository.findById(userId).orElse(null);

            if (user == null || !user.isActive()) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write("{\"message\":\"Account is inactive\"}");
                return;
            }



            var principal = new AuthPrincipal(userId, role, restaurantId);

            var auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
        }


        filterChain.doFilter(request, response);
    }
}

