package com.kostasanagno.his.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kostasanagno.his.config.SecurityConfig;
import com.kostasanagno.his.dto.InvoiceLineResponse;
import com.kostasanagno.his.dto.InvoiceResponse;
import com.kostasanagno.his.dto.InvoiceSummaryResponse;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.dto.PaymentResponse;
import com.kostasanagno.his.entity.InvoiceStatus;
import com.kostasanagno.his.entity.PaymentMethod;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.InvoiceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Billing: ADMIN and RECEPTIONIST bill and collect, every role reads, only ADMIN cancels. */
@WebMvcTest(InvoiceController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class InvoiceControllerSecurityTest {

    private static final String CREATE_JSON = "{\"patientId\":1}";
    private static final String LINE_JSON = "{\"chargeItemId\":1,\"quantity\":2}";
    private static final String PAY_JSON = "{\"amount\":10.00,\"method\":\"CASH\"}";
    private static final String CANCEL_JSON = "{\"reason\":\"Entered by mistake\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private InvoiceService invoiceService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static InvoiceResponse invoice() {
        return new InvoiceResponse(1L, "INV-2026-000001", InvoiceStatus.ISSUED, new PatientSummary(1L, "Maria P"),
                null, null, List.of(new InvoiceLineResponse(1L, 1L, "CONSULT", "Consultation", 2,
                        new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("100.00"), BigDecimal.ZERO,
                        new BigDecimal("100.00"))),
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("100.00"), Instant.now(), null, Instant.now(), Instant.now());
    }

    private void stubService() {
        when(invoiceService.create(any())).thenReturn(invoice());
        when(invoiceService.createFromHospitalization(5L)).thenReturn(invoice());
        when(invoiceService.getById(1L)).thenReturn(invoice());
        when(invoiceService.addLine(eq(1L), any())).thenReturn(invoice());
        when(invoiceService.removeLine(1L, 1L)).thenReturn(invoice());
        when(invoiceService.issue(1L)).thenReturn(invoice());
        when(invoiceService.cancel(eq(1L), any())).thenReturn(invoice());
        when(invoiceService.recordPayment(eq(1L), any(), any())).thenReturn(invoice());
        when(invoiceService.getPayments(1L)).thenReturn(List.of(new PaymentResponse(1L, new BigDecimal("10.00"),
                PaymentMethod.CASH, Instant.now(), null, "rita", Instant.now())));
        when(invoiceService.search(any(), any())).thenReturn(new PageImpl<>(List.of(new InvoiceSummaryResponse(1L,
                "INV-2026-000001", InvoiceStatus.ISSUED, new PatientSummary(1L, "Maria P"), null, null,
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), Instant.now(), Instant.now())),
                PageRequest.of(0, 20), 1));
    }

    @Test
    void anonymousGets401OnEveryEndpoint() throws Exception {
        mockMvc.perform(post("/api/invoices").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(post("/api/invoices/from-hospitalization/5")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/invoices/1/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/invoices/1/lines").contentType(MediaType.APPLICATION_JSON).content(LINE_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/invoices/1/lines/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/invoices/1/issue")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/invoices/1/cancel").contentType(MediaType.APPLICATION_JSON).content(CANCEL_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/invoices/1/payments").contentType(MediaType.APPLICATION_JSON).content(PAY_JSON))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST"})
    void everyRoleCanRead(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(get("/api/invoices/1").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].code").value("CONSULT"));
        mockMvc.perform(get("/api/invoices/1/payments").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].recordedBy").value("rita"));
        mockMvc.perform(get("/api/invoices").param("patientId", "1").param("outstanding", "true")
                        .param("status", "ISSUED").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "RECEPTIONIST"})
    void adminAndReceptionistCanBillAndCollect(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/invoices").with(auth).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/invoices/from-hospitalization/5").with(auth)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/invoices/1/lines").with(auth).contentType(MediaType.APPLICATION_JSON).content(LINE_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/invoices/1/lines/1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/1/issue").with(auth)).andExpect(status().isOk());
        mockMvc.perform(post("/api/invoices/1/payments").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(PAY_JSON)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE"})
    void doctorsAndNursesCannotBillOrCollect(String role) throws Exception {
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/invoices").with(auth).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(post("/api/invoices/from-hospitalization/5").with(auth)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invoices/1/lines").with(auth).contentType(MediaType.APPLICATION_JSON).content(LINE_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/invoices/1/lines/1").with(auth)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invoices/1/issue").with(auth)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/invoices/1/payments").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(PAY_JSON)).andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminCancels() throws Exception {
        stubService();

        mockMvc.perform(post("/api/invoices/1/cancel").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(CANCEL_JSON))
                .andExpect(status().isOk());
        for (String role : List.of("RECEPTIONIST", "DOCTOR", "NURSE")) {
            mockMvc.perform(post("/api/invoices/1/cancel").with(user("u").roles(role))
                            .contentType(MediaType.APPLICATION_JSON).content(CANCEL_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void thePaymentIsRecordedByTheLoggedInUser() throws Exception {
        stubService();

        mockMvc.perform(post("/api/invoices/1/payments").with(user("rita").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON).content(PAY_JSON))
                .andExpect(status().isOk());
        verify(invoiceService).recordPayment(eq(1L), any(), eq(new Caller("rita", Set.of(Role.RECEPTIONIST))));
    }

    @Test
    void validationAndNoDeleteOfInvoices() throws Exception {
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post("/api/invoices").with(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='patientId')]").exists());
        mockMvc.perform(post("/api/invoices/1/lines").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chargeItemId\":1,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='quantity')]").exists());
        mockMvc.perform(post("/api/invoices/1/payments").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"method\":\"CASH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='amount')]").exists());
        mockMvc.perform(post("/api/invoices/1/payments").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.005,\"method\":\"CASH\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/invoices/1/cancel").with(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/invoices/1").with(admin)).andExpect(status().is4xxClientError());
    }
}
