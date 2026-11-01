package com.kostasanagno.his.service.impl;

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
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.ClinicalNoteMapper;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.ClinicalNoteRepository;
import com.kostasanagno.his.repository.ClinicalNoteSpecifications;
import com.kostasanagno.his.repository.ClinicalNoteVersionRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.ClinicalNoteService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Only ids are logged: the content of a note is clinical data and never reaches the logs. The unique
 * (note, version) constraint is the safety net if two amendments race past the {@code baseVersion} check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClinicalNoteServiceImpl implements ClinicalNoteService {

    private static final String RESOURCE = "Clinical note";

    private final ClinicalNoteRepository noteRepository;
    private final ClinicalNoteVersionRepository versionRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final HospitalizationRepository hospitalizationRepository;
    private final UserRepository userRepository;
    private final ClinicalNoteMapper noteMapper;

    @Override
    public ClinicalNoteResponse create(ClinicalNoteRequest request, Caller caller) {
        Doctor author = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(caller.username())
                .orElseThrow(() -> new AccessDeniedException("Only a doctor with a linked profile can write clinical notes"));
        Patient patient = findActivePatient(request.patientId());
        if (request.appointmentId() != null && request.hospitalizationId() != null) {
            throw new BusinessRuleException("A note can be linked to an appointment or a hospitalization, not both");
        }
        Appointment appointment = request.appointmentId() == null
                ? null : findAppointmentOf(request.appointmentId(), patient.getId());
        Hospitalization hospitalization = request.hospitalizationId() == null
                ? null : findHospitalizationOf(request.hospitalizationId(), patient.getId());

        ClinicalNote note = noteRepository.saveAndFlush(ClinicalNote.builder()
                .patient(patient)
                .authorDoctor(author)
                .appointment(appointment)
                .hospitalization(hospitalization)
                .build());
        ClinicalNoteVersion first = saveVersion(note, 1, null, findUser(caller), request.history(),
                request.examination(), request.assessment(), request.plan(), request.diagnosisCode());
        log.info("Created clinical note {} (patient {}, doctor {})", note.getId(), patient.getId(), author.getId());
        return noteMapper.toResponse(note, first);
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicalNoteResponse getById(Long id) {
        return noteMapper.toResponse(find(id), latestVersion(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicalNoteVersionResponse> getVersions(Long id) {
        find(id);
        return versionRepository.findByNoteIdOrderByVersionNumberAsc(id).stream()
                .map(noteMapper::toVersionResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClinicalNoteResponse> search(ClinicalNoteSearchCriteria criteria, Pageable pageable) {
        Page<ClinicalNote> page = noteRepository.findAll(ClinicalNoteSpecifications.from(criteria), pageable);
        // current versions of the whole page in one query
        Map<Long, ClinicalNoteVersion> latest = page.isEmpty() ? Map.of() : versionRepository
                .findLatestByNoteIds(page.getContent().stream().map(ClinicalNote::getId).toList())
                .stream().collect(Collectors.toMap(v -> v.getNote().getId(), Function.identity()));
        return page.map(note -> noteMapper.toResponse(note, latest.get(note.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClinicalNoteResponse> searchForDoctorUser(ClinicalNoteSearchCriteria criteria, Pageable pageable,
            Caller caller) {
        Doctor doctor = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(caller.username())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile is linked to the current user"));
        ClinicalNoteSearchCriteria own = (criteria == null
                ? new ClinicalNoteSearchCriteria(null, null, null, null, null, null, null) : criteria)
                .withAuthorDoctorId(doctor.getId());
        return search(own, pageable);
    }

    @Override
    public ClinicalNoteResponse amend(Long id, ClinicalNoteAmendRequest request, Caller caller) {
        ClinicalNote note = find(id);
        assertMayAmend(note, caller);
        ClinicalNoteVersion current = latestVersion(id);
        if (current.getVersionNumber() != request.baseVersion()) {
            throw new DuplicateResourceException(("The note was changed by someone else (current version is %d, "
                    + "you amended version %d); reload it and try again")
                    .formatted(current.getVersionNumber(), request.baseVersion()));
        }
        ClinicalNoteVersion next = saveVersion(note, current.getVersionNumber() + 1, request.changeReason().trim(),
                findUser(caller), request.history(), request.examination(), request.assessment(), request.plan(),
                request.diagnosisCode());
        log.info("Amended clinical note {} to version {}", id, next.getVersionNumber());
        return noteMapper.toResponse(note, next);
    }

    // ---- lookups -------------------------------------------------------------------------------

    private ClinicalNote find(Long id) {
        return noteRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private ClinicalNoteVersion latestVersion(Long noteId) {
        return versionRepository.findTopByNoteIdOrderByVersionNumberDesc(noteId)
                .orElseThrow(() -> new IllegalStateException("Clinical note %d has no versions".formatted(noteId)));
    }

    private User findUser(Caller caller) {
        return userRepository.findByUsernameIgnoreCase(caller.username())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Patient findActivePatient(Long id) {
        Patient patient = patientRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Patient", id));
        if (!patient.isActive()) {
            throw new BusinessRuleException("Patient %d is inactive".formatted(id));
        }
        return patient;
    }

    private Appointment findAppointmentOf(Long appointmentId, Long patientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Appointment", appointmentId));
        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new BusinessRuleException("Appointment %d belongs to a different patient".formatted(appointmentId));
        }
        return appointment;
    }

    private Hospitalization findHospitalizationOf(Long hospitalizationId, Long patientId) {
        Hospitalization hospitalization = hospitalizationRepository.findById(hospitalizationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Hospitalization", hospitalizationId));
        if (!hospitalization.getPatient().getId().equals(patientId)) {
            throw new BusinessRuleException(
                    "Hospitalization %d belongs to a different patient".formatted(hospitalizationId));
        }
        return hospitalization;
    }

    // ---- rules and helpers ---------------------------------------------------------------------

    /** Only the author (the doctor profile linked to the caller) or an admin may amend. */
    private void assertMayAmend(ClinicalNote note, Caller caller) {
        if (caller.isAdmin()) {
            return;
        }
        boolean author = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(caller.username())
                .map(doctor -> doctor.getId().equals(note.getAuthorDoctor().getId()))
                .orElse(false);
        if (!author) {
            throw new AccessDeniedException("Only the author of a note or an admin can amend it");
        }
    }

    private ClinicalNoteVersion saveVersion(ClinicalNote note, int number, String changeReason, User editor,
            String history, String examination, String assessment, String plan, String diagnosisCode) {
        return versionRepository.saveAndFlush(ClinicalNoteVersion.builder()
                .note(note)
                .versionNumber(number)
                .history(clean(history))
                .examination(clean(examination))
                .assessment(clean(assessment))
                .plan(clean(plan))
                .diagnosisCode(cleanCode(diagnosisCode))
                .changeReason(changeReason)
                .editedBy(editor)
                .build());
    }

    /** Blank text is stored as null so "has content" checks in the database stay meaningful. */
    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String cleanCode(String code) {
        String cleaned = clean(code);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }
}
