package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.AdmissionRequest;
import com.kostasanagno.his.dto.BedAssignmentResponse;
import com.kostasanagno.his.dto.BedSummary;
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
import com.kostasanagno.his.entity.DischargeRecord;
import com.kostasanagno.his.entity.DischargeType;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.HospitalizationStatus;
import com.kostasanagno.his.entity.Patient;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.BedMapper;
import com.kostasanagno.his.mapper.HospitalizationMapper;
import com.kostasanagno.his.repository.AdmissionRecordRepository;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.BedRepository;
import com.kostasanagno.his.repository.DischargeRecordRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.HospitalizationRepository;
import com.kostasanagno.his.repository.HospitalizationSpecifications;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.HospitalizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * The checks here only produce friendly messages; the database guarantees correctness under concurrency
 * (bed exclusion constraint, one ADMITTED stay per patient, one open assignment per stay, every stay has its
 * admission record and every discharged stay its discharge record). Only ids are logged: never names, diagnoses
 * or summaries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HospitalizationServiceImpl implements HospitalizationService {

    private static final String RESOURCE = "Hospitalization";

    private final HospitalizationRepository hospitalizationRepository;
    private final AdmissionRecordRepository admissionRecordRepository;
    private final DischargeRecordRepository dischargeRecordRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final BedRepository bedRepository;
    private final AppointmentRepository appointmentRepository;
    private final HospitalizationMapper hospitalizationMapper;
    private final BedMapper bedMapper;
    private final Clock clock;

    @Override
    public HospitalizationResponse admit(AdmissionRequest request, Caller caller) {
        Instant now = clock.instant();
        Instant admissionTime = request.admissionTime() == null ? now : request.admissionTime();
        if (admissionTime.isAfter(now)) {
            throw new BusinessRuleException("admissionTime must not be in the future");
        }
        validateAdmissionDocument(request);
        Patient patient = findActivePatient(request.patientId());
        Doctor doctor = findActiveDoctor(request.attendingDoctorId());
        Bed bed = findUsableBed(request.bedId());
        Appointment appointment = request.appointmentId() == null
                ? null : findLinkableAppointment(request.appointmentId(), patient.getId());
        if (hospitalizationRepository.existsByPatientIdAndStatus(patient.getId(), HospitalizationStatus.ADMITTED)) {
            throw new DuplicateResourceException("The patient already has an active hospitalization");
        }
        assertBedFree(bed.getId(), admissionTime);

        Hospitalization hospitalization = hospitalizationRepository.saveAndFlush(Hospitalization.builder()
                .patient(patient)
                .attendingDoctor(doctor)
                .appointment(appointment)
                .status(HospitalizationStatus.ADMITTED)
                .admissionTime(admissionTime)
                .build());
        AdmissionRecord admission = admissionRecordRepository.saveAndFlush(AdmissionRecord.builder()
                .hospitalization(hospitalization)
                .admissionType(request.admissionType())
                .admissionSource(request.admissionSource())
                .referringFacility(clean(request.referringFacility()))
                .reason(request.admissionReason().trim())
                .initialDiagnosisCode(cleanCode(request.initialDiagnosisCode()))
                .initialDiagnosis(clean(request.initialDiagnosis()))
                .build());
        BedAssignment assignment = openAssignment(hospitalization, bed, admissionTime, null);
        log.info("Admitted hospitalization {} (patient {}, doctor {}, bed {})", hospitalization.getId(),
                patient.getId(), doctor.getId(), bed.getId());
        return present(hospitalization, assignment, admission, null, caller);
    }

    @Override
    @Transactional(readOnly = true)
    public HospitalizationResponse getById(Long id, Caller caller) {
        Hospitalization hospitalization = find(id);
        return present(hospitalization, currentAssignment(id), admissionRecordOf(id), dischargeRecordOf(hospitalization),
                caller);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BedAssignmentResponse> getBedHistory(Long id, Caller caller) {
        find(id);
        return bedAssignmentRepository.findByHospitalizationIdOrderByStartTimeAsc(id).stream()
                .map(hospitalizationMapper::toAssignmentResponse)
                .map(entry -> caller.canSeeClinicalData() ? entry : entry.withoutClinicalData())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HospitalizationResponse> search(HospitalizationSearchCriteria criteria, Pageable pageable,
            Caller caller) {
        Page<Hospitalization> page = hospitalizationRepository
                .findAll(HospitalizationSpecifications.from(criteria), pageable);
        if (page.isEmpty()) {
            return page.map(h -> present(h, null, null, null, caller));
        }
        // the bed and the two documents of the whole page: one query each, never one per row
        List<Long> ids = page.getContent().stream().map(Hospitalization::getId).toList();
        Map<Long, BedAssignment> beds = bedAssignmentRepository.findByHospitalizationIdInAndEndTimeIsNull(ids).stream()
                .collect(Collectors.toMap(a -> a.getHospitalization().getId(), Function.identity()));
        Map<Long, AdmissionRecord> admissions = admissionRecordRepository.findByHospitalizationIdIn(ids).stream()
                .collect(Collectors.toMap(r -> r.getHospitalization().getId(), Function.identity()));
        Map<Long, DischargeRecord> discharges = dischargeRecordRepository.findByHospitalizationIdIn(ids).stream()
                .collect(Collectors.toMap(r -> r.getHospitalization().getId(), Function.identity()));
        return page.map(h -> present(h, beds.get(h.getId()), admissions.get(h.getId()), discharges.get(h.getId()), caller));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HospitalizationResponse> searchForDoctorUser(HospitalizationSearchCriteria criteria,
            Pageable pageable, Caller caller) {
        Doctor doctor = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(caller.username())
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile is linked to the current user"));
        HospitalizationSearchCriteria own = (criteria == null
                ? new HospitalizationSearchCriteria(null, null, null, null, null, null, null, null) : criteria)
                .withDoctorId(doctor.getId());
        return search(own, pageable, caller);
    }

    @Override
    public HospitalizationResponse update(Long id, HospitalizationUpdateRequest request, Caller caller) {
        Hospitalization hospitalization = find(id);
        requireAdmitted(hospitalization, "Only ADMITTED hospitalizations can be updated");
        Doctor doctor = findActiveDoctor(request.attendingDoctorId());
        AdmissionRecord admission = admissionRecordOf(id);
        hospitalizationMapper.updateAdmission(request, admission);
        admission.setReason(request.admissionReason().trim());
        admission.setInitialDiagnosisCode(cleanCode(request.initialDiagnosisCode()));
        admission.setInitialDiagnosis(clean(request.initialDiagnosis()));
        hospitalization.setAttendingDoctor(doctor);
        Hospitalization saved = hospitalizationRepository.saveAndFlush(hospitalization);
        AdmissionRecord savedAdmission = admissionRecordRepository.saveAndFlush(admission);
        log.info("Updated hospitalization {}", id);
        return present(saved, currentAssignment(id), savedAdmission, null, caller);
    }

    @Override
    public HospitalizationResponse transfer(Long id, TransferRequest request, Caller caller) {
        Hospitalization hospitalization = find(id);
        requireAdmitted(hospitalization, "Only ADMITTED hospitalizations can be transferred");
        BedAssignment current = currentAssignment(id);
        Bed target = findUsableBed(request.bedId());
        if (current != null && current.getBed().getId().equals(target.getId())) {
            throw new BusinessRuleException("The patient is already in this bed");
        }
        Instant now = clock.instant();
        if (current != null && !now.isAfter(current.getStartTime())) {
            throw new BusinessRuleException("The patient was assigned to the current bed too recently to be moved");
        }
        assertBedFree(target.getId(), now);

        if (current != null) {
            current.setEndTime(now);
            bedAssignmentRepository.saveAndFlush(current);
        }
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().trim();
        BedAssignment next = openAssignment(hospitalization, target, now, reason);
        log.info("Transferred hospitalization {} to bed {}", id, target.getId());
        return present(hospitalization, next, admissionRecordOf(id), null, caller);
    }

    @Override
    public HospitalizationResponse discharge(Long id, DischargeRequest request, Caller caller) {
        Hospitalization hospitalization = find(id);
        assertMayDischarge(hospitalization, caller);
        assertTransition(hospitalization, HospitalizationStatus.DISCHARGED);
        validateDischargeDocument(request);

        BedAssignment current = currentAssignment(id);
        Instant now = clock.instant();
        Instant dischargeTime = request.dischargeTime() == null ? now : request.dischargeTime();
        validateDischargeTime(hospitalization, current, dischargeTime, now);

        if (current != null) {
            current.setEndTime(dischargeTime);
            bedAssignmentRepository.saveAndFlush(current);
        }
        hospitalization.setStatus(HospitalizationStatus.DISCHARGED);
        hospitalization.setDischargeTime(dischargeTime);
        Hospitalization saved = hospitalizationRepository.saveAndFlush(hospitalization);
        DischargeRecord discharge = dischargeRecordRepository.saveAndFlush(DischargeRecord.builder()
                .hospitalization(saved)
                .dischargeType(request.dischargeType())
                .destinationFacility(clean(request.destinationFacility()))
                .finalDiagnosisCode(cleanCode(request.finalDiagnosisCode()))
                .finalDiagnosis(clean(request.finalDiagnosis()))
                .summary(request.summary().trim())
                .instructions(clean(request.instructions()))
                .followUpDate(request.followUpDate())
                .build());
        log.info("Discharged hospitalization {}", id);
        return present(saved, null, admissionRecordOf(id), discharge, caller);
    }

    // ---- lookups -------------------------------------------------------------------------------

    private Hospitalization find(Long id) {
        return hospitalizationRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private BedAssignment currentAssignment(Long hospitalizationId) {
        return bedAssignmentRepository.findByHospitalizationIdAndEndTimeIsNull(hospitalizationId).orElse(null);
    }

    /** Every hospitalization has one (the database checks it at commit). */
    private AdmissionRecord admissionRecordOf(Long hospitalizationId) {
        return admissionRecordRepository.findByHospitalizationId(hospitalizationId).orElseThrow(
                () -> new IllegalStateException("Hospitalization %d has no admission record".formatted(hospitalizationId)));
    }

    private DischargeRecord dischargeRecordOf(Hospitalization hospitalization) {
        return hospitalization.getStatus() == HospitalizationStatus.DISCHARGED
                ? dischargeRecordRepository.findByHospitalizationId(hospitalization.getId()).orElse(null) : null;
    }

    private Patient findActivePatient(Long id) {
        Patient patient = patientRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Patient", id));
        if (!patient.isActive()) {
            throw new BusinessRuleException("Patient %d is inactive".formatted(id));
        }
        return patient;
    }

    private Doctor findActiveDoctor(Long id) {
        Doctor doctor = doctorRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Doctor", id));
        if (!doctor.isActive()) {
            throw new BusinessRuleException("Doctor %d is inactive".formatted(id));
        }
        return doctor;
    }

    /** The bed, its room, its ward and the ward's clinic must all be active. */
    private Bed findUsableBed(Long id) {
        Bed bed = bedRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Bed", id));
        if (!bed.isActive() || !bed.getRoom().isActive() || !bed.getRoom().getWard().isActive()
                || !bed.getRoom().getWard().getClinic().isActive()) {
            throw new BusinessRuleException(
                    "Bed %d is not in service (the bed, its room, its ward or its clinic is inactive)".formatted(id));
        }
        return bed;
    }

    private Appointment findLinkableAppointment(Long appointmentId, Long patientId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Appointment", appointmentId));
        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new BusinessRuleException("Appointment %d belongs to a different patient".formatted(appointmentId));
        }
        if (hospitalizationRepository.existsByAppointmentId(appointmentId)) {
            throw new DuplicateResourceException(
                    "Appointment %d is already linked to a hospitalization".formatted(appointmentId));
        }
        return appointment;
    }

    // ---- rules ---------------------------------------------------------------------------------

    /** What the admission record itself requires, independent of other records. */
    private void validateAdmissionDocument(AdmissionRequest request) {
        if (request.admissionType() == AdmissionType.TRANSFER_IN && clean(request.referringFacility()) == null) {
            throw new BusinessRuleException("referringFacility is required for a TRANSFER_IN admission");
        }
        if ((request.admissionSource() == AdmissionSource.APPOINTMENT) != (request.appointmentId() != null)) {
            throw new BusinessRuleException("admissionSource APPOINTMENT and appointmentId go together");
        }
    }

    /** What the discharge record itself requires. */
    private void validateDischargeDocument(DischargeRequest request) {
        boolean transferOut = request.dischargeType() == DischargeType.TRANSFER_OUT;
        if (transferOut != (clean(request.destinationFacility()) != null)) {
            throw new BusinessRuleException(transferOut
                    ? "destinationFacility is required for a TRANSFER_OUT discharge"
                    : "destinationFacility is only allowed for a TRANSFER_OUT discharge");
        }
        if (request.followUpDate() != null) {
            if (request.dischargeType() == DischargeType.DECEASED) {
                throw new BusinessRuleException("A DECEASED discharge has no follow-up date");
            }
            if (request.followUpDate().isBefore(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))) {
                throw new BusinessRuleException("followUpDate must not be in the past");
            }
        }
    }

    private void assertBedFree(Long bedId, Instant from) {
        if (bedAssignmentRepository.isBedTakenFrom(bedId, from)) {
            throw new DuplicateResourceException("The bed is already occupied");
        }
    }

    private void assertMayDischarge(Hospitalization hospitalization, Caller caller) {
        if (caller.isAdmin()) {
            return;
        }
        boolean attending = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(caller.username())
                .map(doctor -> doctor.getId().equals(hospitalization.getAttendingDoctor().getId()))
                .orElse(false);
        if (!attending) {
            throw new AccessDeniedException("Only the attending doctor or an admin can discharge a patient");
        }
    }

    private void validateDischargeTime(Hospitalization hospitalization, BedAssignment current, Instant dischargeTime,
            Instant now) {
        if (dischargeTime.isAfter(now)) {
            throw new BusinessRuleException("dischargeTime must not be in the future");
        }
        if (!dischargeTime.isAfter(hospitalization.getAdmissionTime())) {
            throw new BusinessRuleException("dischargeTime must be after the admission time");
        }
        if (current != null && !dischargeTime.isAfter(current.getStartTime())) {
            throw new BusinessRuleException("dischargeTime must be after the patient was moved to the current bed");
        }
    }

    /** Checks only; the status is changed after every other validation has passed. */
    private void assertTransition(Hospitalization hospitalization, HospitalizationStatus target) {
        if (!hospitalization.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException("Cannot change a hospitalization from %s to %s"
                    .formatted(hospitalization.getStatus(), target));
        }
    }

    private void requireAdmitted(Hospitalization hospitalization, String message) {
        if (hospitalization.getStatus() != HospitalizationStatus.ADMITTED) {
            throw new BusinessRuleException(message);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private BedAssignment openAssignment(Hospitalization hospitalization, Bed bed, Instant start, String reason) {
        return bedAssignmentRepository.saveAndFlush(BedAssignment.builder()
                .hospitalization(hospitalization)
                .bed(bed)
                .startTime(start)
                .reason(reason)
                .build());
    }

    /** Blank text is stored as null. */
    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static String cleanCode(String code) {
        String cleaned = clean(code);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    /**
     * Maps to the API shape: adds the current bed and the two documents and hides clinical text from callers who
     * may not see it. Every response goes through here so the redaction cannot be forgotten.
     */
    private HospitalizationResponse present(Hospitalization hospitalization, BedAssignment current,
            AdmissionRecord admission, DischargeRecord discharge, Caller caller) {
        BedSummary bed = current == null ? null : bedMapper.toSummary(current.getBed());
        HospitalizationResponse response = HospitalizationResponse.assemble(
                hospitalizationMapper.toResponse(hospitalization), bed,
                admission == null ? null : hospitalizationMapper.toAdmissionResponse(admission),
                discharge == null ? null : hospitalizationMapper.toDischargeResponse(discharge));
        return caller.canSeeClinicalData() ? response : response.withoutClinicalData();
    }
}
