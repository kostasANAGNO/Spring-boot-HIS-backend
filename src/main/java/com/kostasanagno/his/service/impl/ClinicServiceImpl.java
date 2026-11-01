package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.ClinicRequest;
import com.kostasanagno.his.dto.ClinicResponse;
import com.kostasanagno.his.entity.Clinic;
import com.kostasanagno.his.entity.Specialty;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.ClinicMapper;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.ClinicRepository;
import com.kostasanagno.his.repository.SpecialtyRepository;
import com.kostasanagno.his.service.ClinicService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClinicServiceImpl implements ClinicService {

    private static final String RESOURCE = "Clinic";

    private final ClinicRepository clinicRepository;
    private final SpecialtyRepository specialtyRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final ClinicMapper clinicMapper;

    @Override
    public ClinicResponse create(ClinicRequest request) {
        String name = request.name().trim();
        if (clinicRepository.existsByNameIgnoreCase(name)) {
            throw DuplicateResourceException.of(RESOURCE, "name", name);
        }
        Clinic clinic = clinicMapper.toEntity(request);
        clinic.setName(name);
        clinic.setSpecialty(resolveSpecialty(request.specialtyId()));
        Clinic saved = clinicRepository.save(clinic);
        log.info("Created clinic {}", saved.getId());
        return clinicMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ClinicResponse getById(Long id) {
        return clinicMapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicResponse> findAll(Boolean active) {
        return clinicRepository.findAllByActiveOrderByNameAsc(active == null || active)
                .stream().map(clinicMapper::toResponse).toList();
    }

    @Override
    public ClinicResponse update(Long id, ClinicRequest request) {
        Clinic clinic = find(id);
        String name = request.name().trim();
        if (clinicRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw DuplicateResourceException.of(RESOURCE, "name", name);
        }
        clinicMapper.updateEntity(request, clinic);
        clinic.setName(name);
        clinic.setSpecialty(resolveSpecialty(request.specialtyId()));
        log.info("Updated clinic {}", id);
        return clinicMapper.toResponse(clinicRepository.saveAndFlush(clinic));
    }

    @Override
    public ClinicResponse deactivate(Long id) {
        Clinic clinic = find(id);
        if (bedAssignmentRepository.existsByBedRoomWardClinicIdAndEndTimeIsNull(id)) {
            throw new BusinessRuleException("Clinic %d has occupied beds and cannot be deactivated".formatted(id));
        }
        clinic.setActive(false);
        log.info("Deactivated clinic {}", id);
        return clinicMapper.toResponse(clinic);
    }

    private Clinic find(Long id) {
        return clinicRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    /** Null in, null out; otherwise the specialty must exist. */
    private Specialty resolveSpecialty(Long specialtyId) {
        return specialtyId == null ? null : specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Specialty", specialtyId));
    }
}
