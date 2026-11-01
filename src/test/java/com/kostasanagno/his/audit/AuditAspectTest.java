package com.kostasanagno.his.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.service.AuditService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditService auditService;
    @Mock
    private PatientIdResolver patientIdResolver;

    private Sample sample;

    /** A stand-in for a controller: the aspect only cares about the annotation and the return value. */
    public static class Sample {

        @Audited(resource = AuditResource.CLINICAL_NOTE, action = AuditAction.CREATE)
        public ResponseEntity<Item> create() {
            return ResponseEntity.ok(new Item(7L, 70L));
        }

        @Audited(resource = AuditResource.CLINICAL_NOTE, action = AuditAction.VIEW)
        public PageResponse<Item> page(boolean empty) {
            List<Item> items = empty ? List.of() : List.of(new Item(1L, 10L), new Item(2L, 10L), new Item(3L, 11L));
            return new PageResponse<>(items, 0, 20, items.size(), 1, true, true);
        }

        @Audited(resource = AuditResource.CLINICAL_NOTE, action = AuditAction.VIEW, idArg = "id")
        public List<String> versions(Long id) {
            return List.of("v1", "v2");
        }

        @Audited(resource = AuditResource.PATIENT, action = AuditAction.DELETE, idArg = "id")
        public void delete(Long id) {
            // nothing returned
        }

        @Audited(resource = AuditResource.APPOINTMENT, action = AuditAction.CANCEL, idArg = "id")
        public Item fail(Long id, RuntimeException failure) {
            throw failure;
        }
    }

    public record Item(Long id, Long patientId) implements Auditable {
        @Override
        public Long auditResourceId() {
            return id;
        }

        @Override
        public Long auditPatientId() {
            return patientId;
        }
    }

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Sample());
        factory.addAspect(new AuditAspect(auditService, patientIdResolver));
        sample = factory.getProxy();

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("dr.house", "n/a", "ROLE_NURSE", "ROLE_DOCTOR", "IGNORED_AUTHORITY"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clinical-notes/7");
        request.setRemoteAddr("10.1.2.3");
        request.setQueryString("name=SecretName");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private AuditEntry recorded() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }

    @Test
    void aSingleResponseBecomesOneEventWithWhoAndWhere() {
        sample.create();

        AuditEntry entry = recorded();
        assertThat(entry.username()).isEqualTo("dr.house");
        assertThat(entry.roles()).isEqualTo("DOCTOR,NURSE");             // sorted, only real roles
        assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
        assertThat(entry.resource()).isEqualTo(AuditResource.CLINICAL_NOTE);
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.httpMethod()).isEqualTo("GET");
        assertThat(entry.path()).isEqualTo("/api/clinical-notes/7");     // path only, never the query string
        assertThat(entry.ipAddress()).isEqualTo("10.1.2.3");
        assertThat(entry.targets()).containsExactly(new AuditTarget(7L, 70L));
    }

    @Test
    void everyItemOfAPageIsLogged() {
        sample.page(false);

        assertThat(recorded().targets()).containsExactly(
                new AuditTarget(1L, 10L), new AuditTarget(2L, 10L), new AuditTarget(3L, 11L));
    }

    @Test
    void anEmptyResultStillLeavesOneEventSoTheSearchIsVisible() {
        sample.page(true);

        assertThat(recorded().targets()).containsExactly(new AuditTarget(null, null));
        verify(patientIdResolver, never()).resolve(any(), any());
    }

    @Test
    void subRecordsAreLoggedAgainstTheParentIdAndItsPatient() {
        when(patientIdResolver.resolve(AuditResource.CLINICAL_NOTE, 5L)).thenReturn(50L);

        sample.versions(5L);

        assertThat(recorded().targets()).containsExactly(new AuditTarget(5L, 50L));
    }

    @Test
    void voidMethodsAreLoggedByPathId() {
        when(patientIdResolver.resolve(AuditResource.PATIENT, 9L)).thenReturn(9L);

        sample.delete(9L);

        AuditEntry entry = recorded();
        assertThat(entry.action()).isEqualTo(AuditAction.DELETE);
        assertThat(entry.targets()).containsExactly(new AuditTarget(9L, 9L));
    }

    @Test
    void aRefusedRequestIsLoggedAsDeniedAndStillRefused() {
        when(patientIdResolver.resolve(AuditResource.APPOINTMENT, 3L)).thenReturn(30L);
        AccessDeniedException denied = new AccessDeniedException("no");

        assertThatThrownBy(() -> sample.fail(3L, denied)).isSameAs(denied);

        AuditEntry entry = recorded();
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(entry.targets()).containsExactly(new AuditTarget(3L, 30L));
    }

    @Test
    void otherFailuresAreLoggedAsFailed() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> sample.fail(3L, failure)).isSameAs(failure);

        assertThat(recorded().outcome()).isEqualTo(AuditOutcome.FAILED);
    }

    @Test
    void ifAuditingAFailureBreaksTheOriginalErrorIsStillThrown() {
        doThrow(new IllegalStateException("audit store down")).when(auditService).record(any());
        IllegalStateException failure = new IllegalStateException("original");

        assertThatThrownBy(() -> sample.fail(3L, failure)).isSameAs(failure);
    }

    @Test
    void noAuditNoData() {
        doThrow(new IllegalStateException("audit store down")).when(auditService).record(any());

        assertThatThrownBy(() -> sample.create())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("audit store down");
    }
}
