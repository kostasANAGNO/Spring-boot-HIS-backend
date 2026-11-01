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
import com.kostasanagno.his.dto.BedResponse;
import com.kostasanagno.his.dto.ClinicResponse;
import com.kostasanagno.his.dto.RoomResponse;
import com.kostasanagno.his.dto.WardResponse;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.BedService;
import com.kostasanagno.his.service.ClinicService;
import com.kostasanagno.his.service.RoomService;
import com.kostasanagno.his.service.WardService;
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

/** Clinics, wards, rooms and beds: readable by every role, writable by ADMIN only. */
@WebMvcTest({ClinicController.class, WardController.class, RoomController.class, BedController.class})
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class StructureControllerSecurityTest {

    private static final String WARD_JSON = "{\"clinicId\":1,\"name\":\"North Wing\"}";
    private static final String CLINIC_JSON = "{\"name\":\"Oncology\"}";
    private static final String ROOM_JSON = "{\"wardId\":1,\"roomNumber\":\"101\"}";
    private static final String BED_JSON = "{\"roomId\":1,\"bedNumber\":\"A\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ClinicService clinicService;
    @MockitoBean
    private WardService wardService;
    @MockitoBean
    private RoomService roomService;
    @MockitoBean
    private BedService bedService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private void stubServices() {
        ClinicResponse clinic = new ClinicResponse(1L, "Oncology", null, true);
        when(clinicService.create(any())).thenReturn(clinic);
        when(clinicService.findAll(any())).thenReturn(List.of(clinic));
        when(clinicService.getById(1L)).thenReturn(clinic);
        when(clinicService.update(any(), any())).thenReturn(clinic);
        when(clinicService.deactivate(1L)).thenReturn(clinic);
        when(wardService.create(any())).thenReturn(new WardResponse(1L, "North Wing", 1L, "Oncology", true));
        when(wardService.findAll(any())).thenReturn(List.of(new WardResponse(1L, "North Wing", 1L, "Oncology", true)));
        when(wardService.getById(1L)).thenReturn(new WardResponse(1L, "North Wing", 1L, "Oncology", true));
        when(wardService.update(any(), any())).thenReturn(new WardResponse(1L, "North Wing", 1L, "Oncology", true));
        when(wardService.deactivate(1L)).thenReturn(new WardResponse(1L, "North Wing", 1L, "Oncology", false));
        RoomResponse room = new RoomResponse(1L, "101", 1L, "North Wing", "Oncology", true);
        when(roomService.create(any())).thenReturn(room);
        when(roomService.getById(1L)).thenReturn(room);
        when(roomService.search(any(), any())).thenReturn(new PageImpl<>(List.of(room), PageRequest.of(0, 20), 1));
        when(roomService.update(any(), any())).thenReturn(room);
        when(roomService.deactivate(1L)).thenReturn(room);
        BedResponse bed = new BedResponse(1L, "A", 1L, "101", 1L, "North Wing", 1L, "Oncology", true, false);
        when(bedService.create(any())).thenReturn(bed);
        when(bedService.getById(1L)).thenReturn(bed);
        when(bedService.search(any(), any())).thenReturn(new PageImpl<>(List.of(bed), PageRequest.of(0, 20), 1));
        when(bedService.update(any(), any())).thenReturn(bed);
        when(bedService.deactivate(1L)).thenReturn(bed);
    }

    @Test
    void anonymousGets401() throws Exception {
        for (String path : List.of("/api/clinics", "/api/wards", "/api/rooms", "/api/beds", "/api/clinics/1",
                "/api/wards/1", "/api/rooms/1", "/api/beds/1")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/wards").contentType(MediaType.APPLICATION_JSON).content(WARD_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(patch("/api/beds/1/deactivate")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST"})
    void everyRoleCanReadStructure(String role) throws Exception {
        stubServices();
        var auth = user("u").roles(role);

        mockMvc.perform(get("/api/clinics").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/clinics/1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/wards").param("clinicId", "1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/wards/1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/rooms").param("wardId", "1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/rooms/1").with(auth)).andExpect(status().isOk());
        mockMvc.perform(get("/api/beds").param("available", "true").param("wardId", "1").with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].wardName").value("North Wing"))
                .andExpect(jsonPath("$.content[0].clinicName").value("Oncology"))
                .andExpect(jsonPath("$.content[0].occupied").value(false));
        mockMvc.perform(get("/api/beds/1").with(auth)).andExpect(status().isOk());
    }

    @Test
    void adminCanWriteStructure() throws Exception {
        stubServices();
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post("/api/clinics").with(admin).contentType(MediaType.APPLICATION_JSON).content(CLINIC_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/clinics/1").with(admin).contentType(MediaType.APPLICATION_JSON).content(CLINIC_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/clinics/1/deactivate").with(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/wards").with(admin).contentType(MediaType.APPLICATION_JSON).content(WARD_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/wards/1").with(admin).contentType(MediaType.APPLICATION_JSON).content(WARD_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/wards/1/deactivate").with(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/rooms").with(admin).contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/rooms/1").with(admin).contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/rooms/1/deactivate").with(admin)).andExpect(status().isOk());
        mockMvc.perform(post("/api/beds").with(admin).contentType(MediaType.APPLICATION_JSON).content(BED_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/beds/1").with(admin).contentType(MediaType.APPLICATION_JSON).content(BED_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/beds/1/deactivate").with(admin)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "RECEPTIONIST"})
    void nonAdminCannotWriteStructure(String role) throws Exception {
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/clinics").with(auth).contentType(MediaType.APPLICATION_JSON).content(CLINIC_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(put("/api/clinics/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(CLINIC_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/clinics/1/deactivate").with(auth)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/wards").with(auth).contentType(MediaType.APPLICATION_JSON).content(WARD_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/wards/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(WARD_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/wards/1/deactivate").with(auth)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms").with(auth).contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/rooms/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(ROOM_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/rooms/1/deactivate").with(auth)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/beds").with(auth).contentType(MediaType.APPLICATION_JSON).content(BED_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/beds/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(BED_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/beds/1/deactivate").with(auth)).andExpect(status().isForbidden());
    }

    @Test
    void invalidBodiesAndNoDelete() throws Exception {
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post("/api/clinics").with(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='name')]").exists());
        mockMvc.perform(post("/api/wards").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='clinicId')]").exists());
        mockMvc.perform(post("/api/rooms").with(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='wardId')]").exists());
        mockMvc.perform(post("/api/beds").with(admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='bedNumber')]").exists());
        for (String path : List.of("/api/clinics/1", "/api/wards/1", "/api/rooms/1", "/api/beds/1")) {
            mockMvc.perform(delete(path).with(admin)).andExpect(status().is4xxClientError());
        }
    }
}
