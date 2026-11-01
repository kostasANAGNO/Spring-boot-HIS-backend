package com.kostasanagno.his.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

@ExtendWith(MockitoExtension.class)
class UrlAccessDeniedAuditListenerTest {

    @Mock
    private AuditService auditService;

    private UrlAccessDeniedAuditListener listener;

    @BeforeEach
    void setUp() {
        listener = new UrlAccessDeniedAuditListener(auditService);
    }

    private static AuthorizationDeniedEvent<MockHttpServletRequest> denied(Authentication authentication,
            String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.4.4.4");
        return new AuthorizationDeniedEvent<>(() -> authentication, request, new AuthorizationDecision(false));
    }

    private static Authentication user(String name, String... roles) {
        Authentication authentication = new TestingAuthenticationToken(name, "n/a", roles);
        authentication.setAuthenticated(true);
        return authentication;
    }

    @Test
    void aLoggedInUserRefusedAtRegisterIsRecorded() {
        listener.onDenied(denied(user("nurse", "ROLE_NURSE"), "POST", "/api/auth/register"));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        AuditEntry entry = captor.getValue();
        assertThat(entry.username()).isEqualTo("nurse");
        assertThat(entry.roles()).isEqualTo("NURSE");
        assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
        assertThat(entry.resource()).isEqualTo(AuditResource.USER);
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(entry.path()).isEqualTo("/api/auth/register");
        assertThat(entry.ipAddress()).isEqualTo("10.4.4.4");
        assertThat(entry.targets()).containsExactly(new AuditTarget(null, null));
    }

    @Test
    void anonymousRequestsAreNeverRecorded() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        listener.onDenied(denied(anonymous, "POST", "/api/auth/register"));
        listener.onDenied(denied(new TestingAuthenticationToken("x", "y"), "POST", "/api/auth/register"));

        verify(auditService, never()).record(any());
    }

    @Test
    void routesWithoutAnAuditedActionAreIgnored() {
        listener.onDenied(denied(user("nurse", "ROLE_NURSE"), "GET", "/api/auth/register"));
        listener.onDenied(denied(user("nurse", "ROLE_NURSE"), "GET", "/actuator/env"));

        verify(auditService, never()).record(any());
    }

    @Test
    void otherAuthorizationObjectsAreIgnored() {
        listener.onDenied(new AuthorizationDeniedEvent<>(() -> user("nurse", "ROLE_NURSE"), "not a request",
                new AuthorizationDecision(false)));

        verify(auditService, never()).record(any());
    }

    @Test
    void anAuditProblemNeverTurnsTheRefusalIntoAnError() {
        doThrow(new IllegalStateException("audit store down")).when(auditService).record(any());

        assertThatCode(() -> listener.onDenied(denied(user("nurse", "ROLE_NURSE"), "POST", "/api/auth/register")))
                .doesNotThrowAnyException();
    }
}
