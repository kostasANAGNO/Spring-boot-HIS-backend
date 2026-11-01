package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.WardRequest;
import com.kostasanagno.his.dto.WardResponse;
import com.kostasanagno.his.dto.WardSearchCriteria;
import com.kostasanagno.his.entity.Clinic;
import com.kostasanagno.his.entity.Ward;
import com.kostasanagno.his.exception.BusinessRuleException;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.WardMapper;
import com.kostasanagno.his.repository.BedAssignmentRepository;
import com.kostasanagno.his.repository.ClinicRepository;
import com.kostasanagno.his.repository.StructureSpecifications;
import com.kostasanagno.his.repository.WardRepository;
import com.kostasanagno.his.service.WardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WardServiceImpl implements WardService {

    private static final String RESOURCE = "Ward";

    private final WardRepository wardRepository;
    private final ClinicRepository clinicRepository;
    private final BedAssignmentRepository bedAssignmentRepository;
    private final WardMapper wardMapper;

    @Override
    public WardResponse create(WardRequest request) {
        Clinic clinic = findActiveClinic(request.clinicId());
        String name = request.name().trim();
        if (wardRepository.existsByClinicIdAndNameIgnoreCase(clinic.getId(), name)) {
            throw DuplicateResourceException.of(RESOURCE, "name", name);
        }
        Ward ward = wardMapper.toEntity(request);
        ward.setName(name);
        ward.setClinic(clinic);
        Ward saved = wardRepository.save(ward);
        log.info("Created ward {}", saved.getId());
        return wardMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WardResponse getById(Long id) {
        return wardMapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WardResponse> findAll(WardSearchCriteria criteria) {
        return wardRepository.findAll(StructureSpecifications.wards(criteria), Sort.by("name"))
                .stream().map(wardMapper::toResponse).toList();
    }

    @Override
    public WardResponse update(Long id, WardRequest request) {
        Ward ward = find(id);
        String name = request.name().trim();
        if (wardRepository.existsByClinicIdAndNameIgnoreCaseAndIdNot(request.clinicId(), name, id)) {
            throw DuplicateResourceException.of(RESOURCE, "name", name);
        }
        if (!ward.getClinic().getId().equals(request.clinicId())) {
            if (bedAssignmentRepository.existsByBedRoomWardIdAndEndTimeIsNull(id)) {
                throw new BusinessRuleException("Ward %d has occupied beds and cannot be moved".formatted(id));
            }
            ward.setClinic(findActiveClinic(request.clinicId()));
        }
        wardMapper.updateEntity(request, ward);
        ward.setName(name);
        log.info("Updated ward {}", id);
        return wardMapper.toResponse(wardRepository.saveAndFlush(ward));
    }

    @Override
    public WardResponse deactivate(Long id) {
        Ward ward = find(id);
        if (bedAssignmentRepository.existsByBedRoomWardIdAndEndTimeIsNull(id)) {
            throw new BusinessRuleException("Ward %d has occupied beds and cannot be deactivated".formatted(id));
        }
        ward.setActive(false);
        log.info("Deactivated ward {}", id);
        return wardMapper.toResponse(ward);
    }

    private Ward find(Long id) {
        return wardRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private Clinic findActiveClinic(Long id) {
        Clinic clinic = clinicRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Clinic", id));
        if (!clinic.isActive()) {
            throw new BusinessRuleException("Clinic %d is inactive".formatted(id));
        }
        return clinic;
    }
}
