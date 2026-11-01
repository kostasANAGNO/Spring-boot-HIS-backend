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
import com.kostasanagno.his.dto.ClinicalNoteResponse;
import com.kostasanagno.his.dto.ClinicalNoteVersionResponse;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.ClinicalNoteService;
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

/** Clinical notes: 401 anonymous, 403 for receptionists everywhere, role rules for write endpoints. */
@WebMvcTest(ClinicalNoteController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class ClinicalNoteControllerSecurityTest {

    private static final String CREATE_JSON = """
            {"patientId":1,"assessment":"Suspected angina","diagnosisCode":"I20.9"}""";
    private static final String AMEND_JSON = """
            {"baseVersion":1,"changeReason":"Typo","assessment":"Confirmed angina"}""";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ClinicalNoteService clinicalNoteService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static ClinicalNoteResponse note() {
        return new ClinicalNoteResponse(1L, new PatientSummary(1L, "Maria Papadopoulou"),
                new DoctorSummary(2L, "Gregory House", "Cardiology"), null, null, 1, null, null,
                "Suspected angina", null, "I20.9", null, "dr.house", Instant.now(), Instant.now());
    }

    private void stubService() {
        when(clinicalNoteService.create(any(), any())).thenReturn(note());
        when(clinicalNoteService.getById(1L)).thenReturn(note());
        when(clinicalNoteService.getVersions(1L)).thenReturn(List.of(new ClinicalNoteVersionResponse(1, null, null,
                "Suspected angina", null, "I20.9", null, "dr.house", Instant.now())));
        when(clinicalNoteService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(note()), PageRequest.of(0, 20), 1));
        when(clinicalNoteService.searchForDoctorUser(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(note()), PageRequest.of(0, 20), 1));
        when(clinicalNoteService.amend(eq(1L), any(), any())).thenReturn(note());
    }

    @Test
    void anonymousGets401OnEveryEndpoint() throws Exception {
        mockMvc.perform(post("/api/clinical-notes").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/clinical-notes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clinical-notes/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clinical-notes/1/versions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/clinical-notes/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/clinical-notes/1").contentType(MediaType.APPLICATION_JSON).content(AMEND_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receptionistIsForbiddenEverywhere() throws Exception {
        var receptionist = user("rita").roles("RECEPTIONIST");

        mockMvc.perform(get("/api/clinical-notes").with(receptionist)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clinical-notes/1").with(receptionist)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clinical-notes/1/versions").with(receptionist)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/clinical-notes/me").with(receptionist)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/clinical-notes").with(receptionist).contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_JSON)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/clinical-notes/1").with(receptionist).contentType(MediaType.APPLICATION_JSON)
                .content(AMEND_JSON)).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE"})
    void clinicalStaffCanReadSearchAndSeeVersions(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(get("/api/clinical-notes/1").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnosisCode").value("I20.9"));
        mockMvc.perform(get("/api/clinical-notes/1/versions").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].version").value(1));
        mockMvc.perform(get("/api/clinical-notes").param("patientId", "1").param("diagnosisCode", "I20")
                        .param("createdFrom", "2029-12-31T00:00:00Z").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyDoctorsCanWrite() throws Exception {
        stubService();

        mockMvc.perform(post("/api/clinical-notes").with(user("dr").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isCreated());
        for (String role : List.of("ADMIN", "NURSE", "RECEPTIONIST")) {
            mockMvc.perform(post("/api/clinical-notes").with(user("u").roles(role))
                            .contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR"})
    void adminAndDoctorCanAmend(String role) throws Exception {
        stubService();

        mockMvc.perform(put("/api/clinical-notes/1").with(user("u").roles(role))
                        .contentType(MediaType.APPLICATION_JSON).content(AMEND_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void nurseCannotAmend() throws Exception {
        mockMvc.perform(put("/api/clinical-notes/1").with(user("u").roles("NURSE"))
                        .contentType(MediaType.APPLICATION_JSON).content(AMEND_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void notTheAuthorGets403FromTheService() throws Exception {
        when(clinicalNoteService.amend(eq(1L), any(), any())).thenThrow(new AccessDeniedException("no"));

        mockMvc.perform(put("/api/clinical-notes/1").with(user("dr.other").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(AMEND_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void meIsDoctorOnly() throws Exception {
        stubService();

        mockMvc.perform(get("/api/clinical-notes/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        verify(clinicalNoteService).searchForDoctorUser(any(), any(), eq(new Caller("dr.house", Set.of(Role.DOCTOR))));
        for (String role : List.of("ADMIN", "NURSE", "RECEPTIONIST")) {
            mockMvc.perform(get("/api/clinical-notes/me").with(user("u").roles(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void meReturns404WhenNoDoctorProfileIsLinked() throws Exception {
        when(clinicalNoteService.searchForDoctorUser(any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("No doctor profile is linked to the current user"));

        mockMvc.perform(get("/api/clinical-notes/me").with(user("dr.house").roles("DOCTOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationAndNoDelete() throws Exception {
        mockMvc.perform(post("/api/clinical-notes").with(user("dr").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='contentProvided')]").exists());
        mockMvc.perform(post("/api/clinical-notes").with(user("dr").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":1,\"assessment\":\"x\",\"diagnosisCode\":\"not-a-code\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='diagnosisCode')]").exists());
        mockMvc.perform(put("/api/clinical-notes/1").with(user("dr").roles("DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"assessment\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='baseVersion')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='changeReason')]").exists());
        mockMvc.perform(delete("/api/clinical-notes/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().is4xxClientError());
    }
}
