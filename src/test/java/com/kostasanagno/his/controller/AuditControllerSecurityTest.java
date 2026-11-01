package com.kostasanagno.his.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kostasanagno.his.config.SecurityConfig;
import com.kostasanagno.his.dto.AuditEventResponse;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.AuditService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The audit log can be read by ADMIN only and cannot be changed through the API. */
@WebMvcTest(AuditController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class AuditControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuditService auditService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/audit-events")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "RECEPTIONIST"})
    void nonAdminGets403(String role) throws Exception {
        mockMvc.perform(get("/api/audit-events").with(user("u").roles(role)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminCanSearchWithFilters() throws Exception {
        var event = new AuditEventResponse(1L, Instant.parse("2030-01-01T00:00:00Z"), "nurse", "NURSE",
                AuditAction.VIEW, AuditResource.PATIENT, 5L, 5L, AuditOutcome.SUCCESS, null, "GET", "/api/patients/5",
                "10.0.0.1");
        when(auditService.search(any(), any())).thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/audit-events").param("patientId", "5").param("outcome", "SUCCESS")
                        .param("resourceType", "PATIENT").param("from", "2029-12-31T00:00:00Z")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("nurse"))
                .andExpect(jsonPath("$.content[0].patientId").value(5));
    }

    @Test
    void noWayToChangeOrDeleteEvents() throws Exception {
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(delete("/api/audit-events/1").with(admin)).andExpect(status().is4xxClientError());
        mockMvc.perform(put("/api/audit-events/1").with(admin)).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/audit-events").with(admin)).andExpect(status().is4xxClientError());
    }
}
