package com.restaurantqr.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI restaurantQrOpenApi() {

        return new OpenAPI()
                .info(new Info()
                        .title("Restaurant QR Ordering & Service Call API")
                        .description("""
                                Backend API for a multi-restaurant QR ordering system.
                                
                                **Core flows**
                                - Customer scans table QR → views menu → submits orders (1 order/min per table).
                                - Customer can send service calls (anti-spam cooldown).
                                - Staff can view recent orders & active service calls (JWT protected).
                                
                                **Auth**
                                - Staff/Manager login returns JWT.
                                - Use JWT as: `Authorization: Bearer <token>`
                                
                                **Multi-tenant rule**
                                - Every STAFF/MANAGER user belongs to one restaurant.
                                - All `/staff/**` and `/manager/**` endpoints are isolated by `restaurantId` claim in JWT.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Backend Team")
                                .email("backend@restaurantqr.local")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local")
                ))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ));
    }
}

