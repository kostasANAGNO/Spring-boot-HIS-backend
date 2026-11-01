package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.ChargeItemRequest;
import com.kostasanagno.his.dto.ChargeItemResponse;
import com.kostasanagno.his.dto.ChargeItemSearchCriteria;
import com.kostasanagno.his.entity.ChargeItem;
import com.kostasanagno.his.exception.DuplicateResourceException;
import com.kostasanagno.his.exception.ResourceNotFoundException;
import com.kostasanagno.his.mapper.ChargeItemMapper;
import com.kostasanagno.his.repository.ChargeItemRepository;
import com.kostasanagno.his.repository.ChargeItemSpecifications;
import com.kostasanagno.his.service.ChargeItemService;
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
public class ChargeItemServiceImpl implements ChargeItemService {

    private static final String RESOURCE = "Charge item";

    private final ChargeItemRepository chargeItemRepository;
    private final ChargeItemMapper chargeItemMapper;

    @Override
    public ChargeItemResponse create(ChargeItemRequest request) {
        String code = normalizeCode(request.code());
        if (chargeItemRepository.existsByCodeIgnoreCase(code)) {
            throw DuplicateResourceException.of(RESOURCE, "code", code);
        }
        ChargeItem item = chargeItemMapper.toEntity(request);
        item.setCode(code);
        item.setName(request.name().trim());
        ChargeItem saved = chargeItemRepository.save(item);
        log.info("Created charge item {}", saved.getId());
        return chargeItemMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeItemResponse getById(Long id) {
        return chargeItemMapper.toResponse(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChargeItemResponse> search(ChargeItemSearchCriteria criteria, Pageable pageable) {
        return chargeItemRepository.findAll(ChargeItemSpecifications.from(criteria), pageable)
                .map(chargeItemMapper::toResponse);
    }

    @Override
    public ChargeItemResponse update(Long id, ChargeItemRequest request) {
        ChargeItem item = find(id);
        String code = normalizeCode(request.code());
        if (chargeItemRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw DuplicateResourceException.of(RESOURCE, "code", code);
        }
        chargeItemMapper.updateEntity(request, item);
        item.setCode(code);
        item.setName(request.name().trim());
        log.info("Updated charge item {}", id);
        return chargeItemMapper.toResponse(chargeItemRepository.saveAndFlush(item));
    }

    @Override
    public ChargeItemResponse deactivate(Long id) {
        ChargeItem item = find(id);
        item.setActive(false);
        log.info("Deactivated charge item {}", id);
        return chargeItemMapper.toResponse(item);
    }

    private ChargeItem find(Long id) {
        return chargeItemRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, id));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
