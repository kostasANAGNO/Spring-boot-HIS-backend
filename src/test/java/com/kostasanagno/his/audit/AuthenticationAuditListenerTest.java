package com.kostasanagno.his.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.entity.Role;
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.service.AuditService;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@ExtendWith(MockitoExtension.class)
class AuthenticationAuditListenerTest {

    @Mock
    private AuditService auditService;
    @Mock
    private UserRepository userRepository;

    private AuthenticationAuditListener listener;
    private User doctor;

    @BeforeEach
    void setUp() {
        listener = new AuthenticationAuditListener(auditService, userRepository);
        doctor = User.builder().username("dr.house").roles(Set.of(Role.DOCTOR)).build();
        doctor.setId(5L);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.9.8.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private AuditEntry recorded() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService).record(captor.capture());
        return captor.getValue();
    }

    private static UsernamePasswordAuthenticationToken attempt(String username) {
        return UsernamePasswordAuthenticationToken.unauthenticated(username, "the-typed-password");
    }

    @Test
    void aSuccessfulLoginRecordsWhoRolesAndWhere() {
        when(userRepository.findByUsernameIgnoreCase("DR.House")).thenReturn(Optional.of(doctor));

        listener.onSuccess(new AuthenticationSuccessEvent(
                new TestingAuthenticationToken("DR.House", "n/a", "ROLE_NURSE", "ROLE_DOCTOR", "OTHER")));

        AuditEntry entry = recorded();
        assertThat(entry.username()).isEqualTo("dr.house");            // the account's own spelling
        assertThat(entry.roles()).isEqualTo("DOCTOR,NURSE");
        assertThat(entry.action()).isEqualTo(AuditAction.LOGIN);
        assertThat(entry.resource()).isEqualTo(AuditResource.USER);
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.detail()).isNull();
        assertThat(entry.httpMethod()).isEqualTo("POST");
        assertThat(entry.path()).isEqualTo("/api/auth/login");
        assertThat(entry.ipAddress()).isEqualTo("10.9.8.7");
        assertThat(entry.targets()).containsExactly(new AuditTarget(5L, null));
    }

    @Test
    void aWrongPasswordForARealAccountIsRecordedAgainstThatAccount() {
        when(userRepository.findByUsernameIgnoreCase("dr.house")).thenReturn(Optional.of(doctor));

        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(attempt("dr.house"),
                new BadCredentialsException("bad")));

        AuditEntry entry = recorded();
        assertThat(entry.username()).isEqualTo("dr.house");
        assertThat(entry.roles()).isEmpty();                            // nothing is granted on a failure
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(entry.detail()).isEqualTo("BAD_CREDENTIALS");
        assertThat(entry.targets()).containsExactly(new AuditTarget(5L, null));
        assertThat(entry.toString()).doesNotContain("the-typed-password");
    }

    @Test
    void aNameThatIsNotAnAccountIsNeverStored() {
        // e.g. a password typed into the username field
        when(userRepository.findByUsernameIgnoreCase("MySecretPassword1!")).thenReturn(Optional.empty());

        listener.onFailure(new AuthenticationFailureBadCredentialsEvent(attempt("MySecretPassword1!"),
                new BadCredentialsException("bad")));

        AuditEntry entry = recorded();
        assertThat(entry.username()).isEqualTo("unknown");
        assertThat(entry.targets()).containsExactly(new AuditTarget(null, null));
        assertThat(entry.toString()).doesNotContain("MySecretPassword1!");
    }

    @Test
    void theReasonIsAShortCode() {
        when(userRepository.findByUsernameIgnoreCase("dr.house")).thenReturn(Optional.of(doctor));

        listener.onFailure(new AuthenticationFailureDisabledEvent(attempt("dr.house"), new DisabledException("x")));
        listener.onFailure(new AuthenticationFailureLockedEvent(attempt("dr.house"), new LockedException("x")));
        listener.onFailure(new AuthenticationFailureExpiredEvent(attempt("dr.house"), new AccountExpiredException("x")));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditService, org.mockito.Mockito.times(3)).record(captor.capture());
        assertThat(captor.getAllValues()).extracting(AuditEntry::detail)
                .containsExactly("ACCOUNT_DISABLED", "ACCOUNT_LOCKED", "ACCOUNT_EXPIRED");
    }

    @Test
    void ifTheAuditStoreIsDownALoginIsNotGranted() {
        when(userRepository.findByUsernameIgnoreCase("dr.house")).thenReturn(Optional.of(doctor));
        doThrow(new IllegalStateException("audit store down")).when(auditService).record(any());

        assertThatThrownBy(() -> listener.onSuccess(
                new AuthenticationSuccessEvent(new TestingAuthenticationToken("dr.house", "n/a", "ROLE_DOCTOR"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("audit store down");
    }

    @Test
    void ifTheAuditStoreIsDownAFailedLoginStillGetsItsNormalAnswer() {
        when(userRepository.findByUsernameIgnoreCase("dr.house")).thenReturn(Optional.of(doctor));
        doThrow(new IllegalStateException("audit store down")).when(auditService).record(any());

        assertThatCode(() -> listener.onFailure(new AuthenticationFailureBadCredentialsEvent(attempt("dr.house"),
                new BadCredentialsException("bad")))).doesNotThrowAnyException();
    }
}
