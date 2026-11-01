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
import com.kostasanagno.his.entity.AdmissionRecord;
import com.kostasanagno.his.entity.AdmissionSource;
import com.kostasanagno.his.entity.AdmissionType;
import com.kostasanagno.his.entity.Appointment;
import com.kostasanagno.his.entity.Bed;
import com.kostasanagno.his.entity.BedAssignment;
import com.kostasanagno.his.entity.Clinic;
import com.kostasanagno.his.entity.DischargeRecord;
import com.kostasanagno.his.entity.DischargeType;
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
import com.kostasanagno.his.repository.AdmissionRecordRepository;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.BedRepository;
import com.kostasanagno.his.repository.DischargeRecordRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.security.Caller;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
    private AdmissionRecordRepository admissionRecordRepository;
    @Mock
    private DischargeRecordRepository dischargeRecordRepository;
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
    private Clinic clinic;
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
        service = new HospitalizationServiceImpl(hospitalizationRepository, admissionRecordRepository,
                dischargeRecordRepository, bedAssignmentRepository, patientRepository, doctorRepository, bedRepository,
                appointmentRepository, hospitalizationMapper, bedMapper, Clock.fixed(NOW, ZoneOffset.UTC));

        Specialty cardiology = Specialty.builder().name("Cardiology").build();
        cardiology.setId(1L);
        patient = Patient.builder().firstName("Maria").lastName("Papadopoulou").nationalId("1").build();
        patient.setId(10L);
        doctor = Doctor.builder().firstName("Gregory").lastName("House").licenseNumber("MD-1")
                .specialty(cardiology).build();
        doctor.setId(20L);
        clinic = Clinic.builder().name("Cardiology").build();
        clinic.setId(9L);
        ward = Ward.builder().clinic(clinic).name("Main Wing").build();
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
        lenient().when(admissionRecordRepository.saveAndFlush(any(AdmissionRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dischargeRecordRepository.saveAndFlush(any(DischargeRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));
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
        return new AdmissionRequest(10L, 20L, 30L, AdmissionType.ELECTIVE, AdmissionSource.OTHER, null,
                "Chest pain", "i20.9", "Suspected angina", null, null);
    }

    private AdmissionRequest admission(AdmissionType type, AdmissionSource source, String facility, Long appointmentId) {
        return new AdmissionRequest(10L, 20L, 30L, type, source, facility, "Chest pain", null, null, null,
                appointmentId);
    }

    private static DischargeRequest discharge(DischargeType type, String destination, LocalDate followUp, Instant at) {
        return new DischargeRequest(type, destination, "I20.0", "Stable angina", " Recovered ", "Rest", followUp, at);
    }

    private static DischargeRequest homeDischarge() {
        return discharge(DischargeType.HOME, null, null, null);
    }

    private void stubAdmissionLookups() {
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findById(20L)).thenReturn(Optional.of(doctor));
        when(bedRepository.findById(30L)).thenReturn(Optional.of(bed));
    }

    private Hospitalization admitted() {
        Hospitalization h = Hospitalization.builder().patient(patient).attendingDoctor(doctor)
                .status(HospitalizationStatus.ADMITTED).admissionTime(ADMITTED_AT).build();
        h.setId(40L);
        return h;
    }

    private Hospitalization discharged() {
        Hospitalization h = admitted();
        h.setStatus(HospitalizationStatus.DISCHARGED);
        h.setDischargeTime(NOW.minusSeconds(60));
        return h;
    }

    private AdmissionRecord admissionRecord(Hospitalization h) {
        AdmissionRecord record = AdmissionRecord.builder().hospitalization(h).admissionType(AdmissionType.EMERGENCY)
                .admissionSource(AdmissionSource.EMERGENCY_DEPARTMENT).reason("Chest pain")
                .initialDiagnosisCode("I20.9").initialDiagnosis("Suspected angina").build();
        record.setId(70L);
        return record;
    }

    private DischargeRecord dischargeRecord(Hospitalization h) {
        DischargeRecord record = DischargeRecord.builder().hospitalization(h).dischargeType(DischargeType.HOME)
                .finalDiagnosisCode("I20.0").finalDiagnosis("Stable angina").summary("Secret summary")
                .instructions("Rest").followUpDate(LocalDate.of(2026, 12, 1)).build();
        record.setId(80L);
        return record;
    }

    private BedAssignment currentAssignment(Hospitalization h) {
        BedAssignment a = BedAssignment.builder().hospitalization(h).bed(bed).startTime(ADMITTED_AT).build();
        a.setId(50L);
        return a;
    }

    private void stubAdmitted(Hospitalization h, BedAssignment current) {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(h));
        when(bedAssignmentRepository.findByHospitalizationIdAndEndTimeIsNull(40L)).thenReturn(Optional.ofNullable(current));
        lenient().when(admissionRecordRepository.findByHospitalizationId(40L)).thenReturn(Optional.of(admissionRecord(h)));
        lenient().when(dischargeRecordRepository.findByHospitalizationId(40L)).thenReturn(Optional.of(dischargeRecord(h)));
    }

    // ---- admission -----------------------------------------------------------------------------

    @Test
    void admitWritesTheHospitalizationTheAdmissionRecordAndTheFirstAssignment() {
        stubAdmissionLookups();

        HospitalizationResponse response = service.admit(admission(), nurse);

        assertThat(response.id()).isEqualTo(40L);
        assertThat(response.status()).isEqualTo(HospitalizationStatus.ADMITTED);
        assertThat(response.admissionTime()).isEqualTo(NOW);
        assertThat(response.discharge()).isNull();
        assertThat(response.patient().fullName()).isEqualTo("Maria Papadopoulou");
        assertThat(response.attendingDoctor().specialty()).isEqualTo("Cardiology");
        assertThat(response.currentBed().bedNumber()).isEqualTo("A");
        assertThat(response.currentBed().roomNumber()).isEqualTo("101");
        assertThat(response.currentBed().wardName()).isEqualTo("Main Wing");
        assertThat(response.currentBed().clinicName()).isEqualTo("Cardiology");
        var record = response.admission();
        assertThat(record.admissionType()).isEqualTo(AdmissionType.ELECTIVE);
        assertThat(record.admissionSource()).isEqualTo(AdmissionSource.OTHER);
        assertThat(record.reason()).isEqualTo("Chest pain");
        assertThat(record.initialDiagnosisCode()).isEqualTo("I20.9");           // normalised to upper case
        assertThat(record.initialDiagnosis()).isEqualTo("Suspected angina");
        ArgumentCaptor<BedAssignment> captor = ArgumentCaptor.forClass(BedAssignment.class);
        verify(bedAssignmentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStartTime()).isEqualTo(NOW);
        assertThat(captor.getValue().getEndTime()).isNull();
        verify(admissionRecordRepository).saveAndFlush(any(AdmissionRecord.class));
    }

    @Test
    void admitUsesAGivenPastAdmissionTime() {
        stubAdmissionLookups();
        Instant earlier = NOW.minus(Duration.ofDays(1));

        HospitalizationResponse response = service.admit(new AdmissionRequest(10L, 20L, 30L, AdmissionType.ELECTIVE,
                AdmissionSource.OTHER, null, "Chest pain", null, null, earlier, null), admin);

        assertThat(response.admissionTime()).isEqualTo(earlier);
    }

    @Test
    void admitRejectsFutureAdmissionTime() {
        assertThatThrownBy(() -> service.admit(new AdmissionRequest(10L, 20L, 30L, AdmissionType.ELECTIVE,
                AdmissionSource.OTHER, null, "x", null, null, NOW.plusSeconds(1), null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
    }

    @Test
    void aTransferInNeedsTheReferringFacility() {
        assertThatThrownBy(() -> service.admit(admission(AdmissionType.TRANSFER_IN, AdmissionSource.REFERRAL, "  ", null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("referringFacility");
        verify(hospitalizationRepository, never()).saveAndFlush(any());

        stubAdmissionLookups();
        HospitalizationResponse response = service.admit(
                admission(AdmissionType.TRANSFER_IN, AdmissionSource.REFERRAL, " General Hospital of Patras ", null), admin);
        assertThat(response.admission().referringFacility()).isEqualTo("General Hospital of Patras");
    }

    @Test
    void theAppointmentSourceAndTheAppointmentLinkGoTogether() {
        assertThatThrownBy(() -> service.admit(
                admission(AdmissionType.ELECTIVE, AdmissionSource.APPOINTMENT, null, null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("appointmentId");
        assertThatThrownBy(() -> service.admit(
                admission(AdmissionType.ELECTIVE, AdmissionSource.REFERRAL, null, 60L), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("appointmentId");
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
    void admitRejectsAnInactiveBedRoomWardOrClinic() {
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
        ward.setActive(true);
        clinic.setActive(false);
        assertThatThrownBy(() -> service.admit(admission(), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("clinic");
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
                admission(AdmissionType.ELECTIVE, AdmissionSource.APPOINTMENT, null, 60L), admin);

        assertThat(response.appointmentId()).isEqualTo(60L);
        assertThat(response.admission().admissionSource()).isEqualTo(AdmissionSource.APPOINTMENT);
    }

    @Test
    void admitRejectsAppointmentOfAnotherPatient() {
        stubAdmissionLookups();
        Patient other = Patient.builder().nationalId("2").build();
        other.setId(11L);
        Appointment appointment = Appointment.builder().patient(other).doctor(doctor).build();
        appointment.setId(60L);
        when(appointmentRepository.findById(60L)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.admit(admission(AdmissionType.ELECTIVE, AdmissionSource.APPOINTMENT, null, 60L), admin))
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

        assertThatThrownBy(() -> service.admit(admission(AdmissionType.ELECTIVE, AdmissionSource.APPOINTMENT, null, 60L), admin))
                .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("already linked");
        assertThatThrownBy(() -> service.admit(admission(AdmissionType.ELECTIVE, AdmissionSource.APPOINTMENT, null, 61L), admin))
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
        assertThat(response.admission().reason()).isEqualTo("Chest pain");
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
    void adminDischargesWritesTheDischargeRecordAndClosesTheAssignment() {
        Hospitalization h = admitted();
        BedAssignment current = currentAssignment(h);
        stubAdmitted(h, current);

        HospitalizationResponse response = service.discharge(40L, homeDischarge(), admin);

        assertThat(response.status()).isEqualTo(HospitalizationStatus.DISCHARGED);
        assertThat(response.dischargeTime()).isEqualTo(NOW);
        assertThat(response.currentBed()).isNull();
        assertThat(current.getEndTime()).isEqualTo(NOW);
        var record = response.discharge();
        assertThat(record.dischargeType()).isEqualTo(DischargeType.HOME);
        assertThat(record.summary()).isEqualTo("Recovered");                    // trimmed
        assertThat(record.finalDiagnosisCode()).isEqualTo("I20.0");
        assertThat(record.finalDiagnosis()).isEqualTo("Stable angina");
        assertThat(record.instructions()).isEqualTo("Rest");
        assertThat(response.admission().reason()).isEqualTo("Chest pain");        // the admission record travels with it
        verify(dischargeRecordRepository).saveAndFlush(any(DischargeRecord.class));
    }

    @Test
    void attendingDoctorCanDischargeWithAnExplicitPastTime() {
        Hospitalization h = admitted();
        BedAssignment current = currentAssignment(h);
        stubAdmitted(h, current);
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        Instant at = NOW.minus(Duration.ofMinutes(30));

        HospitalizationResponse response = service.discharge(40L,
                discharge(DischargeType.HOME, null, null, at), new Caller("dr.house", Set.of(Role.DOCTOR)));

        assertThat(response.dischargeTime()).isEqualTo(at);
        assertThat(current.getEndTime()).isEqualTo(at);
    }

    @Test
    void aTransferOutNeedsADestinationAndOtherTypesMustNotHaveOne() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));

        assertThatThrownBy(() -> service.discharge(40L, discharge(DischargeType.TRANSFER_OUT, null, null, null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("required");
        assertThatThrownBy(() -> service.discharge(40L, discharge(DischargeType.HOME, "Some Clinic", null, null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("only allowed");

        HospitalizationResponse response = service.discharge(40L,
                discharge(DischargeType.TRANSFER_OUT, " University Hospital ", null, null), admin);
        assertThat(response.discharge().destinationFacility()).isEqualTo("University Hospital");
    }

    @Test
    void theFollowUpDateRules() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));

        assertThatThrownBy(() -> service.discharge(40L,
                discharge(DischargeType.HOME, null, LocalDate.of(2026, 11, 9), null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past");
        assertThatThrownBy(() -> service.discharge(40L,
                discharge(DischargeType.DECEASED, null, LocalDate.of(2026, 12, 1), null), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("DECEASED");
        assertThat(h.getStatus()).isEqualTo(HospitalizationStatus.ADMITTED);        // nothing was changed

        HospitalizationResponse today = service.discharge(40L,
                discharge(DischargeType.HOME, null, LocalDate.of(2026, 11, 10), null), admin);
        assertThat(today.discharge().followUpDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void anotherDoctorCannotDischarge() {
        Doctor other = Doctor.builder().licenseNumber("MD-2").build();
        other.setId(21L);
        Hospitalization h = admitted();
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(h));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.other")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.discharge(40L, homeDischarge(), new Caller("dr.other", Set.of(Role.DOCTOR))))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(h.getStatus()).isEqualTo(HospitalizationStatus.ADMITTED);
        verify(bedAssignmentRepository, never()).saveAndFlush(any());
        verify(dischargeRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void userWithoutDoctorProfileCannotDischarge() {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(admitted()));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nurse")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.discharge(40L, homeDischarge(), nurse))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dischargedHospitalizationRejectsEveryFurtherChange() {
        when(hospitalizationRepository.findById(40L)).thenReturn(Optional.of(discharged()));

        assertThatThrownBy(() -> service.discharge(40L, homeDischarge(), admin))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.update(40L,
                new HospitalizationUpdateRequest(20L, "x", null, null), admin))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.transfer(40L, new TransferRequest(31L, null), admin))
                .isInstanceOf(BusinessRuleException.class);
        verify(dischargeRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void dischargeTimeRules() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));

        assertThatThrownBy(() -> service.discharge(40L,
                discharge(DischargeType.HOME, null, null, NOW.plusSeconds(1)), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service.discharge(40L,
                discharge(DischargeType.HOME, null, null, ADMITTED_AT), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("after");
        assertThat(h.getStatus()).isEqualTo(HospitalizationStatus.ADMITTED);
    }

    @Test
    void dischargeMustBeAfterTheCurrentBedAssignmentStarted() {
        Hospitalization h = admitted();
        BedAssignment current = currentAssignment(h);
        current.setStartTime(NOW.minus(Duration.ofMinutes(10)));
        stubAdmitted(h, current);

        assertThatThrownBy(() -> service.discharge(40L,
                discharge(DischargeType.HOME, null, null, NOW.minus(Duration.ofMinutes(20))), admin))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("current bed");
    }

    // ---- update --------------------------------------------------------------------------------

    @Test
    void updateCorrectsTheAdmissionRecordAndTheAttendingDoctorButNotItsFacts() {
        Hospitalization h = admitted();
        stubAdmitted(h, currentAssignment(h));
        Doctor other = Doctor.builder().firstName("Lisa").lastName("Cuddy").licenseNumber("MD-2")
                .specialty(doctor.getSpecialty()).build();
        other.setId(21L);
        when(doctorRepository.findById(21L)).thenReturn(Optional.of(other));

        HospitalizationResponse response = service.update(40L,
                new HospitalizationUpdateRequest(21L, " New reason ", "i21.0", "New diagnosis"), admin);

        assertThat(response.admission().reason()).isEqualTo("New reason");
        assertThat(response.admission().initialDiagnosisCode()).isEqualTo("I21.0");
        assertThat(response.admission().initialDiagnosis()).isEqualTo("New diagnosis");
        assertThat(response.admission().admissionType()).isEqualTo(AdmissionType.EMERGENCY);   // as written
        assertThat(response.admission().admissionSource()).isEqualTo(AdmissionSource.EMERGENCY_DEPARTMENT);
        assertThat(response.attendingDoctor().id()).isEqualTo(21L);
        assertThat(response.admissionTime()).isEqualTo(ADMITTED_AT);
        assertThat(response.status()).isEqualTo(HospitalizationStatus.ADMITTED);
    }

    // ---- privacy -------------------------------------------------------------------------------

    @Test
    void receptionistDoesNotSeeClinicalFieldsOfEitherDocument() {
        Hospitalization h = discharged();
        stubAdmitted(h, null);

        HospitalizationResponse response = service.getById(40L, receptionist);

        assertThat(response.admission().initialDiagnosis()).isNull();
        assertThat(response.admission().initialDiagnosisCode()).isNull();
        assertThat(response.discharge().finalDiagnosis()).isNull();
        assertThat(response.discharge().finalDiagnosisCode()).isNull();
        assertThat(response.discharge().summary()).isNull();
        assertThat(response.discharge().instructions()).isNull();
        // administrative data stays visible
        assertThat(response.admission().reason()).isEqualTo("Chest pain");
        assertThat(response.admission().admissionType()).isEqualTo(AdmissionType.EMERGENCY);
        assertThat(response.discharge().dischargeType()).isEqualTo(DischargeType.HOME);
        assertThat(response.discharge().followUpDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(response.patient().fullName()).isEqualTo("Maria Papadopoulou");
    }

    @Test
    void clinicalRolesSeeClinicalFields() {
        Hospitalization h = discharged();
        stubAdmitted(h, null);

        for (Caller caller : List.of(admin, nurse, new Caller("d", Set.of(Role.DOCTOR)))) {
            HospitalizationResponse response = service.getById(40L, caller);
            assertThat(response.admission().initialDiagnosis()).isEqualTo("Suspected angina");
            assertThat(response.discharge().summary()).isEqualTo("Secret summary");
            assertThat(response.discharge().finalDiagnosis()).isEqualTo("Stable angina");
            assertThat(response.discharge().instructions()).isEqualTo("Rest");
        }
    }

    @Test
    void userWithReceptionistAndNurseRolesCanSeeClinicalFields() {
        stubAdmitted(admitted(), null);

        HospitalizationResponse response = service.getById(40L, new Caller("x", Set.of(Role.RECEPTIONIST, Role.NURSE)));

        assertThat(response.admission().initialDiagnosis()).isEqualTo("Suspected angina");
    }

    @Test
    void admittingAsReceptionistDoesNotEchoTheDiagnosis() {
        stubAdmissionLookups();

        HospitalizationResponse response = service.admit(admission(), receptionist);

        assertThat(response.admission().initialDiagnosis()).isNull();
        assertThat(response.admission().initialDiagnosisCode()).isNull();
        ArgumentCaptor<AdmissionRecord> captor = ArgumentCaptor.forClass(AdmissionRecord.class);
        verify(admissionRecordRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getInitialDiagnosis()).isEqualTo("Suspected angina");   // stored, just not shown
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
    void anAdmittedStayHasNoDischargeRecord() {
        stubAdmitted(admitted(), null);

        assertThat(service.getById(40L, admin).discharge()).isNull();
        verify(dischargeRecordRepository, never()).findByHospitalizationId(anyLong());
    }

    @Test
    void searchAddsBedsAndBothDocumentsWithOneQueryEach() {
        Hospitalization h = discharged();
        Pageable pageable = PageRequest.of(0, 10);
        when(hospitalizationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(h), pageable, 1));
        when(bedAssignmentRepository.findByHospitalizationIdInAndEndTimeIsNull(List.of(40L)))
                .thenReturn(List.of(currentAssignment(h)));
        when(admissionRecordRepository.findByHospitalizationIdIn(List.of(40L))).thenReturn(List.of(admissionRecord(h)));
        when(dischargeRecordRepository.findByHospitalizationIdIn(List.of(40L))).thenReturn(List.of(dischargeRecord(h)));

        Page<HospitalizationResponse> page = service.search(
                new HospitalizationSearchCriteria(null, null, null, 1L, null, null, null, null), pageable, nurse);

        assertThat(page.getContent()).hasSize(1);
        HospitalizationResponse row = page.getContent().get(0);
        assertThat(row.currentBed().wardName()).isEqualTo("Main Wing");
        assertThat(row.admission().reason()).isEqualTo("Chest pain");
        assertThat(row.discharge().dischargeType()).isEqualTo(DischargeType.HOME);
    }

    @Test
    void searchRedactsEveryRowForReceptionists() {
        Hospitalization h = discharged();
        Pageable pageable = PageRequest.of(0, 10);
        when(hospitalizationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(h), pageable, 1));
        when(bedAssignmentRepository.findByHospitalizationIdInAndEndTimeIsNull(List.of(40L))).thenReturn(List.of());
        when(admissionRecordRepository.findByHospitalizationIdIn(List.of(40L))).thenReturn(List.of(admissionRecord(h)));
        when(dischargeRecordRepository.findByHospitalizationIdIn(List.of(40L))).thenReturn(List.of(dischargeRecord(h)));

        HospitalizationResponse row = service.search(null, pageable, receptionist).getContent().get(0);

        assertThat(row.admission().initialDiagnosis()).isNull();
        assertThat(row.discharge().summary()).isNull();
        assertThat(row.discharge().dischargeType()).isEqualTo(DischargeType.HOME);
    }

    @Test
    void myHospitalizationsAreRestrictedToTheLinkedDoctor() {
        Pageable pageable = PageRequest.of(0, 10);
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(doctor));
        when(hospitalizationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<HospitalizationResponse> page = service.searchForDoctorUser(
                new HospitalizationSearchCriteria(null, 999L, null, null, null, null, null, null), pageable,
                new Caller("dr.house", Set.of(Role.DOCTOR)));

        assertThat(page.getTotalElements()).isZero();
        verify(bedAssignmentRepository, never()).findByHospitalizationIdInAndEndTimeIsNull(any());
        verify(admissionRecordRepository, never()).findByHospitalizationIdIn(any());
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
