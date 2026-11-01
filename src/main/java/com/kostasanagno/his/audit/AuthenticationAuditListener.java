package com.kostasanagno.his.audit;

import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.entity.User;
import com.kostasanagno.his.repository.UserRepository;
import com.kostasanagno.his.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureCredentialsExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureDisabledEvent;
import org.springframework.security.authentication.event.AuthenticationFailureExpiredEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Audits every login attempt. Nobody is authenticated yet while {@code /api/auth/login} runs, so this uses Spring
 * Security's authentication events instead of {@link AuditAspect}. (Requests carrying a JWT are not logins and
 * raise no event.)
 * <p>
 * A successful login is written before the token is returned ("no audit, no login"). For a failed login the name
 * that was typed is stored only if it belongs to an account; otherwise it is recorded as {@code unknown}, because
 * people sometimes type their password into the username field. The password itself is never stored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationAuditListener {

    static final String UNKNOWN_USERNAME = "unknown";

    private final AuditService auditService;
    private final UserRepository userRepository;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        String roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(name -> name.startsWith("ROLE_"))
                .map(name -> name.substring("ROLE_".length()))
                .sorted()
                .collect(Collectors.joining(","));
        Optional<User> user = userRepository.findByUsernameIgnoreCase(authentication.getName());
        record(user.map(User::getUsername).orElse(authentication.getName()), roles, AuditOutcome.SUCCESS, null,
                user.map(User::getId).orElse(null));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        try {
            Optional<User> user = userRepository.findByUsernameIgnoreCase(event.getAuthentication().getName());
            record(user.map(User::getUsername).orElse(UNKNOWN_USERNAME), "", AuditOutcome.DENIED, detailOf(event),
                    user.map(User::getId).orElse(null));
        } catch (RuntimeException auditFailure) {
            // never replace the 401 with an audit problem
            log.error("Could not write the audit event for a failed login", auditFailure);
        }
    }

    private void record(String username, String roles, AuditOutcome outcome, String detail, Long userId) {
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                ? a.getRequest() : null;
        auditService.record(new AuditEntry(username, roles, AuditAction.LOGIN, AuditResource.USER, outcome, detail,
                request == null ? "N/A" : request.getMethod(),
                request == null ? "N/A" : request.getRequestURI(),
                request == null ? null : request.getRemoteAddr(),
                List.of(new AuditTarget(userId, null))));
    }

    /** A short machine code for the reason; the exact exception text is not stored. */
    private static String detailOf(AbstractAuthenticationFailureEvent event) {
        return switch (event) {
            case AuthenticationFailureBadCredentialsEvent e -> "BAD_CREDENTIALS";
            case AuthenticationFailureDisabledEvent e -> "ACCOUNT_DISABLED";
            case AuthenticationFailureLockedEvent e -> "ACCOUNT_LOCKED";
            case AuthenticationFailureExpiredEvent e -> "ACCOUNT_EXPIRED";
            case AuthenticationFailureCredentialsExpiredEvent e -> "CREDENTIALS_EXPIRED";
            default -> "AUTHENTICATION_FAILED";
        };
    }
}
