package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.ChargeItemRequest;
import com.kostasanagno.his.dto.ChargeItemResponse;
import com.kostasanagno.his.entity.ChargeItem;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.ChargeItemMapperImpl;
import com.kostasanagno.his.repository.ChargeItemRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeItemServiceImplTest {

    @Mock
    private ChargeItemRepository chargeItemRepository;

    private ChargeItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChargeItemServiceImpl(chargeItemRepository, new ChargeItemMapperImpl());
    }

    private static ChargeItemRequest request(String code) {
        return new ChargeItemRequest(code, " Blood test ", new BigDecimal("12.50"), new BigDecimal("13.00"));
    }

    @Test
    void createNormalizesTheCodeAndTrimsTheName() {
        when(chargeItemRepository.save(any(ChargeItem.class))).thenAnswer(inv -> inv.getArgument(0));

        ChargeItemResponse response = service.create(request(" lab-01 "));

        assertThat(response.code()).isEqualTo("LAB-01");
        assertThat(response.name()).isEqualTo("Blood test");
        assertThat(response.unitPrice()).isEqualByComparingTo("12.50");
        assertThat(response.active()).isTrue();
    }

    @Test
    void duplicateCodesAreRejected() {
        when(chargeItemRepository.existsByCodeIgnoreCase("LAB-01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("lab-01"))).isInstanceOf(DuplicateResourceException.class);
        verify(chargeItemRepository, never()).save(any());
    }

    @Test
    void updateChangesThePriceListEntry() {
        ChargeItem item = ChargeItem.builder().code("LAB-01").name("Old").unitPrice(new BigDecimal("1.00"))
                .vatRate(BigDecimal.ZERO).build();
        item.setId(5L);
        when(chargeItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(chargeItemRepository.saveAndFlush(item)).thenReturn(item);

        ChargeItemResponse response = service.update(5L, request("lab-02"));

        assertThat(response.code()).isEqualTo("LAB-02");
        assertThat(response.unitPrice()).isEqualByComparingTo("12.50");
    }

    @Test
    void updateRejectsACodeUsedByAnotherItem() {
        ChargeItem item = ChargeItem.builder().code("A").build();
        when(chargeItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(chargeItemRepository.existsByCodeIgnoreCaseAndIdNot("LAB-01", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, request("lab-01"))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deactivateAndUnknownItem() {
        ChargeItem item = ChargeItem.builder().code("A").build();
        when(chargeItemRepository.findById(5L)).thenReturn(Optional.of(item));
        when(chargeItemRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(service.deactivate(5L).active()).isFalse();
        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
