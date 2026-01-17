package com.restaurantqr.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html","/", "/index.html"
                        ).permitAll()
                        .requestMatchers(
                                "/", "/index.html",
                                "/health.txt",
                                "/**/*.css", "/**/*.js",
                                "/**/*.png", "/**/*.jpg", "/**/*.jpeg", "/**/*.svg", "/**/*.webp", "/**/*.ico"
                        ).permitAll()

                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/api/v1/manager/**").hasAuthority("ROLE_MANAGER")
                        .requestMatchers("/api/v1/staff/**").hasAnyAuthority("ROLE_STAFF", "ROLE_MANAGER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(
                // ✅ all static resources
                "/", "/index.html",
                "/health.txt",
                "/**/*.css", "/**/*.js",
                "/**/*.png", "/**/*.jpg", "/**/*.jpeg", "/**/*.svg", "/**/*.webp", "/**/*.ico",
                "/favicon.ico"
        );
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // لاحقًا استبدلها بدومين الواجهة الحقيقي (مثلا https://staff.yourdomain.com)
        config.setAllowedOrigins(List.of( "http://app.local"));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type","Accept"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false); // إذا تستخدم cookies. إن كنت JWT فقط في header ممكن false

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}



