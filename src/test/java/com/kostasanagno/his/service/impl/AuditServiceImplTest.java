package com.kostasanagno.his.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.audit.AuditEntry;
import com.kostasanagno.his.audit.AuditTarget;
import com.kostasanagno.his.dto.AuditEventResponse;
import com.kostasanagno.his.dto.AuditEventSearchCriteria;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditEvent;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.mapper.AuditEventMapperImpl;
import com.kostasanagno.his.repository.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(auditEventRepository, new AuditEventMapperImpl(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordStoresOneEventPerTarget() {
        AuditEntry entry = new AuditEntry("dr.house", "DOCTOR", AuditAction.VIEW, AuditResource.CLINICAL_NOTE,
                AuditOutcome.SUCCESS, null, "GET", "/api/clinical-notes", "10.0.0.1",
                List.of(new AuditTarget(1L, 10L), new AuditTarget(2L, 11L)));

        service.record(entry);

        ArgumentCaptor<List<AuditEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditEventRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
                .allSatisfy(event -> {
                    assertThat(event.getOccurredAt()).isEqualTo(NOW);
                    assertThat(event.getUsername()).isEqualTo("dr.house");
                    assertThat(event.getRoles()).isEqualTo("DOCTOR");
                    assertThat(event.getAction()).isEqualTo(AuditAction.VIEW);
                    assertThat(event.getResourceType()).isEqualTo(AuditResource.CLINICAL_NOTE);
                    assertThat(event.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
                    assertThat(event.getPath()).isEqualTo("/api/clinical-notes");
                    assertThat(event.getIpAddress()).isEqualTo("10.0.0.1");
                });
        assertThat(captor.getValue()).extracting(AuditEvent::getResourceId).containsExactly(1L, 2L);
        assertThat(captor.getValue()).extracting(AuditEvent::getPatientId).containsExactly(10L, 11L);
    }

    @Test
    void searchMapsEventsToResponses() {
        AuditEvent event = AuditEvent.builder().occurredAt(NOW).username("nurse").roles("NURSE")
                .action(AuditAction.VIEW).resourceType(AuditResource.PATIENT).resourceId(1L).patientId(1L)
                .outcome(AuditOutcome.DENIED).detail("BAD_CREDENTIALS").httpMethod("GET").path("/api/patients/1").build();
        event.setId(5L);
        Pageable pageable = PageRequest.of(0, 20);
        when(auditEventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event), pageable, 1));

        Page<AuditEventResponse> page = service.search(
                new AuditEventSearchCriteria("nurse", 1L, null, null, null, AuditOutcome.DENIED, null, null, null), pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).outcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(page.getContent().get(0).patientId()).isEqualTo(1L);
        assertThat(page.getContent().get(0).detail()).isEqualTo("BAD_CREDENTIALS");
    }
}
