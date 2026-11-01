package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.ClinicRequest;
import com.kostasanagno.his.dto.ClinicResponse;
import com.kostasanagno.his.entity.Clinic;
import com.kostasanagno.his.entity.Specialty;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.ClinicMapperImpl;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.ClinicRepository;
import com.kostasanagno.his.repository.SpecialtyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClinicServiceImplTest {

    @Mock
    private ClinicRepository clinicRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private BedAssignmentRepository bedAssignmentRepository;

    private ClinicServiceImpl service;
    private Specialty cardiology;
    private Clinic clinic;

    @BeforeEach
    void setUp() {
        service = new ClinicServiceImpl(clinicRepository, specialtyRepository, bedAssignmentRepository,
                new ClinicMapperImpl());
        cardiology = Specialty.builder().name("Cardiology").build();
        cardiology.setId(1L);
        clinic = Clinic.builder().name("Cardiology").specialty(cardiology).build();
        clinic.setId(4L);
    }

    @Test
    void createTrimsTheNameAndLinksTheSpecialty() {
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(cardiology));
        when(clinicRepository.save(any(Clinic.class))).thenAnswer(inv -> inv.getArgument(0));

        ClinicResponse response = service.create(new ClinicRequest(" Heart Clinic ", 1L));

        assertThat(response.name()).isEqualTo("Heart Clinic");
        assertThat(response.specialty().name()).isEqualTo("Cardiology");
        assertThat(response.active()).isTrue();
    }

    @Test
    void theSpecialtyIsOptional() {
        when(clinicRepository.save(any(Clinic.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(new ClinicRequest("Intensive Care", null)).specialty()).isNull();
    }

    @Test
    void createRejectsADuplicateNameAndAnUnknownSpecialty() {
        when(clinicRepository.existsByNameIgnoreCase("Cardiology")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new ClinicRequest("Cardiology", null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(clinicRepository, never()).save(any());

        when(specialtyRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new ClinicRequest("Oncology", 9L)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Specialty");
    }

    @Test
    void updateRenamesAndRejectsANameUsedByAnotherClinic() {
        when(clinicRepository.findById(4L)).thenReturn(Optional.of(clinic));
        when(clinicRepository.existsByNameIgnoreCaseAndIdNot("Surgery", 4L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(4L, new ClinicRequest("Surgery", null)))
                .isInstanceOf(DuplicateResourceException.class);

        when(clinicRepository.saveAndFlush(clinic)).thenReturn(clinic);
        ClinicResponse response = service.update(4L, new ClinicRequest("Heart Clinic", null));
        assertThat(response.name()).isEqualTo("Heart Clinic");
        assertThat(response.specialty()).isNull();
    }

    @Test
    void aClinicWithOccupiedBedsCannotBeDeactivated() {
        when(clinicRepository.findById(4L)).thenReturn(Optional.of(clinic));
        when(bedAssignmentRepository.existsByBedRoomWardClinicIdAndEndTimeIsNull(4L)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivate(4L)).isInstanceOf(BusinessRuleException.class);
        assertThat(clinic.isActive()).isTrue();
    }

    @Test
    void anEmptyClinicIsDeactivatedAndListedOnlyWhenAsked() {
        when(clinicRepository.findById(4L)).thenReturn(Optional.of(clinic));
        when(clinicRepository.findAllByActiveOrderByNameAsc(true)).thenReturn(List.of(clinic));
        when(clinicRepository.findAllByActiveOrderByNameAsc(false)).thenReturn(List.of());

        assertThat(service.findAll(null)).hasSize(1);
        assertThat(service.findAll(false)).isEmpty();
        assertThat(service.deactivate(4L).active()).isFalse();
    }

    @Test
    void unknownClinicIs404() {
        when(clinicRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
