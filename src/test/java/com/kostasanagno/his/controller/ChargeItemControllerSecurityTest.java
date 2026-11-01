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
import com.kostasanagno.his.dto.ChargeItemResponse;
import com.kostasanagno.his.exception.GlobalExceptionHandler;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.security.RestAccessDeniedHandler;
import com.kostasanagno.his.security.RestAuthenticationEntryPoint;
import com.kostasanagno.his.service.ChargeItemService;
import java.math.BigDecimal;
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

/** The price list: every role reads, only ADMIN maintains it. */
@WebMvcTest(ChargeItemController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class ChargeItemControllerSecurityTest {

    private static final String ITEM_JSON = "{\"code\":\"LAB-01\",\"name\":\"Blood test\",\"unitPrice\":12.50,\"vatRate\":13}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ChargeItemService chargeItemService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private static ChargeItemResponse item() {
        return new ChargeItemResponse(1L, "LAB-01", "Blood test", new BigDecimal("12.50"), new BigDecimal("13.00"), true);
    }

    private void stubService() {
        when(chargeItemService.create(any())).thenReturn(item());
        when(chargeItemService.getById(1L)).thenReturn(item());
        when(chargeItemService.search(any(), any())).thenReturn(new PageImpl<>(List.of(item()), PageRequest.of(0, 20), 1));
        when(chargeItemService.update(any(), any())).thenReturn(item());
        when(chargeItemService.deactivate(1L)).thenReturn(item());
    }

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/charge-items")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/charge-items").contentType(MediaType.APPLICATION_JSON).content(ITEM_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NURSE", "RECEPTIONIST"})
    void everyRoleCanReadThePriceList(String role) throws Exception {
        stubService();
        var auth = user("u").roles(role);

        mockMvc.perform(get("/api/charge-items").param("text", "blood").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].code").value("LAB-01"));
        mockMvc.perform(get("/api/charge-items/1").with(auth)).andExpect(status().isOk());
    }

    @Test
    void adminMaintainsThePriceList() throws Exception {
        stubService();
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post("/api/charge-items").with(admin).contentType(MediaType.APPLICATION_JSON).content(ITEM_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/charge-items/1").with(admin).contentType(MediaType.APPLICATION_JSON).content(ITEM_JSON))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/charge-items/1/deactivate").with(admin)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DOCTOR", "NURSE", "RECEPTIONIST"})
    void othersCannotChangeThePriceList(String role) throws Exception {
        var auth = user("u").roles(role);

        mockMvc.perform(post("/api/charge-items").with(auth).contentType(MediaType.APPLICATION_JSON).content(ITEM_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(put("/api/charge-items/1").with(auth).contentType(MediaType.APPLICATION_JSON).content(ITEM_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/charge-items/1/deactivate").with(auth)).andExpect(status().isForbidden());
    }

    @Test
    void validationAndNoDelete() throws Exception {
        var admin = user("admin").roles("ADMIN");

        mockMvc.perform(post("/api/charge-items").with(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"unitPrice\":0,\"vatRate\":101}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='code')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='name')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='unitPrice')]").exists())
                .andExpect(jsonPath("$.validationErrors[?(@.field=='vatRate')]").exists());
        mockMvc.perform(delete("/api/charge-items/1").with(admin)).andExpect(status().is4xxClientError());
    }
}
