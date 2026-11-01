package com.kostasanagno.his.service.impl;

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
import com.kostasanagno.his.repository.ClinicRepository;
import com.kostasanagno.his.repository.DoctorRepository;
import com.kostasanagno.his.repository.DoctorSpecifications;
import com.kostasanagno.his.repository.SpecialtyRepository;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.service.DoctorService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DoctorServiceImpl implements DoctorService {

    private static final String RESOURCE = "Doctor";

    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;
    private final DoctorMapper doctorMapper;

    @Override
    public DoctorResponse create(DoctorRequest request) {
        String licenseNumber = request.licenseNumber().trim();
        if (doctorRepository.existsByLicenseNumber(licenseNumber)) {
            throw DuplicateResourceException.of(RESOURCE, "license number", licenseNumber);
        }
        Doctor doctor = doctorMapper.toEntity(request);
        doctor.setLicenseNumber(licenseNumber);
        doctor.setSpecialty(findSpecialty(request.specialtyId()));
        if (request.userId() != null && doctorRepository.existsByUserId(request.userId())) {
            throw linkedAlready(request.userId());
        }
        doctor.setUser(resolveUser(request.userId()));
        doctor.setClinic(resolveClinic(request.clinicId()));
        Doctor saved = doctorRepository.save(doctor);
        log.info("Created doctor {}", saved.getId());
        return doctorMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorResponse getById(Long id) {
        return doctorMapper.toResponse(findActive(id));
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorResponse getByUsername(String username) {
        return doctorRepository.findByUserUsernameIgnoreCaseAndActiveTrue(username)
                .map(doctorMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No doctor profile is linked to the current user"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DoctorResponse> search(DoctorSearchCriteria criteria, Pageable pageable) {
        return doctorRepository.findAll(DoctorSpecifications.from(criteria), pageable)
                .map(doctorMapper::toResponse);
    }

    @Override
    public DoctorResponse update(Long id, DoctorRequest request) {
        Doctor doctor = findActive(id);
        String licenseNumber = request.licenseNumber().trim();
        if (doctorRepository.existsByLicenseNumberAndIdNot(licenseNumber, id)) {
            throw DuplicateResourceException.of(RESOURCE, "license number", licenseNumber);
        }
        if (request.userId() != null && doctorRepository.existsByUserIdAndIdNot(request.userId(), id)) {
            throw linkedAlready(request.userId());
        }
        doctorMapper.updateEntity(request, doctor);
        doctor.setLicenseNumber(licenseNumber);
        doctor.setSpecialty(findSpecialty(request.specialtyId()));
        doctor.setUser(resolveUser(request.userId()));
        doctor.setClinic(resolveClinic(request.clinicId()));
        log.info("Updated doctor {}", id);
        return doctorMapper.toResponse(doctorRepository.saveAndFlush(doctor));
    }

    @Override
    public void delete(Long id) {
        Doctor doctor = findActive(id);
        doctor.setActive(false);
        doctor.setDeletedAt(Instant.now());
        log.info("Soft-deleted doctor {}", id);
    }

    private Doctor findActive(Long id) {
        return doctorRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private Specialty findSpecialty(Long id) {
        return specialtyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Specialty", id));
    }

    /** Null in, null out; otherwise the user must exist and carry the DOCTOR role. */
    private User resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        if (!user.getRoles().contains(Role.DOCTOR)) {
            throw new BusinessRuleException("User %d does not have the DOCTOR role".formatted(userId));
        }
        return user;
    }

    /** Null in, null out; otherwise the clinic must exist and be active. */
    private Clinic resolveClinic(Long clinicId) {
        if (clinicId == null) {
            return null;
        }
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> ResourceNotFoundException.of("Clinic", clinicId));
        if (!clinic.isActive()) {
            throw new BusinessRuleException("Clinic %d is inactive".formatted(clinicId));
        }
        return clinic;
    }

    private static DuplicateResourceException linkedAlready(Long userId) {
        return new DuplicateResourceException("User %d is already linked to another doctor".formatted(userId));
    }
}
