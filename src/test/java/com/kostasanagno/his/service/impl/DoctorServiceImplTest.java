package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.DoctorRequest;
import com.kostasanagno.his.dto.DoctorResponse;
import com.kostasanagno.his.dto.DoctorSearchCriteria;
import com.kostasanagno.his.entity.Clinic;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.entity.Specialty;
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.DoctorMapper;
import com.kostasanagno.his.mapper.DoctorMapperImpl;
import com.kostasanagno.his.mapper.SpecialtyMapperImpl;
import com.kostasanagno.his.repository.ClinicRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.SpecialtyRepository;
import com.kostasanagno.his.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DoctorServiceImplTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClinicRepository clinicRepository;

    private DoctorMapper doctorMapper;

    @InjectMocks
    private DoctorServiceImpl service;

    private Specialty cardiology;

    @BeforeEach
    void setUp() {
        doctorMapper = new DoctorMapperImpl();
        ReflectionTestUtils.setField(doctorMapper, "specialtyMapper", new SpecialtyMapperImpl());
        ReflectionTestUtils.setField(service, "doctorMapper", doctorMapper);
        cardiology = Specialty.builder().name("Cardiology").build();
        cardiology.setId(1L);
    }

    private static DoctorRequest request(Long specialtyId, Long userId) {
        return new DoctorRequest("Gregory", "House", " MD-1001 ", specialtyId, "+30 210 1234567",
                "house@his.local", userId, null);
    }

    private static User user(Long id, Role... roles) {
        User user = User.builder().username("dr.house").roles(Set.of(roles)).build();
        user.setId(id);
        return user;
    }

    @Test
    void createSavesDoctorWithSpecialtyAndTrimmedLicense() {
        when(doctorRepository.existsByLicenseNumber("MD-1001")).thenReturn(false);
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(cardiology));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setId(7L);
            return d;
        });

        DoctorResponse response = service.create(request(1L, null));

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.licenseNumber()).isEqualTo("MD-1001");
        assertThat(response.specialty().name()).isEqualTo("Cardiology");
        assertThat(response.userId()).isNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void createRejectsDuplicateLicense() {
        when(doctorRepository.existsByLicenseNumber("MD-1001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(1L, null))).isInstanceOf(DuplicateResourceException.class);
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownSpecialty() {
        when(doctorRepository.existsByLicenseNumber("MD-1001")).thenReturn(false);
        when(specialtyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(99L, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Specialty");
    }

    @Test
    void createLinksExistingDoctorUser() {
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(cardiology));
        when(doctorRepository.existsByUserId(5L)).thenReturn(false);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.DOCTOR)));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = service.create(request(1L, 5L));

        assertThat(response.userId()).isEqualTo(5L);
    }

    @Test
    void createRejectsUnknownUser() {
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(cardiology));
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(1L, 5L))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRejectsUserWithoutDoctorRole() {
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(cardiology));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, Role.NURSE)));

        assertThatThrownBy(() -> service.create(request(1L, 5L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("DOCTOR");
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void createRejectsUserLinkedToAnotherDoctor() {
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(cardiology));
        when(doctorRepository.existsByUserId(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(1L, 5L))).isInstanceOf(DuplicateResourceException.class);
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void createLinksAnActiveClinic() {
        Clinic clinic = Clinic.builder().name("Cardiology").build();
        clinic.setId(4L);
        when(doctorRepository.existsByLicenseNumber("MD-1001")).thenReturn(false);
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(cardiology));
        when(clinicRepository.findById(4L)).thenReturn(Optional.of(clinic));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        DoctorResponse response = service.create(new DoctorRequest("Gregory", "House", "MD-1001", 1L, null, null, null, 4L));

        assertThat(response.clinic().name()).isEqualTo("Cardiology");
    }

    @Test
    void createRejectsUnknownAndInactiveClinics() {
        when(specialtyRepository.findById(1L)).thenReturn(Optional.of(cardiology));
        when(clinicRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new DoctorRequest("G", "H", "MD-1001", 1L, null, null, null, 9L)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Clinic");

        Clinic closed = Clinic.builder().name("Closed").active(false).build();
        closed.setId(5L);
        when(clinicRepository.findById(5L)).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> service.create(new DoctorRequest("G", "H", "MD-1001", 1L, null, null, null, 5L)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("inactive");
        verify(doctorRepository, never()).save(any());
    }

    @Test
    void getByIdThrowsWhenMissingOrSoftDeleted() {
        when(doctorRepository.findByIdAndActiveTrue(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getByUsernameThrowsWhenNoProfileIsLinked() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByUsername("dr.house")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateOverwritesFieldsAndClearsUserLink() {
        Doctor existing = Doctor.builder().firstName("Old").lastName("Name").licenseNumber("X")
                .specialty(cardiology).user(user(5L, Role.DOCTOR)).build();
        existing.setId(3L);
        Specialty neurology = Specialty.builder().name("Neurology").build();
        neurology.setId(2L);
        when(doctorRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(existing));
        when(doctorRepository.existsByLicenseNumberAndIdNot("MD-1001", 3L)).thenReturn(false);
        when(specialtyRepository.findById(2L)).thenReturn(Optional.of(neurology));
        when(doctorRepository.saveAndFlush(existing)).thenReturn(existing);

        DoctorResponse response = service.update(3L, request(2L, null));

        assertThat(response.firstName()).isEqualTo("Gregory");
        assertThat(response.specialty().name()).isEqualTo("Neurology");
        assertThat(response.userId()).isNull();
        assertThat(response.active()).isTrue();
    }

    @Test
    void updateRejectsLicenseOfAnotherDoctor() {
        Doctor existing = Doctor.builder().licenseNumber("X").build();
        when(doctorRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(existing));
        when(doctorRepository.existsByLicenseNumberAndIdNot("MD-1001", 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(3L, request(1L, null))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateRejectsUserLinkedToAnotherDoctor() {
        Doctor existing = Doctor.builder().licenseNumber("X").build();
        when(doctorRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(existing));
        when(doctorRepository.existsByLicenseNumberAndIdNot("MD-1001", 3L)).thenReturn(false);
        when(doctorRepository.existsByUserIdAndIdNot(5L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(3L, request(1L, 5L))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deleteIsSoft() {
        Doctor existing = Doctor.builder().licenseNumber("X").build();
        when(doctorRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(existing));

        service.delete(3L);

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getDeletedAt()).isNotNull();
        verify(doctorRepository, never()).delete(any(Doctor.class));
        verify(doctorRepository, never()).deleteById(any());
    }

    @Test
    void searchMapsPageToResponses() {
        Doctor doctor = Doctor.builder().firstName("A").lastName("B").licenseNumber("L").specialty(cardiology).build();
        Pageable pageable = PageRequest.of(0, 10);
        when(doctorRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor), pageable, 1));

        Page<DoctorResponse> page = service.search(new DoctorSearchCriteria("A", null, 1L, null, null), pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).specialty().id()).isEqualTo(1L);
    }
}
