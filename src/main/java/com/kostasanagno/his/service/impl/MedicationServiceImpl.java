package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.MedicationRequest;
import com.kostasanagno.his.dto.MedicationResponse;
import com.kostasanagno.his.dto.MedicationSearchCriteria;
import com.kostasanagno.his.entity.Medication;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.MedicationMapper;
import com.kostasanagno.his.repository.MedicationRepository;
import com.kostasanagno.his.repository.MedicationSpecifications;
import com.kostasanagno.his.service.MedicationService;
import java.util.Locale;
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
public class MedicationServiceImpl implements MedicationService {

    private static final String RESOURCE = "Medication";

    private final MedicationRepository medicationRepository;
    private final MedicationMapper medicationMapper;

    @Override
    public MedicationResponse create(MedicationRequest request) {
        String code = normalizeCode(request.code());
        if (medicationRepository.existsByCodeIgnoreCase(code)) {
            throw DuplicateResourceException.of(RESOURCE, "code", code);
        }
        Medication medication = medicationMapper.toEntity(request);
        prepare(medication, request, code);
        Medication saved = medicationRepository.save(medication);
        log.info("Created medication {}", saved.getId());
        return medicationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicationResponse getById(Long id) {
        return medicationMapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MedicationResponse> search(MedicationSearchCriteria criteria, Pageable pageable) {
        return medicationRepository.findAll(MedicationSpecifications.catalog(criteria), pageable)
                .map(medicationMapper::toResponse);
    }

    @Override
    public MedicationResponse update(Long id, MedicationRequest request) {
        Medication medication = find(id);
        String code = normalizeCode(request.code());
        if (medicationRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw DuplicateResourceException.of(RESOURCE, "code", code);
        }
        medicationMapper.updateEntity(request, medication);
        prepare(medication, request, code);
        log.info("Updated medication {}", id);
        return medicationMapper.toResponse(medicationRepository.saveAndFlush(medication));
    }

    @Override
    public MedicationResponse deactivate(Long id) {
        Medication medication = find(id);
        medication.setActive(false);
        log.info("Deactivated medication {}", id);
        return medicationMapper.toResponse(medication);
    }

    private Medication find(Long id) {
        return medicationRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private static void prepare(Medication medication, MedicationRequest request, String code) {
        medication.setCode(code);
        medication.setName(request.name().trim());
        medication.setStrength(blankToNull(request.strength()));
        medication.setAtcCode(request.atcCode() == null || request.atcCode().isBlank()
                ? null : request.atcCode().trim().toUpperCase(Locale.ROOT));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
