package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.audit.AuditEntry;
import com.kostasanagno.his.dto.AuditEventResponse;
import com.kostasanagno.his.dto.AuditEventSearchCriteria;
import com.kostasanagno.his.entity.AuditEvent;
import com.kostasanagno.his.mapper.AuditEventMapper;
import com.kostasanagno.his.repository.AuditEventRepository;
import com.kostasanagno.his.repository.AuditEventSpecifications;
import com.kostasanagno.his.service.AuditService;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditServiceImpl implements AuditService {

    private final AuditEventRepository auditEventRepository;
    private final AuditEventMapper auditEventMapper;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEntry entry) {
        List<AuditEvent> events = entry.targets().stream()
                .map(target -> AuditEvent.builder()
                        .occurredAt(clock.instant())
                        .username(entry.username())
                        .roles(entry.roles())
                        .action(entry.action())
                        .resourceType(entry.resource())
                        .resourceId(target.resourceId())
                        .patientId(target.patientId())
                        .outcome(entry.outcome())
                        .httpMethod(entry.httpMethod())
                        .path(entry.path())
                        .ipAddress(entry.ipAddress())
                        .build())
                .toList();
        auditEventRepository.saveAll(events);
    }

    @Override
    public Page<AuditEventResponse> search(AuditEventSearchCriteria criteria, Pageable pageable) {
        return auditEventRepository.findAll(AuditEventSpecifications.from(criteria), pageable)
                .map(auditEventMapper::toResponse);
    }
}
