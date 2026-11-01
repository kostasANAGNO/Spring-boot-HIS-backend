package com.kostasanagno.his.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kostasanagno.his.config.SecurityConfig;
import com.kostasanagno.his.dto.MedicationResponse;
import com.kostasanagno.his.entity.MedicationForm;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.MedicationService;
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

/** The medication catalog: read by those who handle medication, maintained by ADMIN and PHARMACIST. */
@WebMvcTest(MedicationController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class MedicationControllerSecurityTest {

    private static final String MEDICATION_JSON = """
            {"code":"CEFTRIAXONE-1G","name":"Ceftriaxone","form":"INJECTION","strength":"1 g","controlled":false}""";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private MedicationService medicationService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static MedicationResponse medication() {
        return new MedicationResponse(1L, "CEFTRIAXONE-1G", "Ceftriaxone", MedicationForm.INJECTION, "1 g", null, false, true);
    }

    private void stubService() {
        when(medicationService.create(any())).thenReturn(medication());
        when(medicationService.getById(1L)).thenReturn(medication());
        when(medicationService.search(any(), any())).thenReturn(new PageImpl<>(List.of(medication()), PageRequest.of(0, 20), 1));
        when(medicationService.update(any(), any())).thenReturn(medication());
        when(medicationService.deactivate(1L)).thenReturn(medication());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/medications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/medications").contentType(MediaType.APPLICATION_JSON).content(MEDICATION_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "PHARMACIST"})
    void everyoneWhoHandlesMedicationCanReadTheCatalog(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(get("/api/medications").param("text", "cef").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].code").value("CEFTRIAXONE-1G"));
        mockMvc.perform(get("/api/medications/1").with(auth)).andExpect(status().isOk());
    }

    @Test
    void receptionistsDoNotSeeTheCatalog() throws Exception {
        var receptionist = user("rita").roles("RECEPTIONIST");

        mockMvc.perform(get("/api/medications").with(receptionist)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/medications/1").with(receptionist)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PHARMACIST"})
    void adminAndPharmacistMaintainTheCatalog(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/medications").with(auth).contentType(MediaType.APPLICATION_JSON).content(MEDICATION_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/medications/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(MEDICATION_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/medications/1/deactivate").with(auth)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "RECEPTIONIST"})
    void othersCannotChangeTheCatalog(String role) throws Exception {
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/medications").with(auth).contentType(MediaType.APPLICATION_JSON).content(MEDICATION_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(put("/api/medications/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(MEDICATION_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/medications/1/deactivate").with(auth)).andExpect(status().isForbidden());
    }

    @Test
    void validationAndNoDelete() throws Exception {
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post("/api/medications").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"atcCode\":\"N02 BE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='code')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='form')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='atcCode')]").exists());
        mockMvc.perform(delete("/api/medications/1").with(admin)).andExpect(status().is4xxClientError());
    }
}
