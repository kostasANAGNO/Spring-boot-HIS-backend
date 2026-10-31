package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.AdmissionRequest;
import com.kostasanagno.his.dto.BedAssignmentResponse;
import com.kostasanagno.his.dto.DischargeRequest;
import com.kostasanagno.his.dto.HospitalizationResponse;
import com.kostasanagno.his.dto.HospitalizationSearchCriteria;
import com.kostasanagno.his.dto.HospitalizationUpdateRequest;
import com.kostasanagno.his.dto.TransferRequest;
import com.kostasanagno.his.entity.Appointment;
import com.kostasanagno.his.entity.Bed;
import com.kostasanagno.his.entity.BedAssignment;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.HospitalizationStatus;
import com.kostasanagno.his.entity.Patient;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.entity.Room;
import com.kostasanagno.his.entity.Specialty;
import com.kostasanagno.his.entity.Ward;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.BedMapperImpl;
import com.kostasanagno.his.mapper.HospitalizationMapperImpl;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.BedRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.security.Caller;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HospitalizationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant ADMITTED_AT = NOW.minus(Duration.ofHours(2));

    @Mock
    private HospitalizationRepository hospitalizationRepository;
    @Mock
    private BedAssignmentRepository bedAssignmentRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private BedRepository bedRepository;
    @Mock
    private AppointmentRepository appointmentRepository;

    private HospitalizationServiceImpl service;
    private Patient patient;
    private Doctor doctor;
    private Ward ward;
    private Room room;
    private Bed bed;
    private Bed otherBed;
    private final Caller admin = new Caller("admin", Set.of(Role.ADMIN));
    private final Caller nurse = new Caller("nurse", Set.of(Role.NURSE));
    private final Caller receptionist = new Caller("reception", Set.of(Role.RECEPTIONIST));

    @BeforeEach
    void setUp() {
        var bedMapper = new BedMapperImpl();
        var hospitalizationMapper = new HospitalizationMapperImpl();
        ReflectionTestUtils.setField(hospitalizationMapper, "bedMapper", bedMapper);
        service = new HospitalizationServiceImpl(hospitalizationRepository, bedAssignmentRepository,
                patientRepository, doctorRepository, bedRepository, appointmentRepository, hospitalizationMapper,
                bedMapper, Clock.fixed(NOW, ZoneOffset.UTC));

        Specialty cardiology = Specialty.builder().name("Cardiology").build();
        cardiology.setId(1L);
        patient = Patient.builder().firstName("Maria").lastName("Papadopoulou").nationalId("1").build();
        patient.setId(10L);
        doctor = Doctor.builder().firstName("Gregory").lastName("House").licenseNumber("MD-1")
                .specialty(cardiology).build();
        doctor.setId(20L);
        ward = Ward.builder().name("Cardiology").build();
        ward.setId(1L);
        room = Room.builder().ward(ward).roomNumber("101").build();
        room.setId(2L);
        bed = Bed.builder().room(room).bedNumber("A").build();
        bed.setId(30L);
        otherBed = Bed.builder().room(room).bedNumber("B").build();
        otherBed.setId(31L);

        // saving returns the same instance; ids are assigned where a test needs them
        lenient().when(hospitalizationRepository.saveAndFlush(any(Hospitalization.class))).thenAnswer(inv -> {
            Hospitalization h = inv.getArgument(0);
            if (h.getId() == null) {
                h.setId(40L);
            }
            return h;
        });
        lenient().when(bedAssignmentRepository.saveAndFlush(any(BedAssignment.class))).thenAnswer(inv -> {
            BedAssignment a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(50L);
            }
            return a;
        });
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private AdmissionRequest admission() {
        return new AdmissionRequest(10L, 20L, 30L, "Chest pain", "Suspected angina", null, null);
    }

    private void stubAdmissionLookups() {
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(20L)).thenReturn(Optional.of(doctor));
        when(bedRepository.findById(30L)).thenReturn(Optional.of(bed));
    }

    private Hospitalization admitted() {
        Hospitalization h = Hospitalization.builder().patient(patient).attendingDoctor(doctor)
                .status(HospitalizationStatus.ADMITTED).admissionTime(ADMITTED_AT)
                .admissionReason("Chest pain").diagnosis("Suspected angina").build();
        h.setId(40L);
        return h;
    }

    private Hospitalization discharged() {
        Hospitalization h = admitted();
        h.setStatus(HospitalizationStatus.DISCHARGED);
        h.setDischargeTime(NOW.minusSeconds(60));
        return h;
    }

    private BedAssignment currentAssignment(Hospitalization h) {
        BedAssignment a = BedAssignment.builder().hospitalization(h).bed(bed).startTime(ADMITTED_AT).build();
        a.setId(50L);
        return a;
    }

    private void stubAdmitted(Hospitalization h, BedAssignment current) {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(h));
        when(bedAssignmentRepository.findByHospitalizationIdAndEndTimeIsNull(40L)).thenReturn(Optional.ofNullable(current));
    }

    // ---- admission -----------------------------------------------------------------------------

    @Test
    void admitCreatesHospitalizationAndFirstAssignment() {
        stubAdmissionLookups();

        HospitalizationResponse response = service.admit(admission(), nurse);

        assertThat(response.id()).isEqualTo(40L);
        assertThat(response.status()).isEqualTo(HospitalizationStatus.ADMITTED);
        assertThat(response.admissionTime()).isEqualTo(NOW);
        assertThat(response.patient().fullName()).isEqualTo("Maria Papadopoulou");
        assertThat(response.attendingDoctor().specialty()).isEqualTo("Cardiology");
        assertThat(response.currentBed().bedNumber()).isEqualTo("A");
        assertThat(response.currentBed().roomNumber()).isEqualTo("101");
        assertThat(response.currentBed().wardName()).isEqualTo("Cardiology");
        assertThat(response.diagnosis()).isEqualTo("Suspected angina");
        ArgumentCaptor<BedAssignment> captor = ArgumentCaptor.forClass(BedAssignment.class);
        verify(bedAssignmentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStartTime()).isEqualTo(NOW);
        assertThat(captor.getValue().getEndTime()).isNull();
    }

    @Test
    void admitUsesAGivenPastAdmissionTime() {
        stubAdmissionLookups();
        Instant earlier = NOW.minus(Duration.ofDays(1));

        HospitalizationResponse response = service.admit(
                new AdmissionRequest(10L, 20L, 30L, "Chest pain", null, earlier, null), admin);

        assertThat(response.admissionTime()).isEqualTo(earlier);
    }

    @Test
    void admitRejectsFutureAdmissionTime() {
        assertThatThrownBy(() -> service.admit(new AdmissionRequest(10L, 20L, 30L, "x", null,
                NOW.plusSeconds(1), null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
    }

    @Test
    void admitRejectsMissingPatientDoctorOrBed() {
        when(patientRepository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.admit(admission(), admin))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Patient");

        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(20L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.admit(admission(), admin))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Doctor");

        when(doctorRepository.findById(20L)).thenReturn(Optional.of(doctor));
        when(bedRepository.findById(30L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.admit(admission(), admin))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Bed");
    }

    @Test
    void admitRejectsInactivePatient() {
        patient.setActive(false);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.admit(admission(), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Patient");
    }

    @Test
    void admitRejectsInactiveDoctor() {
        doctor.setActive(false);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(20L)).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> service.admit(admission(), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Doctor");
    }

    @Test
    void admitRejectsInactiveBedRoomOrWard() {
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(20L)).thenReturn(Optional.of(doctor));
        when(bedRepository.findById(30L)).thenReturn(Optional.of(bed));

        bed.setActive(false);
        assertThatThrownBy(() -> service.admit(admission(), admin)).isInstanceOf(BusinessRuleException.class);
        bed.setActive(true);
        room.setActive(false);
        assertThatThrownBy(() -> service.admit(admission(), admin)).isInstanceOf(BusinessRuleException.class);
        room.setActive(true);
        ward.setActive(false);
        assertThatThrownBy(() -> service.admit(admission(), admin)).isInstanceOf(BusinessRuleException.class);
        verify(hospitalizationRepository, never()).saveAndFlush(any());
    }

    @Test
    void admitRejectsPatientWhoIsAlreadyAdmitted() {
        stubAdmissionLookups();
        when(hospitalizationRepository.existsByPatientIdAndStatus(10L, HospitalizationStatus.ADMITTED)).thenReturn(true);

        assertThatThrownBy(() -> service.admit(admission(), admin))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("patient");
        verify(hospitalizationRepository, never()).saveAndFlush(any());
    }

    @Test
    void admitRejectsOccupiedBed() {
        stubAdmissionLookups();
        when(bedAssignmentRepository.isBedTakenFrom(30L, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service.admit(admission(), admin))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("bed");
        verify(hospitalizationRepository, never()).saveAndFlush(any());
    }

    @Test
    void admitLinksAnAppointmentOfTheSamePatient() {
        stubAdmissionLookups();
        Appointment appointment = Appointment.builder().patient(patient).doctor(doctor).build();
        appointment.setId(60L);
        when(appointmentRepository.findById(60L)).thenReturn(Optional.of(appointment));

        HospitalizationResponse response = service.admit(
                new AdmissionRequest(10L, 20L, 30L, "Chest pain", null, null, 60L), admin);

        assertThat(response.appointmentId()).isEqualTo(60L);
    }

    @Test
    void admitRejectsAppointmentOfAnotherPatient() {
        stubAdmissionLookups();
        Patient other = Patient.builder().nationalId("2").build();
        other.setId(11L);
        Appointment appointment = Appointment.builder().patient(other).doctor(doctor).build();
        appointment.setId(60L);
        when(appointmentRepository.findById(60L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.admit(
                new AdmissionRequest(10L, 20L, 30L, "x", null, null, 60L), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("different patient");
    }

    @Test
    void admitRejectsAppointmentAlreadyLinkedAndUnknownAppointment() {
        stubAdmissionLookups();
        Appointment appointment = Appointment.builder().patient(patient).doctor(doctor).build();
        appointment.setId(60L);
        when(appointmentRepository.findById(60L)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.findById(61L)).thenReturn(Optional.empty());
        when(hospitalizationRepository.existsByAppointmentId(60L)).thenReturn(true);

        assertThatThrownBy(() -> service.admit(new AdmissionRequest(10L, 20L, 30L, "x", null, null, 60L), admin))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("already linked");
        assertThatThrownBy(() -> service.admit(new AdmissionRequest(10L, 20L, 30L, "x", null, null, 61L), admin))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- transfer ------------------------------------------------------------------------------

    @Test
    void transferClosesCurrentAssignmentAndOpensANewOneAtTheSameInstant() {
        Hospitalization h = admitted();
        BedAssignment current = currentAssignment(h);
        stubAdmitted(h, current);
        when(bedRepository.findById(31L)).thenReturn(Optional.of(otherBed));

        HospitalizationResponse response = service.transfer(40L, new TransferRequest(31L, " Needs isolation "), nurse);

        assertThat(current.getEndTime()).isEqualTo(NOW);
        ArgumentCaptor<BedAssignment> captor = ArgumentCaptor.forClass(BedAssignment.class);
        verify(bedAssignmentRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        BedAssignment next = captor.getAllValues().get(1);
        assertThat(next.getStartTime()).isEqualTo(NOW);
        assertThat(next.getEndTime()).isNull();
        assertThat(next.getReason()).isEqualTo("Needs isolation");
        assertThat(response.currentBed().bedNumber()).isEqualTo("B");
    }

    @Test
    void transferRejectsSameBed() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));
        when(bedRepository.findById(30L)).thenReturn(Optional.of(bed));

        assertThatThrownBy(() -> service.transfer(40L, new TransferRequest(30L, null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already in this bed");
    }

    @Test
    void transferRejectsOccupiedBed() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));
        when(bedRepository.findById(31L)).thenReturn(Optional.of(otherBed));
        when(bedAssignmentRepository.isBedTakenFrom(31L, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service.transfer(40L, new TransferRequest(31L, null), admin))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("bed");
        verify(bedAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void transferRejectsInactiveTargetBed() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));
        otherBed.setActive(false);
        when(bedRepository.findById(31L)).thenReturn(Optional.of(otherBed));

        assertThatThrownBy(() -> service.transfer(40L, new TransferRequest(31L, null), admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void transferRejectsDischargedHospitalization() {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(discharged()));

        assertThatThrownBy(() -> service.transfer(40L, new TransferRequest(31L, null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ADMITTED");
    }

    @Test
    void transferRejectsAMoveRightAtTheStartOfTheCurrentAssignment() {
        Hospitalization h = admitted();
        BedAssignment current = currentAssignment(h);
        current.setStartTime(NOW);
        stubAdmitted(h, current);
        when(bedRepository.findById(31L)).thenReturn(Optional.of(otherBed));

        assertThatThrownBy(() -> service.transfer(40L, new TransferRequest(31L, null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("too recently");
    }

    // ---- discharge -----------------------------------------------------------------------------

    @Test
    void adminDischargesAndTheOpenAssignmentIsClosed() {
        Hospitalization h = admitted();
        BedAssignment current = currentAssignment(h);
        stubAdmitted(h, current);

        HospitalizationResponse response = service.discharge(40L, new DischargeRequest(" Recovered ", null), admin);

        assertThat(response.status()).isEqualTo(HospitalizationStatus.DISCHARGED);
        assertThat(response.dischargeTime()).isEqualTo(NOW);
        assertThat(response.dischargeNotes()).isEqualTo("Recovered");
        assertThat(response.currentBed()).isNull();
        assertThat(current.getEndTime()).isEqualTo(NOW);
    }

    @Test
    void attendingDoctorCanDischargeWithAnExplicitPastTime() {
        Hospitalization h = admitted();
        BedAssignment current = currentAssignment(h);
        stubAdmitted(h, current);
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        Instant at = NOW.minus(Duration.ofMinutes(30));

        HospitalizationResponse response = service.discharge(40L, new DischargeRequest("Recovered", at),
                new Caller("dr.house", Set.of(Role.DOCTOR)));

        assertThat(response.dischargeTime()).isEqualTo(at);
        assertThat(current.getEndTime()).isEqualTo(at);
    }

    @Test
    void anotherDoctorCannotDischarge() {
        Doctor other = Doctor.builder().licenseNumber("MD-2").build();
        other.setId(21L);
        Hospitalization h = admitted();
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(h));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.other")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.discharge(40L, new DischargeRequest("x", null),
                new Caller("dr.other", Set.of(Role.DOCTOR)))).isInstanceOf(AccessDeniedException.class);
        assertThat(h.getStatus()).isEqualTo(HospitalizationStatus.ADMITTED);
        verify(bedAssignmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void userWithoutDoctorProfileCannotDischarge() {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(admitted()));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nurse")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.discharge(40L, new DischargeRequest("x", null), nurse))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dischargedHospitalizationRejectsEveryFurtherChange() {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(discharged()));

        assertThatThrownBy(() -> service.discharge(40L, new DischargeRequest("x", null), admin))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.update(40L, new HospitalizationUpdateRequest(20L, "x", null), admin))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.transfer(40L, new TransferRequest(31L, null), admin))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void dischargeTimeRules() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));

        assertThatThrownBy(() -> service.discharge(40L, new DischargeRequest("x", NOW.plusSeconds(1)), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service.discharge(40L, new DischargeRequest("x", ADMITTED_AT), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("after");
    }

    @Test
    void dischargeMustBeAfterTheCurrentBedAssignmentStarted() {
        Hospitalization h = admitted();
        BedAssignment current = currentAssignment(h);
        current.setStartTime(NOW.minus(Duration.ofMinutes(10)));
        stubAdmitted(h, current);

        assertThatThrownBy(() -> service.discharge(40L,
                new DischargeRequest("x", NOW.minus(Duration.ofMinutes(20))), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("current bed");
    }

    // ---- update --------------------------------------------------------------------------------

    @Test
    void updateChangesReasonDiagnosisAndAttendingDoctor() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));
        Doctor other = Doctor.builder().firstName("Lisa").lastName("Cuddy").licenseNumber("MD-2")
                .specialty(doctor.getSpecialty()).build();
        other.setId(21L);
        when(doctorRepository.findById(21L)).thenReturn(Optional.of(other));

        HospitalizationResponse response = service.update(40L,
                new HospitalizationUpdateRequest(21L, " New reason ", "New diagnosis"), admin);

        assertThat(response.admissionReason()).isEqualTo("New reason");
        assertThat(response.diagnosis()).isEqualTo("New diagnosis");
        assertThat(response.attendingDoctor().id()).isEqualTo(21L);
        assertThat(response.admissionTime()).isEqualTo(ADMITTED_AT);
        assertThat(response.status()).isEqualTo(HospitalizationStatus.ADMITTED);
    }

    // ---- privacy -------------------------------------------------------------------------------

    @Test
    void receptionistDoesNotSeeClinicalFields() {
        Hospitalization h = discharged();
        h.setDischargeNotes("Secret notes");
        stubAdmitted(h, null);

        HospitalizationResponse response = service.getById(40L, receptionist);

        assertThat(response.diagnosis()).isNull();
        assertThat(response.dischargeNotes()).isNull();
        assertThat(response.admissionReason()).isEqualTo("Chest pain");
        assertThat(response.patient().fullName()).isEqualTo("Maria Papadopoulou");
    }

    @Test
    void clinicalRolesSeeClinicalFields() {
        Hospitalization h = discharged();
        h.setDischargeNotes("Secret notes");
        stubAdmitted(h, null);

        for (Caller caller : List.of(admin, nurse, new Caller("d", Set.of(Role.DOCTOR)))) {
            HospitalizationResponse response = service.getById(40L, caller);
            assertThat(response.diagnosis()).isEqualTo("Suspected angina");
            assertThat(response.dischargeNotes()).isEqualTo("Secret notes");
        }
    }

    @Test
    void userWithReceptionistAndNurseRolesCanSeeClinicalFields() {
        stubAdmitted(admitted(), null);

        HospitalizationResponse response = service.getById(40L, new Caller("x", Set.of(Role.RECEPTIONIST, Role.NURSE)));

        assertThat(response.diagnosis()).isEqualTo("Suspected angina");
    }

    @Test
    void admittingAsReceptionistDoesNotEchoTheDiagnosis() {
        stubAdmissionLookups();

        HospitalizationResponse response = service.admit(admission(), receptionist);

        assertThat(response.diagnosis()).isNull();
        ArgumentCaptor<Hospitalization> captor = ArgumentCaptor.forClass(Hospitalization.class);
        verify(hospitalizationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getDiagnosis()).isEqualTo("Suspected angina"); // stored, just not shown
    }

    @Test
    void bedHistoryHidesTransferReasonFromReceptionists() {
        Hospitalization h = admitted();
        BedAssignment first = currentAssignment(h);
        first.setEndTime(NOW);
        BedAssignment second = BedAssignment.builder().hospitalization(h).bed(otherBed).startTime(NOW)
                .reason("Needs isolation").build();
        second.setId(51L);
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(h));
        when(bedAssignmentRepository.findByHospitalizationIdOrderByStartTimeAsc(40L)).thenReturn(List.of(first, second));

        List<BedAssignmentResponse> forNurse = service.getBedHistory(40L, nurse);
        List<BedAssignmentResponse> forReceptionist = service.getBedHistory(40L, receptionist);

        assertThat(forNurse).hasSize(2);
        assertThat(forNurse.get(0).bed().bedNumber()).isEqualTo("A");
        assertThat(forNurse.get(0).endTime()).isEqualTo(NOW);
        assertThat(forNurse.get(1).endTime()).isNull();
        assertThat(forNurse.get(1).reason()).isEqualTo("Needs isolation");
        assertThat(forReceptionist.get(1).reason()).isNull();
    }

    // ---- reads ---------------------------------------------------------------------------------

    @Test
    void unknownHospitalizationIs404() {
        when(hospitalizationRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L, admin)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getBedHistory(9L, admin)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchAddsCurrentBedsWithOneExtraQuery() {
        Hospitalization h = admitted();
        Pageable pageable = PageRequest.of(0, 10);
        when(hospitalizationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(h), pageable, 1));
        when(bedAssignmentRepository.findByHospitalizationIdInAndEndTimeIsNull(List.of(40L)))
                .thenReturn(List.of(currentAssignment(h)));

        Page<HospitalizationResponse> page = service.search(
                new HospitalizationSearchCriteria(null, null, 1L, null, null, null, null), pageable, nurse);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).currentBed().wardName()).isEqualTo("Cardiology");
    }

    @Test
    void myHospitalizationsAreRestrictedToTheLinkedDoctor() {
        Pageable pageable = PageRequest.of(0, 10);
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        when(hospitalizationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<HospitalizationResponse> page = service.searchForDoctorUser(
                new HospitalizationSearchCriteria(null, 999L, null, null, null, null, null), pageable,
                new Caller("dr.house", Set.of(Role.DOCTOR)));

        assertThat(page.getTotalElements()).isZero();
        verify(bedAssignmentRepository, never()).findByHospitalizationIdInAndEndTimeIsNull(any());
    }

    @Test
    void myHospitalizationsIs404WithoutLinkedProfile() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchForDoctorUser(null, PageRequest.of(0, 10),
                new Caller("nobody", Set.of(Role.DOCTOR)))).isInstanceOf(ResourceNotFoundException.class);
        verify(hospitalizationRepository, never()).findAll(any(Specification.class), any(Pageable.class));
        verify(bedAssignmentRepository, never()).isBedTakenFrom(anyLong(), any());
    }
}
