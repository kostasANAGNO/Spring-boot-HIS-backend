package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.AppointmentRequest;
import com.kostasanagno.his.dto.AppointmentResponse;
import com.kostasanagno.his.dto.AppointmentSearchCriteria;
import com.kostasanagno.his.dto.AppointmentUpdateRequest;
import com.kostasanagno.his.dto.CancelAppointmentRequest;
import com.kostasanagno.his.entity.Appointment;
import com.kostasanagno.his.entity.AppointmentStatus;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Patient;
import com.kostasanagno.his.entity.Specialty;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.AppointmentMapperImpl;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.service.AppointmentProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-01T08:00:00Z");
    private static final Instant START = NOW.plus(Duration.ofDays(1));
    private static final Instant END = START.plus(Duration.ofMinutes(30));

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DoctorRepository doctorRepository;

    private AppointmentServiceImpl service;
    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        var mapper = new AppointmentMapperImpl();
        service = new AppointmentServiceImpl(appointmentRepository, patientRepository, doctorRepository, mapper,
                new AppointmentProperties(Duration.ofMinutes(5), Duration.ofHours(8)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        Specialty cardiology = Specialty.builder().name("Cardiology").build();
        cardiology.setId(1L);
        patient = Patient.builder().firstName("Maria").lastName("Papadopoulou").nationalId("1").build();
        patient.setId(10L);
        doctor = Doctor.builder().firstName("Gregory").lastName("House").licenseNumber("MD-1")
                .specialty(cardiology).build();
        doctor.setId(20L);
    }

    private AppointmentRequest request(Instant start, Instant end) {
        return new AppointmentRequest(10L, 20L, start, end, "Checkup", null);
    }

    private void stubParties() {
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(20L)).thenReturn(Optional.of(doctor));
    }

    private void stubSave() {
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(100L);
            }
            return a;
        });
    }

    private Appointment scheduled() {
        Appointment a = Appointment.builder().patient(patient).doctor(doctor).startTime(START).endTime(END)
                .status(AppointmentStatus.SCHEDULED).reason("Checkup").build();
        a.setId(100L);
        return a;
    }

    private Appointment withStatus(AppointmentStatus status) {
        Appointment a = scheduled();
        a.setStatus(status);
        return a;
    }

    // ---- booking -------------------------------------------------------------------------------

    @Test
    void createBooksScheduledAppointmentWithNestedSummaries() {
        stubParties();
        stubSave();

        AppointmentResponse response = service.create(request(START, END));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(response.patient().fullName()).isEqualTo("Maria Papadopoulou");
        assertThat(response.doctor().fullName()).isEqualTo("Gregory House");
        assertThat(response.doctor().specialty()).isEqualTo("Cardiology");
    }

    @Test
    void backToBackAppointmentsAreAllowed() {
        // the overlap query is half-open; with no overlapping rows found, booking right after another succeeds
        stubParties();
        stubSave();
        when(appointmentRepository.exists(any(Specification.class))).thenReturn(false);

        assertThat(service.create(request(END, END.plus(Duration.ofMinutes(30)))).status())
                .isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void doctorOverlapIsRejectedWithDoctorMessage() {
        stubParties();
        when(appointmentRepository.exists(any(Specification.class))).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(START, END)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("doctor");
        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void patientOverlapIsRejectedWithPatientMessage() {
        stubParties();
        // first check (doctor) passes, second (patient) finds a clash
        when(appointmentRepository.exists(any(Specification.class))).thenReturn(false, true);

        assertThatThrownBy(() -> service.create(request(START, END)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("patient");
        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void missingPatientOrDoctorIs404() {
        when(patientRepository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(START, END)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Patient");

        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(START, END)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Doctor");
    }

    @Test
    void inactivePatientOrDoctorIsRejected() {
        patient.setActive(false);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        assertThatThrownBy(() -> service.create(request(START, END)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Patient");

        patient.setActive(true);
        doctor.setActive(false);
        when(doctorRepository.findById(20L)).thenReturn(Optional.of(doctor));
        assertThatThrownBy(() -> service.create(request(START, END)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Doctor");
    }

    @Test
    void pastOrCurrentStartIsRejected() {
        assertThatThrownBy(() -> service.create(request(NOW.minusSeconds(60), NOW.plus(Duration.ofMinutes(30)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service.create(request(NOW, NOW.plus(Duration.ofMinutes(30)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
    }

    @Test
    void endMustBeAfterStart() {
        assertThatThrownBy(() -> service.create(request(START, START)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("after");
        assertThatThrownBy(() -> service.create(request(START, START.minusSeconds(1))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void durationLimitsAreEnforcedAndBoundariesAllowed() {
        assertThatThrownBy(() -> service.create(request(START, START.plus(Duration.ofMinutes(4)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at least");
        assertThatThrownBy(() -> service.create(request(START, START.plus(Duration.ofHours(8)).plusSeconds(1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at most");

        stubParties();
        stubSave();
        assertThat(service.create(request(START, START.plus(Duration.ofMinutes(5)))).id()).isNotNull();
        assertThat(service.create(request(START, START.plus(Duration.ofHours(8)))).id()).isNotNull();
    }

    // ---- reschedule ----------------------------------------------------------------------------

    @Test
    void rescheduleExcludesTheAppointmentItselfFromTheOverlapCheck() {
        Appointment existing = scheduled();
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(20L)).thenReturn(Optional.of(doctor));
        stubSave();
        when(appointmentRepository.exists(any(Specification.class))).thenReturn(false);

        // shift by 10 minutes: overlaps its own old slot, which must not count as a conflict
        Instant newStart = START.plus(Duration.ofMinutes(10));
        AppointmentResponse response = service.update(100L,
                new AppointmentUpdateRequest(20L, newStart, newStart.plus(Duration.ofMinutes(30)), "Follow-up", "n"));

        assertThat(response.startTime()).isEqualTo(newStart);
        assertThat(response.reason()).isEqualTo("Follow-up");
        assertThat(response.status()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void rescheduleToAnotherDoctorIsCheckedAgainstThatDoctor() {
        Doctor other = Doctor.builder().firstName("Lisa").lastName("Cuddy").licenseNumber("MD-2")
                .specialty(doctor.getSpecialty()).build();
        other.setId(21L);
        Appointment existing = scheduled();
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(21L)).thenReturn(Optional.of(other));
        when(appointmentRepository.exists(any(Specification.class))).thenReturn(true);

        assertThatThrownBy(() -> service.update(100L, new AppointmentUpdateRequest(21L, START, END, "x", null)))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("doctor");
        assertThat(existing.getDoctor()).isSameAs(doctor);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    void onlyScheduledAppointmentsCanBeUpdated(AppointmentStatus status) {
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(withStatus(status)));

        assertThatThrownBy(() -> service.update(100L, new AppointmentUpdateRequest(20L, START, END, "x", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("SCHEDULED");
    }

    @Test
    void rescheduleToThePastIsRejected() {
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(scheduled()));

        assertThatThrownBy(() -> service.update(100L,
                new AppointmentUpdateRequest(20L, NOW.minusSeconds(3600), NOW.minusSeconds(1800), "x", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
    }

    // ---- status transitions --------------------------------------------------------------------

    @Test
    void cancelStoresReasonAndFreesTheSlot() {
        Appointment existing = scheduled();
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(existing));
        stubSave();

        AppointmentResponse response = service.cancel(100L, new CancelAppointmentRequest(" Patient called "));

        assertThat(response.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Patient called");
    }

    @Test
    void noShowMovesScheduledToNoShow() {
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(scheduled()));
        stubSave();

        assertThat(service.markNoShow(100L).status()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void adminCanComplete() {
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(scheduled()));
        stubSave();

        assertThat(service.complete(100L, "admin", true).status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void assignedDoctorCanComplete() {
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(scheduled()));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        stubSave();

        assertThat(service.complete(100L, "dr.house", false).status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void otherDoctorCannotComplete() {
        Doctor other = Doctor.builder().licenseNumber("MD-2").build();
        other.setId(21L);
        Appointment existing = scheduled();
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.cuddy")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.complete(100L, "dr.cuddy", false)).isInstanceOf(AccessDeniedException.class);
        assertThat(existing.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void userWithoutDoctorProfileCannotComplete() {
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(scheduled()));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nurse")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(100L, "nurse", false)).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    void terminalStatusesRejectEveryFurtherChange(AppointmentStatus status) {
        Appointment terminal = withStatus(status);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(terminal));

        assertThatThrownBy(() -> service.cancel(100L, new CancelAppointmentRequest("x")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.complete(100L, "admin", true)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.markNoShow(100L)).isInstanceOf(BusinessRuleException.class);
        assertThat(terminal.getStatus()).isEqualTo(status);
        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownAppointmentIs404() {
        when(appointmentRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.markNoShow(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- search --------------------------------------------------------------------------------

    @Test
    void searchMapsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(scheduled()), pageable, 1));

        Page<AppointmentResponse> page = service.search(new AppointmentSearchCriteria(20L, null, null, null, null), pageable);

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void myAppointmentsAreRestrictedToTheLinkedDoctor() {
        Pageable pageable = PageRequest.of(0, 10);
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(scheduled()), pageable, 1));

        // a doctorId supplied by the caller must be ignored
        Page<AppointmentResponse> page = service.searchForDoctorUser("dr.house",
                new AppointmentSearchCriteria(999L, null, AppointmentStatus.SCHEDULED, null, null), pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        ArgumentCaptor<Specification<Appointment>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(appointmentRepository).findAll(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void myAppointmentsIs404WithoutLinkedProfile() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchForDoctorUser("nobody", null, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
