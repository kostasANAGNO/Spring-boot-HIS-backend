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
import com.kostasanagno.his.dto.BedAssignmentResponse;
import com.kostasanagno.his.dto.BedSummary;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.HospitalizationResponse;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.HospitalizationStatus;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.HospitalizationService;
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

/** 401 / 403 / 2xx per role for every Hospitalization endpoint (service layer mocked). */
@WebMvcTest(HospitalizationController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class HospitalizationControllerSecurityTest {

    private static final String ADMIT_JSON = """
            {"patientId":1,"attendingDoctorId":2,"bedId":3,"admissionReason":"Chest pain"}""";
    private static final String UPDATE_JSON = """
            {"attendingDoctorId":2,"admissionReason":"Chest pain","diagnosis":"Angina"}""";
    private static final String TRANSFER_JSON = "{\"bedId\":4}";
    private static final String DISCHARGE_JSON = "{\"dischargeNotes\":\"Recovered\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private HospitalizationService hospitalizationService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static HospitalizationResponse hospitalization() {
        return new HospitalizationResponse(1L, new PatientSummary(1L, "Maria Papadopoulou"),
                new DoctorSummary(2L, "Gregory House", "Cardiology"), new BedSummary(3L, "A", "101", "Cardiology"),
                null, HospitalizationStatus.ADMITTED, Instant.parse("2030-01-01T00:00:00Z"), null, "Chest pain",
                "Angina", null, Instant.now(), Instant.now());
    }

    private void stubService() {
        when(hospitalizationService.admit(any(), any())).thenReturn(hospitalization());
        when(hospitalizationService.getById(eq(1L), any())).thenReturn(hospitalization());
        when(hospitalizationService.getBedHistory(eq(1L), any())).thenReturn(List.of(
                new BedAssignmentResponse(1L, new BedSummary(3L, "A", "101", "Cardiology"),
                        Instant.parse("2030-01-01T00:00:00Z"), null, null)));
        when(hospitalizationService.search(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(hospitalization()), PageRequest.of(0, 20), 1));
        when(hospitalizationService.searchForDoctorUser(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(hospitalization()), PageRequest.of(0, 20), 1));
        when(hospitalizationService.update(eq(1L), any(), any())).thenReturn(hospitalization());
        when(hospitalizationService.transfer(eq(1L), any(), any())).thenReturn(hospitalization());
        when(hospitalizationService.discharge(eq(1L), any(), any())).thenReturn(hospitalization());
    }

    @Test
    void anonymousGets401OnEveryEndpoint() throws Exception {
        mockMvc.perform(post("/api/hospitalizations").contentType(MediaType.APPLICATION_JSON).content(ADMIT_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/hospitalizations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hospitalizations/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hospitalizations/1/bed-history")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/hospitalizations/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/hospitalizations/1").contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/hospitalizations/1/transfer").contentType(MediaType.APPLICATION_JSON)
                .content(TRANSFER_JSON)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/hospitalizations/1/discharge").contentType(MediaType.APPLICATION_JSON)
                .content(DISCHARGE_JSON)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST"})
    void everyRoleCanAdmitReadSearchAndSeeBedHistory(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/hospitalizations").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content(ADMIT_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentBed.wardName").value("Cardiology"));
        mockMvc.perform(get("/api/hospitalizations/1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/hospitalizations/1/bed-history").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].bed.bedNumber").value("A"));
        mockMvc.perform(get("/api/hospitalizations").param("status", "ADMITTED").param("wardId", "1")
                        .param("admittedFrom", "2029-12-31T00:00:00Z").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NURSE", "RECEPTIONIST"})
    void nurseAndReceptionistCannotUpdate(String role) throws Exception {
        mockMvc.perform(put("/api/hospitalizations/1").with(user("u").roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR"})
    void adminAndDoctorCanUpdateAndDischarge(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(put("/api/hospitalizations/1").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(UPDATE_JSON)).andExpect(status().isOk());
        mockMvc.perform(post("/api/hospitalizations/1/discharge").with(auth).contentType(MediaType.APPLICATION_JSON)
                .content(DISCHARGE_JSON)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"NURSE", "RECEPTIONIST"})
    void nurseAndReceptionistCannotDischarge(String role) throws Exception {
        mockMvc.perform(post("/api/hospitalizations/1/discharge").with(user("u").roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(DISCHARGE_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void notAttendingDoctorGets403FromTheService() throws Exception {
        when(hospitalizationService.discharge(eq(1L), any(), any())).thenThrow(new AccessDeniedException("no"));

        mockMvc.perform(post("/api/hospitalizations/1/discharge").with(user("dr.other").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(DISCHARGE_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE"})
    void clinicalStaffCanTransfer(String role) throws Exception {
        stubService();

        mockMvc.perform(post("/api/hospitalizations/1/transfer").with(user("u").roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSFER_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCannotTransfer() throws Exception {
        mockMvc.perform(post("/api/hospitalizations/1/transfer").with(user("u").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON).content(TRANSFER_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void controllerPassesTheCallerWithRolesToTheService() throws Exception {
        stubService();

        mockMvc.perform(get("/api/hospitalizations/1").with(user("rita").roles("RECEPTIONIST")))
                .andExpect(status().isOk());
        verify(hospitalizationService).getById(1L, new Caller("rita", Set.of(Role.RECEPTIONIST)));
    }

    @Test
    void meIsDoctorOnly() throws Exception {
        stubService();

        mockMvc.perform(get("/api/hospitalizations/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String role : List.of("ADMIN", "NURSE", "RECEPTIONIST")) {
            mockMvc.perform(get("/api/hospitalizations/me").with(user("u").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void meReturns404WhenNoDoctorProfileIsLinked() throws Exception {
        when(hospitalizationService.searchForDoctorUser(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("No doctor profile is linked to the current user"));

        mockMvc.perform(get("/api/hospitalizations/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsAndNoDeleteEndpoint() throws Exception {
        mockMvc.perform(post("/api/hospitalizations").with(user("u").roles("NURSE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"admissionReason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='patientId')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='bedId')]").exists());
        mockMvc.perform(post("/api/hospitalizations/1/discharge").with(user("u").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/hospitalizations/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().is4xxClientError());
    }
}
