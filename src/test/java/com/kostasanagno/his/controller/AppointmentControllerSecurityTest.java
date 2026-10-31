package com.kostasanagno.his.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import com.kostasanagno.his.dto.AppointmentResponse;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.AppointmentStatus;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.AppointmentService;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 401 / 403 / 2xx per role for every Appointment endpoint (service layer mocked). */
@WebMvcTest(AppointmentController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class AppointmentControllerSecurityTest {

    private static final String CREATE_JSON = """
            {"patientId":1,"doctorId":2,"startTime":"2030-01-01T09:00:00+02:00",
             "endTime":"2030-01-01T09:30:00+02:00","reason":"Checkup"}""";
    private static final String UPDATE_JSON = """
            {"doctorId":2,"startTime":"2030-01-01T09:00:00Z","endTime":"2030-01-01T09:30:00Z","reason":"Checkup"}""";
    private static final String CANCEL_JSON = "{\"cancellationReason\":\"Patient called\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AppointmentService appointmentService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static AppointmentResponse appointment() {
        return new AppointmentResponse(1L, new PatientSummary(1L, "Maria Papadopoulou"),
                new DoctorSummary(2L, "Gregory House", "Cardiology"), Instant.parse("2030-01-01T07:00:00Z"),
                Instant.parse("2030-01-01T07:30:00Z"), AppointmentStatus.SCHEDULED, "Checkup", null, null,
                Instant.now(), Instant.now());
    }

    private void stubService() {
        when(appointmentService.create(any())).thenReturn(appointment());
        when(appointmentService.getById(1L)).thenReturn(appointment());
        when(appointmentService.search(any(), any())).thenReturn(new PageImpl<>(List.of(appointment()), PageRequest.of(0, 20), 1));
        when(appointmentService.searchForDoctorUser(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(appointment()), PageRequest.of(0, 20), 1));
        when(appointmentService.update(eq(1L), any())).thenReturn(appointment());
        when(appointmentService.cancel(eq(1L), any())).thenReturn(appointment());
        when(appointmentService.complete(eq(1L), any(), anyBoolean())).thenReturn(appointment());
        when(appointmentService.markNoShow(1L)).thenReturn(appointment());
    }

    @Test
    void anonymousGets401OnEveryEndpoint() throws Exception {
        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/appointments/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/appointments/1").contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/appointments/1/cancel").contentType(MediaType.APPLICATION_JSON).content(CANCEL_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/appointments/1/complete")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/appointments/1/no-show")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST"})
    void everyRoleCanReadCreateUpdateAndCancel(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/appointments").with(auth).contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctor.specialty").value("Cardiology"));
        mockMvc.perform(get("/api/appointments/1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/appointments").param("doctorId", "2").param("status", "SCHEDULED")
                        .param("from", "2030-01-01T00:00:00Z").param("to", "2030-01-02T00:00:00Z").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].patient.fullName").value("Maria Papadopoulou"));
        mockMvc.perform(put("/api/appointments/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(UPDATE_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/appointments/1/cancel").with(auth).contentType(MediaType.APPLICATION_JSON)
                        .content(CANCEL_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void thereIsNoDeleteEndpoint() throws Exception {
        mockMvc.perform(delete("/api/appointments/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void cancelRequiresAReason() throws Exception {
        mockMvc.perform(post("/api/appointments/1/cancel").with(user("u").roles("NURSE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='cancellationReason')]").exists());
    }

    @Test
    void invalidBookingBodyReturnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/appointments").with(user("u").roles("NURSE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='patientId')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='startTime')]").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR"})
    void adminAndDoctorCanComplete(String role) throws Exception {
        stubService();

        mockMvc.perform(post("/api/appointments/1/complete").with(user("u").roles(role))).andExpect(status().isOk());
        verify(appointmentService).complete(1L, "u", role.equals("ADMIN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NURSE", "RECEPTIONIST"})
    void nurseAndReceptionistCannotComplete(String role) throws Exception {
        mockMvc.perform(post("/api/appointments/1/complete").with(user("u").roles(role)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void notAssignedDoctorGets403FromTheService() throws Exception {
        when(appointmentService.complete(eq(1L), any(), anyBoolean())).thenThrow(new AccessDeniedException("no"));

        mockMvc.perform(post("/api/appointments/1/complete").with(user("dr.other").roles("DOCTOR")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "RECEPTIONIST", "DOCTOR"})
    void noShowAllowedRoles(String role) throws Exception {
        stubService();

        mockMvc.perform(post("/api/appointments/1/no-show").with(user("u").roles(role))).andExpect(status().isOk());
    }

    @Test
    void nurseCannotMarkNoShow() throws Exception {
        mockMvc.perform(post("/api/appointments/1/no-show").with(user("u").roles("NURSE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void meIsDoctorOnly() throws Exception {
        stubService();

        mockMvc.perform(get("/api/appointments/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        verify(appointmentService).searchForDoctorUser(eq("dr.house"), any(), any());
        for (String role : List.of("ADMIN", "NURSE", "RECEPTIONIST")) {
            mockMvc.perform(get("/api/appointments/me").with(user("u").roles(role))).andExpect(status().isForbidden());
        }
    }

    @Test
    void meReturns404WhenNoDoctorProfileIsLinked() throws Exception {
        when(appointmentService.searchForDoctorUser(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("No doctor profile is linked to the current user"));

        mockMvc.perform(get("/api/appointments/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isNotFound());
    }
}
