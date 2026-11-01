package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.MedicationRequest;
import com.kostasanagno.his.dto.MedicationResponse;
import com.kostasanagno.his.entity.Medication;
import com.kostasanagno.his.entity.MedicationForm;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.MedicationMapperImpl;
import com.kostasanagno.his.repository.MedicationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MedicationServiceImplTest {

    @Mock
    private MedicationRepository medicationRepository;

    private MedicationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MedicationServiceImpl(medicationRepository, new MedicationMapperImpl());
    }

    private static MedicationRequest request(String code) {
        return new MedicationRequest(code, " Ceftriaxone ", MedicationForm.INJECTION, " 1 g ", "j01dd04", true);
    }

    @Test
    void createNormalizesTheCodeNameAndAtcCode() {
        when(medicationRepository.save(any(Medication.class))).thenAnswer(inv -> inv.getArgument(0));

        MedicationResponse response = service.create(request(" ceftriaxone-1g "));

        assertThat(response.code()).isEqualTo("CEFTRIAXONE-1G");
        assertThat(response.name()).isEqualTo("Ceftriaxone");
        assertThat(response.strength()).isEqualTo("1 g");
        assertThat(response.atcCode()).isEqualTo("J01DD04");
        assertThat(response.controlled()).isTrue();
        assertThat(response.active()).isTrue();
    }

    @Test
    void duplicateCodesAreRejected() {
        when(medicationRepository.existsByCodeIgnoreCase("CEFTRIAXONE-1G")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ceftriaxone-1g"))).isInstanceOf(DuplicateResourceException.class);
        verify(medicationRepository, never()).save(any());
    }

    @Test
    void updateChangesTheEntryButNotItsSafetyFlagByAccident() {
        Medication existing = Medication.builder().code("A").name("Old").form(MedicationForm.TABLET).build();
        when(medicationRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(medicationRepository.saveAndFlush(existing)).thenReturn(existing);

        MedicationResponse response = service.update(5L, request("new-code"));

        assertThat(response.code()).isEqualTo("NEW-CODE");
        assertThat(response.form()).isEqualTo(MedicationForm.INJECTION);
        assertThat(response.controlled()).isTrue();   // the request states it explicitly every time
    }

    @Test
    void updateRejectsACodeUsedByAnotherEntry() {
        Medication existing = Medication.builder().code("A").build();
        when(medicationRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(medicationRepository.existsByCodeIgnoreCaseAndIdNot("CEFTRIAXONE-1G", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, request("ceftriaxone-1g"))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deactivateAndUnknownEntry() {
        Medication existing = Medication.builder().code("A").form(MedicationForm.TABLET).build();
        when(medicationRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(medicationRepository.findById(9L)).thenReturn(Optional.empty());

        assertThat(service.deactivate(5L).active()).isFalse();
        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
