package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.ClinicalNoteAmendRequest;
import com.kostasanagno.his.dto.ClinicalNoteRequest;
import com.kostasanagno.his.dto.ClinicalNoteResponse;
import com.kostasanagno.his.dto.ClinicalNoteSearchCriteria;
import com.kostasanagno.his.dto.ClinicalNoteVersionResponse;
import com.kostasanagno.his.entity.Appointment;
import com.kostasanagno.his.entity.ClinicalNote;
import com.kostasanagno.his.entity.ClinicalNoteVersion;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.Patient;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.entity.Specialty;
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.ClinicalNoteMapperImpl;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.ClinicalNoteRepository;
import com.kostasanagno.his.repository.ClinicalNoteVersionRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.security.Caller;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
class ClinicalNoteServiceImplTest {

    private static final Instant T0 = Instant.parse("2026-11-10T10:00:00Z");

    @Mock
    private ClinicalNoteRepository noteRepository;
    @Mock
    private ClinicalNoteVersionRepository versionRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private HospitalizationRepository hospitalizationRepository;
    @Mock
    private UserRepository userRepository;

    private ClinicalNoteServiceImpl service;
    private Patient patient;
    private Doctor author;
    private Doctor otherDoctor;
    private User authorUser;
    private User adminUser;
    private final Caller authorCaller = new Caller("dr.house", Set.of(Role.DOCTOR));
    private final Caller adminCaller = new Caller("admin", Set.of(Role.ADMIN));
    private final Caller otherDoctorCaller = new Caller("dr.cuddy", Set.of(Role.DOCTOR));

    @BeforeEach
    void setUp() {
        service = new ClinicalNoteServiceImpl(noteRepository, versionRepository, patientRepository, doctorRepository,
                appointmentRepository, hospitalizationRepository, userRepository, new ClinicalNoteMapperImpl());

        Specialty cardiology = Specialty.builder().name("Cardiology").build();
        cardiology.setId(1L);
        patient = Patient.builder().firstName("Maria").lastName("Papadopoulou").nationalId("1").build();
        patient.setId(10L);
        author = Doctor.builder().firstName("Gregory").lastName("House").licenseNumber("MD-1").specialty(cardiology).build();
        author.setId(20L);
        otherDoctor = Doctor.builder().firstName("Lisa").lastName("Cuddy").licenseNumber("MD-2").specialty(cardiology).build();
        otherDoctor.setId(21L);
        authorUser = User.builder().username("dr.house").roles(Set.of(Role.DOCTOR)).build();
        authorUser.setId(5L);
        adminUser = User.builder().username("admin").roles(Set.of(Role.ADMIN)).build();
        adminUser.setId(1L);

        lenient().when(noteRepository.saveAndFlush(any(ClinicalNote.class))).thenAnswer(inv -> {
            ClinicalNote n = inv.getArgument(0);
            n.setId(100L);
            n.setCreatedAt(T0);
            return n;
        });
        lenient().when(versionRepository.saveAndFlush(any(ClinicalNoteVersion.class))).thenAnswer(inv -> {
            ClinicalNoteVersion v = inv.getArgument(0);
            v.setId(200L + v.getVersionNumber());
            v.setCreatedAt(T0.plusSeconds(60L * v.getVersionNumber()));
            return v;
        });
        lenient().when(userRepository.findByUsernameIgnoreCase("dr.house")).thenReturn(Optional.of(authorUser));
        lenient().when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(adminUser));
    }

    private ClinicalNoteRequest request() {
        return new ClinicalNoteRequest(10L, null, null, " Chest pain for two days ", "  ", "Suspected angina",
                "ECG and troponin", "i20.9");
    }

    private void stubAuthorAndPatient() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(author));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
    }

    private ClinicalNote note() {
        ClinicalNote n = ClinicalNote.builder().patient(patient).authorDoctor(author).build();
        n.setId(100L);
        n.setCreatedAt(T0);
        return n;
    }

    private ClinicalNoteVersion version(ClinicalNote note, int number, String assessment) {
        ClinicalNoteVersion v = ClinicalNoteVersion.builder().note(note).versionNumber(number).assessment(assessment)
                .diagnosisCode("I20.9").editedBy(authorUser).build();
        v.setId(200L + number);
        v.setCreatedAt(T0.plusSeconds(60L * number));
        return v;
    }

    private ClinicalNoteAmendRequest amendment(int baseVersion) {
        return new ClinicalNoteAmendRequest(baseVersion, " Corrected assessment ", null, null, "Confirmed angina",
                null, "I20.0");
    }

    // ---- create --------------------------------------------------------------------------------

    @Test
    void createWritesVersionOneAsTheLinkedDoctorWithNormalisedContent() {
        stubAuthorAndPatient();

        ClinicalNoteResponse response = service.create(request(), authorCaller);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.authorDoctor().fullName()).isEqualTo("Gregory House");
        assertThat(response.patient().fullName()).isEqualTo("Maria Papadopoulou");
        assertThat(response.history()).isEqualTo("Chest pain for two days");
        assertThat(response.examination()).isNull();               // blank text becomes null
        assertThat(response.diagnosisCode()).isEqualTo("I20.9");  // upper-cased
        assertThat(response.lastChangeReason()).isNull();
        assertThat(response.editedBy()).isEqualTo("dr.house");
    }

    @Test
    void createRequiresADoctorProfile() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nurse")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(), new Caller("nurse", Set.of(Role.NURSE))))
                .isInstanceOf(AccessDeniedException.class);
        verify(noteRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsMissingAndInactivePatient() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(author));
        when(patientRepository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request(), authorCaller)).isInstanceOf(ResourceNotFoundException.class);

        patient.setActive(false);
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        assertThatThrownBy(() -> service.create(request(), authorCaller)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void createLinksAnAppointmentOfTheSamePatient() {
        stubAuthorAndPatient();
        Appointment appointment = Appointment.builder().patient(patient).doctor(author).build();
        appointment.setId(60L);
        when(appointmentRepository.findById(60L)).thenReturn(Optional.of(appointment));

        ClinicalNoteResponse response = service.create(
                new ClinicalNoteRequest(10L, 60L, null, "h", null, null, null, null), authorCaller);

        assertThat(response.appointmentId()).isEqualTo(60L);
        assertThat(response.hospitalizationId()).isNull();
    }

    @Test
    void createRejectsAnAppointmentOrHospitalizationOfAnotherPatient() {
        stubAuthorAndPatient();
        Patient other = Patient.builder().nationalId("2").build();
        other.setId(11L);
        Appointment appointment = Appointment.builder().patient(other).doctor(author).build();
        Hospitalization hospitalization = Hospitalization.builder().patient(other).attendingDoctor(author).build();
        when(appointmentRepository.findById(60L)).thenReturn(Optional.of(appointment));
        when(hospitalizationRepository.findById(70L)).thenReturn(Optional.of(hospitalization));

        assertThatThrownBy(() -> service.create(
                new ClinicalNoteRequest(10L, 60L, null, "h", null, null, null, null), authorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("different patient");
        assertThatThrownBy(() -> service.create(
                new ClinicalNoteRequest(10L, null, 70L, "h", null, null, null, null), authorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("different patient");
    }

    @Test
    void createLinksAHospitalizationOfTheSamePatient() {
        stubAuthorAndPatient();
        Hospitalization hospitalization = Hospitalization.builder().patient(patient).attendingDoctor(author).build();
        hospitalization.setId(70L);
        when(hospitalizationRepository.findById(70L)).thenReturn(Optional.of(hospitalization));

        ClinicalNoteResponse response = service.create(
                new ClinicalNoteRequest(10L, null, 70L, "h", null, null, null, null), authorCaller);

        assertThat(response.hospitalizationId()).isEqualTo(70L);
    }

    @Test
    void createRejectsBothLinksAndUnknownLinks() {
        stubAuthorAndPatient();
        assertThatThrownBy(() -> service.create(
                new ClinicalNoteRequest(10L, 60L, 70L, "h", null, null, null, null), authorCaller))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("not both");

        when(appointmentRepository.findById(61L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(
                new ClinicalNoteRequest(10L, 61L, null, "h", null, null, null, null), authorCaller))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- amend ---------------------------------------------------------------------------------

    @Test
    void authorAmendsByAddingANewVersionAndOldVersionsAreUntouched() {
        ClinicalNote note = note();
        ClinicalNoteVersion v1 = version(note, 1, "Suspected angina");
        when(noteRepository.findById(100L)).thenReturn(Optional.of(note));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(author));
        when(versionRepository.findTopByNoteIdOrderByVersionNumberDesc(100L)).thenReturn(Optional.of(v1));

        ClinicalNoteResponse response = service.amend(100L, amendment(1), authorCaller);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.assessment()).isEqualTo("Confirmed angina");
        assertThat(response.history()).isNull();                   // an amendment states the full new content
        assertThat(response.diagnosisCode()).isEqualTo("I20.0");
        assertThat(response.lastChangeReason()).isEqualTo("Corrected assessment");
        assertThat(response.createdAt()).isEqualTo(T0);
        assertThat(response.modifiedAt()).isAfter(T0);
        assertThat(v1.getAssessment()).isEqualTo("Suspected angina");
        ArgumentCaptor<ClinicalNoteVersion> captor = ArgumentCaptor.forClass(ClinicalNoteVersion.class);
        verify(versionRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(202L);     // a new row, not v1 modified
    }

    @Test
    void adminCanAmendAndIsRecordedAsTheEditor() {
        ClinicalNote note = note();
        when(noteRepository.findById(100L)).thenReturn(Optional.of(note));
        when(versionRepository.findTopByNoteIdOrderByVersionNumberDesc(100L))
                .thenReturn(Optional.of(version(note, 1, "x")));

        ClinicalNoteResponse response = service.amend(100L, amendment(1), adminCaller);

        assertThat(response.editedBy()).isEqualTo("admin");
        assertThat(response.authorDoctor().id()).isEqualTo(20L);   // the author does not change
    }

    @Test
    void anotherDoctorCannotAmend() {
        when(noteRepository.findById(100L)).thenReturn(Optional.of(note()));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.cuddy")).thenReturn(Optional.of(otherDoctor));

        assertThatThrownBy(() -> service.amend(100L, amendment(1), otherDoctorCaller))
                .isInstanceOf(AccessDeniedException.class);
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void userWithoutADoctorProfileCannotAmend() {
        when(noteRepository.findById(100L)).thenReturn(Optional.of(note()));
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nurse")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.amend(100L, amendment(1), new Caller("nurse", Set.of(Role.NURSE))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void amendingAStaleVersionIsAConflict() {
        ClinicalNote note = note();
        when(noteRepository.findById(100L)).thenReturn(Optional.of(note));
        when(versionRepository.findTopByNoteIdOrderByVersionNumberDesc(100L))
                .thenReturn(Optional.of(version(note, 3, "x")));

        assertThatThrownBy(() -> service.amend(100L, amendment(2), adminCaller))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("someone else").hasMessageContaining("3");
        verify(versionRepository, never()).saveAndFlush(any());
    }

    @Test
    void amendingAnUnknownNoteIs404() {
        when(noteRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.amend(9L, amendment(1), adminCaller))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getById(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getVersions(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- reads ---------------------------------------------------------------------------------

    @Test
    void getByIdReturnsTheCurrentVersion() {
        ClinicalNote note = note();
        when(noteRepository.findById(100L)).thenReturn(Optional.of(note));
        when(versionRepository.findTopByNoteIdOrderByVersionNumberDesc(100L))
                .thenReturn(Optional.of(version(note, 2, "Latest")));

        ClinicalNoteResponse response = service.getById(100L);

        assertThat(response.version()).isEqualTo(2);
        assertThat(response.assessment()).isEqualTo("Latest");
    }

    @Test
    void versionsAreReturnedWithTheirChangeReasons() {
        ClinicalNote note = note();
        ClinicalNoteVersion v1 = version(note, 1, "First");
        ClinicalNoteVersion v2 = version(note, 2, "Second");
        v2.setChangeReason("Typo");
        when(noteRepository.findById(100L)).thenReturn(Optional.of(note));
        when(versionRepository.findByNoteIdOrderByVersionNumberAsc(100L)).thenReturn(List.of(v1, v2));

        List<ClinicalNoteVersionResponse> versions = service.getVersions(100L);

        assertThat(versions).extracting(ClinicalNoteVersionResponse::version).containsExactly(1, 2);
        assertThat(versions.get(0).changeReason()).isNull();
        assertThat(versions.get(1).changeReason()).isEqualTo("Typo");
        assertThat(versions.get(1).editedBy()).isEqualTo("dr.house");
    }

    @Test
    void searchAddsCurrentVersionsWithOneExtraQuery() {
        ClinicalNote note = note();
        Pageable pageable = PageRequest.of(0, 10);
        when(noteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(note), pageable, 1));
        when(versionRepository.findLatestByNoteIds(List.of(100L))).thenReturn(List.of(version(note, 2, "Latest")));

        Page<ClinicalNoteResponse> page = service.search(
                new ClinicalNoteSearchCriteria(10L, null, null, null, "I20", null, null), pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).version()).isEqualTo(2);
    }

    @Test
    void myNotesAreRestrictedToTheLinkedDoctor() {
        Pageable pageable = PageRequest.of(0, 10);
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("dr.house")).thenReturn(Optional.of(author));
        when(noteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<ClinicalNoteResponse> page = service.searchForDoctorUser(
                new ClinicalNoteSearchCriteria(null, 999L, null, null, null, null, null), pageable, authorCaller);

        assertThat(page.getTotalElements()).isZero();
        verify(versionRepository, never()).findLatestByNoteIds(any());
    }

    @Test
    void myNotesIs404WithoutALinkedProfile() {
        when(doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.searchForDoctorUser(null, PageRequest.of(0, 10),
                new Caller("nobody", Set.of(Role.DOCTOR)))).isInstanceOf(ResourceNotFoundException.class);
    }
}
