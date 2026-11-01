package com.kostasanagno.his.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kostasanagno.his.config.SecurityConfig;
import com.kostasanagno.his.dto.AdministrationResponse;
import com.kostasanagno.his.dto.DispensingResponse;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.MedicationOrderResponse;
import com.kostasanagno.his.dto.MedicationSummary;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.AdministrationOutcome;
import com.kostasanagno.his.entity.MedicationForm;
import com.kostasanagno.his.entity.MedicationOrderStatus;
import com.kostasanagno.his.entity.MedicationRoute;
import com.kostasanagno.his.entity.MedicationSchedule;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.MedicationOrderService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Who may do what with medication: DOCTOR orders, PHARMACIST/ADMIN dispense and reject, DOCTOR/NURSE record doses,
 * ADMIN/DOCTOR stop or cancel, RECEPTIONIST sees nothing.
 */
@WebMvcTest({MedicationOrderController.class, MedicationAdministrationController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class MedicationOrderControllerSecurityTest {

    private static final String ORDER_JSON = """
            {"hospitalizationId":1,"medicationId":2,"doseAmount":500,"doseUnit":"mg","route":"ORAL",
             "scheduleType":"SCHEDULED","intervalHours":8}""";
    private static final String DISPENSE_JSON = "{\"quantity\":30}";
    private static final String REASON_JSON = "{\"reason\":\"Because\"}";
    private static final String GIVEN_JSON = "{\"outcome\":\"GIVEN\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private MedicationOrderService orderService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static MedicationOrderResponse order() {
        return new MedicationOrderResponse(1L, 40L, new PatientSummary(10L, "Maria Papadopoulou"), null,
                new MedicationSummary(2L, "PARACETAMOL-500", "Paracetamol", MedicationForm.TABLET, "500 mg", false),
                new DoctorSummary(20L, "Gregory House", "Cardiology"), new BigDecimal("500.000"), "mg",
                MedicationRoute.ORAL, MedicationSchedule.SCHEDULED, 8, Instant.now(), null, null,
                MedicationOrderStatus.PENDING, null, null, Instant.now(), Instant.now());
    }

    private static AdministrationResponse administration() {
        return new AdministrationResponse(1L, 1L, 40L, 10L, AdministrationOutcome.GIVEN, Instant.now(),
                new BigDecimal("500.000"), null, "nurse", Instant.now());
    }

    private void stubService() {
        when(orderService.create(any(), any())).thenReturn(order());
        when(orderService.getById(1L)).thenReturn(order());
        when(orderService.search(any(), any())).thenReturn(new PageImpl<>(List.of(order()), PageRequest.of(0, 20), 1));
        when(orderService.searchForDoctorUser(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(order()), PageRequest.of(0, 20), 1));
        when(orderService.dispense(eq(1L), any(), any())).thenReturn(order());
        when(orderService.reject(eq(1L), any(), any())).thenReturn(order());
        when(orderService.cancel(eq(1L), any(), any())).thenReturn(order());
        when(orderService.stop(eq(1L), any(), any())).thenReturn(order());
        when(orderService.administer(eq(1L), any(), any())).thenReturn(administration());
        when(orderService.getDispensings(1L)).thenReturn(List.of(new DispensingResponse(1L, 30, "pharma", Instant.now(), null)));
        when(orderService.getAdministrations(1L)).thenReturn(List.of(administration()));
        when(orderService.searchAdministrations(any(), any()))
                .thenReturn(new PageImpl<>(List.of(administration()), PageRequest.of(0, 20), 1));
    }

    @Test
    void anonymousGets401OnEveryEndpoint() throws Exception {
        mockMvc.perform(post("/api/medication-orders").contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        for (String path : List.of("/api/medication-orders", "/api/medication-orders/1", "/api/medication-orders/me",
                "/api/medication-orders/1/dispensings", "/api/medication-orders/1/administrations",
                "/api/medication-administrations")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String action : List.of("dispense", "reject", "cancel", "stop", "administrations")) {
            mockMvc.perform(post("/api/medication-orders/1/" + action).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void receptionistSeesNothingAtAll() throws Exception {
        var receptionist = user("rita").roles("RECEPTIONIST");

        for (String path : List.of("/api/medication-orders", "/api/medication-orders/1", "/api/medication-orders/me",
                "/api/medication-orders/1/dispensings", "/api/medication-orders/1/administrations",
                "/api/medication-administrations")) {
            mockMvc.perform(get(path).with(receptionist)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/medication-orders").with(receptionist).contentType(MediaType.APPLICATION_JSON)
                .content(ORDER_JSON)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "PHARMACIST"})
    void everyoneWhoHandlesMedicationCanRead(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(get("/api/medication-orders/1").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patient.fullName").value("Maria Papadopoulou"));
        mockMvc.perform(get("/api/medication-orders").param("status", "PENDING").param("patientId", "10").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/medication-orders/1/dispensings").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(30));
        mockMvc.perform(get("/api/medication-orders/1/administrations").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/medication-administrations").param("hospitalizationId", "40")
                        .param("outcome", "GIVEN").param("from", "2029-12-31T00:00:00Z").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].outcome").value("GIVEN"));
    }

    @Test
    void onlyADoctorOrders() throws Exception {
        stubService();

        mockMvc.perform(post("/api/medication-orders").with(user("dr").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().isCreated());
        for (String role : List.of("ADMIN", "NURSE", "PHARMACIST", "RECEPTIONIST")) {
            mockMvc.perform(post("/api/medication-orders").with(user("u").roles(role))
                            .contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PHARMACIST"})
    void adminAndPharmacistDispenseAndReject(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/medication-orders/1/dispense").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(DISPENSE_JSON)).andExpect(status().isOk());
        mockMvc.perform(post("/api/medication-orders/1/reject").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(REASON_JSON)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "RECEPTIONIST"})
    void othersCannotDispenseOrReject(String role) throws Exception {
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/medication-orders/1/dispense").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(DISPENSE_JSON)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/medication-orders/1/reject").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(REASON_JSON)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR"})
    void adminAndDoctorCancelAndStop(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/medication-orders/1/cancel").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(REASON_JSON)).andExpect(status().isOk());
        mockMvc.perform(post("/api/medication-orders/1/stop").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(REASON_JSON)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NURSE", "PHARMACIST", "RECEPTIONIST"})
    void othersCannotCancelOrStop(String role) throws Exception {
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/medication-orders/1/cancel").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(REASON_JSON)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/medication-orders/1/stop").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(REASON_JSON)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE"})
    void doctorsAndNursesRecordDoses(String role) throws Exception {
        stubService();

        mockMvc.perform(post("/api/medication-orders/1/administrations").with(user("u").roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(GIVEN_JSON))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.outcome").value("GIVEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PHARMACIST", "RECEPTIONIST"})
    void othersCannotRecordDoses(String role) throws Exception {
        mockMvc.perform(post("/api/medication-orders/1/administrations").with(user("u").roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(GIVEN_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void theCallerAndHisRolesReachTheService() throws Exception {
        stubService();

        mockMvc.perform(post("/api/medication-orders/1/dispense").with(user("pharma").roles("PHARMACIST"))
                .contentType(MediaType.APPLICATION_JSON).content(DISPENSE_JSON)).andExpect(status().isOk());
        verify(orderService).dispense(eq(1L), any(), eq(new Caller("pharma", Set.of(Role.PHARMACIST))));
    }

    @Test
    void servicesCanRefuseToo() throws Exception {
        when(orderService.dispense(eq(1L), any(), any())).thenThrow(new AccessDeniedException("controlled"));

        mockMvc.perform(post("/api/medication-orders/1/dispense").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(DISPENSE_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void meIsDoctorOnly() throws Exception {
        stubService();

        mockMvc.perform(get("/api/medication-orders/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String role : List.of("ADMIN", "NURSE", "PHARMACIST", "RECEPTIONIST")) {
            mockMvc.perform(get("/api/medication-orders/me").with(user("u").roles(role))).andExpect(status().isForbidden());
        }
    }

    @Test
    void meReturns404WhenNoDoctorProfileIsLinked() throws Exception {
        when(orderService.searchForDoctorUser(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("No doctor profile is linked to the current user"));

        mockMvc.perform(get("/api/medication-orders/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationAndNoEditNoDelete() throws Exception {
        var doctor = user("dr").roles("DOCTOR");

        mockMvc.perform(post("/api/medication-orders").with(doctor).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='hospitalizationId')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='medicationId')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='doseAmount')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='route')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='scheduleType')]").exists());
        mockMvc.perform(post("/api/medication-orders").with(doctor).contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER_JSON.replace("\"intervalHours\":8", "\"intervalHours\":48")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='intervalHours')]").exists());
        mockMvc.perform(post("/api/medication-orders/1/dispense").with(user("pharma").roles("PHARMACIST"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='quantity')]").exists());
        mockMvc.perform(post("/api/medication-orders/1/stop").with(doctor).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/medication-orders/1/administrations").with(user("n").roles("NURSE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='outcome')]").exists());
        mockMvc.perform(put("/api/medication-orders/1").with(doctor).contentType(MediaType.APPLICATION_JSON).content(ORDER_JSON))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/medication-orders/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().is4xxClientError());
    }
}
