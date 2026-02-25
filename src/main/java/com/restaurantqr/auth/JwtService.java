package com.restaurantqr.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private final JwtProperties props;
    private final Key key;
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    public JwtService(JwtProperties props) {
        if (props.secret() == null || props.secret().isBlank()) {
            throw new IllegalStateException("""
                    JWT secret is not configured!
                    Please set 'app.jwt.secret' in application.yml
                    """);
        }

        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String role, Long restaurantId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.expirationMinutes() * 60);
        log.info("JWT props.expirationMinutes() = {}", props.expirationMinutes());

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (restaurantId != null) {
            claims.put("restaurantId", restaurantId);
        }

        return Jwts.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(claims)
                .signWith(key)
                .compact();
    }

    public Jws<Claims> parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) key)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token);
    }
}

