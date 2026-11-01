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
import com.kostasanagno.his.dto.ClinicSummary;
import com.kostasanagno.his.dto.DoctorResponse;
import com.kostasanagno.his.dto.SpecialtyResponse;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.DoctorService;
import com.kostasanagno.his.service.SpecialtyService;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 401 / 403 / 2xx per role for the Doctor and Specialty endpoints (service layer mocked). */
@WebMvcTest({DoctorController.class, SpecialtyController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class DoctorControllerSecurityTest {

    private static final String DOCTOR_JSON = """
            {"firstName":"Gregory","lastName":"House","licenseNumber":"MD-1001","specialtyId":1}""";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private DoctorService doctorService;
    @MockitoBean
    private SpecialtyService specialtyService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static DoctorResponse doctor() {
        return new DoctorResponse(1L, "Gregory", "House", "MD-1001", new SpecialtyResponse(1L, "Cardiology"),
                new ClinicSummary(1L, "Cardiology"), null, null, null, true, Instant.now(), Instant.now());
    }

    @Test
    void anonymousGets401OnEveryEndpoint() throws Exception {
        mockMvc.perform(get("/api/doctors")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/doctors/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/doctors/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/specialties")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/doctors").contentType(MediaType.APPLICATION_JSON).content(DOCTOR_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(put("/api/doctors/1").contentType(MediaType.APPLICATION_JSON).content(DOCTOR_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/doctors/1")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST"})
    void everyRoleCanReadSearchAndListSpecialties(String role) throws Exception {
        when(doctorService.getById(1L)).thenReturn(doctor());
        when(doctorService.search(any(), any())).thenReturn(new PageImpl<>(List.of(doctor()), PageRequest.of(0, 20), 1));
        when(specialtyService.findAll()).thenReturn(List.of(new SpecialtyResponse(1L, "Cardiology")));
        var auth = user("u").roles(role);

        mockMvc.perform(get("/api/doctors/1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/doctors").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].specialty.name").value("Cardiology"));
        mockMvc.perform(get("/api/specialties").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cardiology"));
    }

    @Test
    void adminCanCreateUpdateAndDelete() throws Exception {
        when(doctorService.create(any())).thenReturn(doctor());
        when(doctorService.update(any(), any())).thenReturn(doctor());
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post("/api/doctors").with(admin).contentType(MediaType.APPLICATION_JSON).content(DOCTOR_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/doctors/1").with(admin).contentType(MediaType.APPLICATION_JSON).content(DOCTOR_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/doctors/1").with(admin)).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "RECEPTIONIST"})
    void nonAdminCannotCreateUpdateOrDelete(String role) throws Exception {
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/doctors").with(auth).contentType(MediaType.APPLICATION_JSON).content(DOCTOR_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(put("/api/doctors/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(DOCTOR_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/doctors/1").with(auth)).andExpect(status().isForbidden());
    }

    @Test
    void invalidDoctorBodyReturnsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/doctors").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='specialtyId')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='licenseNumber')]").exists());
    }

    @Test
    void meIsDoctorOnly() throws Exception {
        when(doctorService.getByUsername("dr.house")).thenReturn(doctor());

        mockMvc.perform(get("/api/doctors/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenseNumber").value("MD-1001"));
        for (String role : List.of("ADMIN", "NURSE", "RECEPTIONIST")) {
            mockMvc.perform(get("/api/doctors/me").with(user("u").roles(role))).andExpect(status().isForbidden());
        }
    }

    @Test
    void meReturns404WhenNoProfileIsLinked() throws Exception {
        when(doctorService.getByUsername("dr.house"))
                .thenThrow(new ResourceNotFoundException("No doctor profile is linked to the current user"));

        mockMvc.perform(get("/api/doctors/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
