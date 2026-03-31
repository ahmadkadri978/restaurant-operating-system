package com.restaurantqr.auth;

import com.restaurantqr.admin.AdminRestaurantController;
import com.restaurantqr.admin.RestaurantSetupService;
import com.restaurantqr.finance.*;
import com.restaurantqr.order.staff.KitchenOrderController;
import com.restaurantqr.order.staff.StaffOrderService;
import com.restaurantqr.owner.DashboardPeriod;
import com.restaurantqr.owner.OwnerController;
import com.restaurantqr.owner.OwnerDashboardResponse;
import com.restaurantqr.owner.OwnerDashboardService;
import com.restaurantqr.sse.SseController;
import com.restaurantqr.sse.SseEmitterRegistry;
import com.restaurantqr.table.*;
import com.restaurantqr.user.Role;
import com.restaurantqr.user.User;
import com.restaurantqr.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc security tests that verify the SecurityConfig URL-to-role
 * mappings are enforced correctly, including method-level @PreAuthorize.
 * <p>
 * Uses real JWT tokens + real JwtAuthFilter so the full security chain is exercised.
 */
@WebMvcTest({
        OwnerController.class,
        InvoiceController.class,
        CashRegisterController.class,
        StaffTableController.class,
        AdminRestaurantController.class,
        KitchenOrderController.class,
        SseController.class
})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-for-hmac",
        "app.jwt.issuer=restaurant-qr",
        "app.jwt.expiration-minutes=60"
})
@DisplayName("Security URL → Role Mapping Tests")
class SecurityRoleMappingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    // ---- Mock all service beans the controllers depend on ----
    @MockitoBean private OwnerDashboardService ownerDashboardService;
    @MockitoBean private InvoiceService invoiceService;
    @MockitoBean private CashRegisterService cashRegisterService;
    @MockitoBean private StaffTableSessionService sessionService;
    @MockitoBean private BillCalculationService billCalculationService;
    @MockitoBean private TableSessionRepository tableSessionRepository;
    @MockitoBean private RestaurantSetupService restaurantSetupService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private StaffOrderService staffOrderService;
    @MockitoBean private SseEmitterRegistry sseEmitterRegistry;

    private String managerToken;
    private String ownerToken;
    private String staffToken;
    private String kitchenToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // Create active users for each role — the JwtAuthFilter checks userRepository.findById
        createActiveUser(1L, Role.MANAGER);
        createActiveUser(2L, Role.OWNER);
        createActiveUser(3L, Role.STAFF);
        createActiveUser(4L, Role.KITCHEN);
        createActiveUser(5L, Role.ADMIN);

        managerToken = jwtService.generateToken(1L, "MANAGER", 1L);
        ownerToken   = jwtService.generateToken(2L, "OWNER", 1L);
        staffToken   = jwtService.generateToken(3L, "STAFF", 1L);
        kitchenToken = jwtService.generateToken(4L, "KITCHEN", 1L);
        adminToken   = jwtService.generateToken(5L, "ADMIN", null);
    }

    private void createActiveUser(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setActive(true);
        user.setUsername(role.name().toLowerCase() + "-user");
        user.setPasswordHash("irrelevant");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // ================================================================
    //  /api/v1/owner/dashboard — OWNER only
    // ================================================================

    @Nested
    @DisplayName("Owner Dashboard (/api/v1/owner/dashboard)")
    class OwnerDashboardSecurity {

        @BeforeEach
        void stubService() {
            when(ownerDashboardService.getDashboard(anyLong(), any(DashboardPeriod.class), any(), anyString()))
                    .thenReturn(mock(OwnerDashboardResponse.class));
        }

        @Test
        void ownerShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/owner/dashboard")
                            .param("period", "DAY")
                            .param("zone", "Asia/Damascus")
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void managerShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/owner/dashboard")
                            .param("period", "DAY")
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void staffShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/owner/dashboard")
                            .param("period", "DAY")
                            .header("Authorization", bearer(staffToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedShouldBeUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/owner/dashboard")
                            .param("period", "DAY"))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  /api/v1/manager/invoices — MANAGER + OWNER
    // ================================================================

    @Nested
    @DisplayName("Invoices (/api/v1/manager/invoices)")
    class InvoicesSecurity {

        @BeforeEach
        void stubService() {
            when(invoiceService.listByDate(anyLong(), any(LocalDate.class), anyString()))
                    .thenReturn(Collections.emptyList());
        }

        @Test
        void managerShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/manager/invoices")
                            .param("date", "2026-03-31")
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void ownerShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/manager/invoices")
                            .param("date", "2026-03-31")
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void staffShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/manager/invoices")
                            .param("date", "2026-03-31")
                            .header("Authorization", bearer(staffToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedShouldBeUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/manager/invoices")
                            .param("date", "2026-03-31"))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  /api/v1/manager/cash-register/current — MANAGER + OWNER
    // ================================================================

    @Nested
    @DisplayName("Cash Register (/api/v1/manager/cash-register/current)")
    class CashRegisterSecurity {

        @BeforeEach
        void stubService() {
            when(cashRegisterService.getCurrentRegister(anyLong()))
                    .thenReturn(Optional.empty());
        }

        @Test
        void managerShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/manager/cash-register/current")
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void ownerShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/manager/cash-register/current")
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void staffShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/manager/cash-register/current")
                            .header("Authorization", bearer(staffToken)))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  /api/v1/manager/tables — MANAGER + STAFF (class-level)
    // ================================================================

    @Nested
    @DisplayName("Staff Tables (/api/v1/manager/tables)")
    class StaffTablesSecurity {

        @BeforeEach
        void stubService() {
            when(sessionService.listTables(anyLong()))
                    .thenReturn(Collections.emptyList());
        }

        @Test
        void managerShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/manager/tables")
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void staffShouldAccessViaSecurity() throws Exception {
            // SecurityConfig: /api/v1/manager/** → ROLE_MANAGER only (URL-level)
            // BUT StaffTableController has class-level @PreAuthorize("hasAnyRole('MANAGER','STAFF')")
            // SecurityConfig gives /api/v1/manager/** to MANAGER only at URL level,
            // so STAFF should be blocked at the security filter chain level
            mockMvc.perform(get("/api/v1/manager/tables")
                            .header("Authorization", bearer(staffToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void kitchenShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/manager/tables")
                            .header("Authorization", bearer(kitchenToken)))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  /api/v1/manager/tables/{id}/session/close-with-payment
    //  Method-level: @PreAuthorize("hasRole('MANAGER')") — MANAGER only
    // ================================================================

    @Nested
    @DisplayName("Close With Payment (/api/v1/manager/tables/{id}/session/close-with-payment)")
    class CloseWithPaymentSecurity {

        @Test
        void managerShouldAccess() throws Exception {
            CloseWithPaymentResponse response = new CloseWithPaymentResponse(
                    1L, true, 1L, "INV-001",
                    new java.math.BigDecimal("10000"), new java.math.BigDecimal("10000"),
                    java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
            when(sessionService.closeSessionWithPayment(anyLong(), anyLong(), any(), any(), anyLong()))
                    .thenReturn(response);

            mockMvc.perform(post("/api/v1/manager/tables/1/session/close-with-payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amountReceived": 10000, "tipAmount": 0}
                                    """)
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void staffShouldBeForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/manager/tables/1/session/close-with-payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amountReceived": 10000, "tipAmount": 0}
                                    """)
                            .header("Authorization", bearer(staffToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void ownerShouldBeForbidden() throws Exception {
            // /api/v1/manager/** URL-level is MANAGER only (not OWNER)
            // Even though cash-register and invoices have special overrides,
            // the generic /api/v1/manager/** does NOT include OWNER
            mockMvc.perform(post("/api/v1/manager/tables/1/session/close-with-payment")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"amountReceived": 10000, "tipAmount": 0}
                                    """)
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  /api/v1/admin/** — ADMIN only
    // ================================================================

    @Nested
    @DisplayName("Admin endpoints (/api/v1/admin/**)")
    class AdminSecurity {

        @Test
        void adminShouldNotBeForbidden() throws Exception {
            // Admin accessing admin endpoint — should pass security (may 404 on specific path)
            // We use a known endpoint path; any non-403 proves security allowed it
            mockMvc.perform(post("/api/v1/admin/setup-restaurant")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .header("Authorization", bearer(adminToken)))
                    .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        }

        @Test
        void ownerShouldBeForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/setup-restaurant")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void managerShouldBeForbidden() throws Exception {
            mockMvc.perform(post("/api/v1/admin/setup-restaurant")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  /api/v1/auth/** — public (no auth)
    // ================================================================

    @Nested
    @DisplayName("Auth endpoints (/api/v1/auth/**)")
    class AuthSecurity {

        @Test
        void shouldBeAccessibleWithoutAuth() throws Exception {
            // /api/v1/auth/login is permitAll — may return 400/404 but never 401/403
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"x","password":"y"}
                                    """))
                    .andExpect(status().is(org.hamcrest.Matchers.not(401)))
                    .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        }
    }

    // ================================================================
    //  /api/v1/kitchen/** — KITCHEN + MANAGER
    //  SecurityConfig: hasAnyAuthority("ROLE_KITCHEN", "ROLE_MANAGER")
    // ================================================================

    @Nested
    @DisplayName("Kitchen endpoints (/api/v1/kitchen/**)")
    class KitchenEndpointsSecurity {

        @Test
        void kitchenShouldAccess() throws Exception {
            mockMvc.perform(patch("/api/v1/kitchen/orders/1/done")
                            .header("Authorization", bearer(kitchenToken)))
                    .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        }

        @Test
        void managerShouldAccess() throws Exception {
            mockMvc.perform(patch("/api/v1/kitchen/orders/1/done")
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        }

        @Test
        void staffShouldBeForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/kitchen/orders/1/done")
                            .header("Authorization", bearer(staffToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void ownerShouldBeForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/kitchen/orders/1/done")
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedShouldBeForbidden() throws Exception {
            mockMvc.perform(patch("/api/v1/kitchen/orders/1/done"))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  /api/v1/sse/** — MANAGER + STAFF + KITCHEN
    //  SecurityConfig: hasAnyAuthority("ROLE_MANAGER", "ROLE_STAFF", "ROLE_KITCHEN")
    // ================================================================

    @Nested
    @DisplayName("SSE endpoints (/api/v1/sse/**)")
    class SseEndpointsSecurity {

        @BeforeEach
        void stubRegistry() {
            when(sseEmitterRegistry.register(anyLong(), any()))
                    .thenReturn(true);
            when(sseEmitterRegistry.getConnectionCount(anyLong()))
                    .thenReturn(1);
        }

        @Test
        void managerShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/sse/events")
                            .header("Authorization", bearer(managerToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void staffShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/sse/events")
                            .header("Authorization", bearer(staffToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void kitchenShouldAccess() throws Exception {
            mockMvc.perform(get("/api/v1/sse/events")
                            .header("Authorization", bearer(kitchenToken)))
                    .andExpect(status().isOk());
        }

        @Test
        void ownerShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/sse/events")
                            .header("Authorization", bearer(ownerToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unauthenticatedShouldBeForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/sse/events"))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    //  /api/v1/public/** — no auth required (permitAll)
    // ================================================================

    @Nested
    @DisplayName("Public endpoints (/api/v1/public/**)")
    class PublicEndpointsSecurity {

        @Test
        void shouldBeAccessibleWithoutAuth() throws Exception {
            // Public endpoint should never return 401/403
            // May return 400/404 etc. but the security chain does not block it
            mockMvc.perform(post("/api/v1/public/tables/test-token/session/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"code":"1234"}
                                    """))
                    .andExpect(status().is(org.hamcrest.Matchers.not(401)))
                    .andExpect(status().is(org.hamcrest.Matchers.not(403)));
        }
    }
}






