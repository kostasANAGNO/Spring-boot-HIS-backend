package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.AppointmentRequest;
import com.kostasanagno.his.dto.AppointmentResponse;
import com.kostasanagno.his.dto.AppointmentSearchCriteria;
import com.kostasanagno.his.dto.AppointmentUpdateRequest;
import com.kostasanagno.his.dto.CancelAppointmentRequest;
import com.kostasanagno.his.entity.Appointment;
import com.kostasanagno.his.entity.AppointmentStatus;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Patient;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.AppointmentMapper;
import com.kostasanagno.his.repository.AppointmentRepository;
import com.kostasanagno.his.repository.AppointmentSpecifications;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.PatientRepository;
import com.kostasanagno.his.service.AppointmentProperties;
import com.kostasanagno.his.service.AppointmentService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Overlaps are checked here first for a friendly 409 message; the exclusion constraints in V1 are the
 * safety net for concurrent requests (see GlobalExceptionHandler). Only ids are logged, never personal data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AppointmentServiceImpl implements AppointmentService {

    private static final String RESOURCE = "Appointment";

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentProperties properties;
    private final Clock clock;

    @Override
    public AppointmentResponse create(AppointmentRequest request) {
        validateTimes(request.startTime(), request.endTime());
        Patient patient = findActivePatient(request.patientId());
        Doctor doctor = findActiveDoctor(request.doctorId());
        assertNoOverlap(doctor.getId(), patient.getId(), request.startTime(), request.endTime(), null);

        Appointment appointment = appointmentMapper.toEntity(request);
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        log.info("Booked appointment {} (doctor {}, patient {})", saved.getId(), doctor.getId(), patient.getId());
        return appointmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long id) {
        return appointmentMapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> search(AppointmentSearchCriteria criteria, Pageable pageable) {
        return appointmentRepository.findAll(AppointmentSpecifications.from(criteria), pageable)
                .map(appointmentMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AppointmentResponse> searchForDoctorUser(String username, AppointmentSearchCriteria criteria,
            Pageable pageable) {
        Doctor doctor = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(username)
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile is linked to the current user"));
        AppointmentSearchCriteria own = (criteria == null
                ? new AppointmentSearchCriteria(null, null, null, null, null) : criteria)
                .withDoctorId(doctor.getId());
        return search(own, pageable);
    }

    @Override
    public AppointmentResponse update(Long id, AppointmentUpdateRequest request) {
        Appointment appointment = find(id);
        requireTransition(appointment, AppointmentStatus.SCHEDULED, "Only SCHEDULED appointments can be updated");
        validateTimes(request.startTime(), request.endTime());
        Patient patient = findActivePatient(appointment.getPatient().getId());
        Doctor doctor = findActiveDoctor(request.doctorId());
        assertNoOverlap(doctor.getId(), patient.getId(), request.startTime(), request.endTime(), appointment.getId());

        appointmentMapper.updateEntity(request, appointment);
        appointment.setDoctor(doctor);
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        log.info("Updated appointment {}", id);
        return appointmentMapper.toResponse(saved);
    }

    @Override
    public AppointmentResponse cancel(Long id, CancelAppointmentRequest request) {
        Appointment appointment = find(id);
        transition(appointment, AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(request.cancellationReason().trim());
        return saveTransition(appointment);
    }

    @Override
    public AppointmentResponse complete(Long id, String username, boolean admin) {
        Appointment appointment = find(id);
        assertMayComplete(appointment, username, admin);
        transition(appointment, AppointmentStatus.COMPLETED);
        return saveTransition(appointment);
    }

    @Override
    public AppointmentResponse markNoShow(Long id) {
        Appointment appointment = find(id);
        transition(appointment, AppointmentStatus.NO_SHOW);
        return saveTransition(appointment);
    }

    private Appointment find(Long id) {
        return appointmentRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
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

    /** End after start, duration within configured limits, start in the future. */
    private void validateTimes(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("endTime must be after startTime");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(properties.minDuration()) < 0) {
            throw new BusinessRuleException("Appointment must last at least %s minutes"
                    .formatted(properties.minDuration().toMinutes()));
        }
        if (duration.compareTo(properties.maxDuration()) > 0) {
            throw new BusinessRuleException("Appointment must last at most %s minutes"
                    .formatted(properties.maxDuration().toMinutes()));
        }
        if (!start.isAfter(clock.instant())) {
            throw new BusinessRuleException("Appointment must start in the future");
        }
    }

    private void assertNoOverlap(Long doctorId, Long patientId, Instant start, Instant end, Long excludeId) {
        if (appointmentRepository.exists(AppointmentSpecifications.doctorBooked(doctorId, start, end, excludeId))) {
            throw new DuplicateResourceException("The doctor is already booked during the requested time");
        }
        if (appointmentRepository.exists(AppointmentSpecifications.patientBooked(patientId, start, end, excludeId))) {
            throw new DuplicateResourceException("The patient is already booked during the requested time");
        }
    }

    private void assertMayComplete(Appointment appointment, String username, boolean admin) {
        if (admin) {
            return;
        }
        boolean assignedDoctor = doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(username)
                .map(doctor -> doctor.getId().equals(appointment.getDoctor().getId()))
                .orElse(false);
        if (!assignedDoctor) {
            throw new AccessDeniedException("Only the assigned doctor or an admin can complete an appointment");
        }
    }

    private void transition(Appointment appointment, AppointmentStatus target) {
        requireTransition(appointment, target, "Cannot change an appointment from %s to %s"
                .formatted(appointment.getStatus(), target));
        appointment.setStatus(target);
    }

    private void requireTransition(Appointment appointment, AppointmentStatus target, String message) {
        AppointmentStatus current = appointment.getStatus();
        boolean allowed = target == AppointmentStatus.SCHEDULED
                ? current == AppointmentStatus.SCHEDULED
                : current.canTransitionTo(target);
        if (!allowed) {
            throw new BusinessRuleException(message);
        }
    }

    private AppointmentResponse saveTransition(Appointment appointment) {
        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        log.info("Appointment {} is now {}", saved.getId(), saved.getStatus());
        return appointmentMapper.toResponse(saved);
    }
}
