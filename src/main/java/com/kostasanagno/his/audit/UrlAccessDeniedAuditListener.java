package com.kostasanagno.his.audit;

import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.entity.AuditResource;
import com.kostasanagno.his.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Audits refusals made by the URL rules in {@code SecurityConfig}. Those happen in the filter chain, before any
 * controller runs, so {@link AuditAspect} cannot see them. Only requests from logged-in users are recorded
 * (anonymous requests are not, so unauthenticated traffic cannot fill the audit table), and only for routes that
 * map to an audited action.
 * <p>
 * Today the only URL-level role rule is {@code POST /api/auth/register}; add a line to {@link #auditTargetOf} when
 * another one is introduced. Refusals by {@code @PreAuthorize} are audited by the aspect instead.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrlAccessDeniedAuditListener {

    private final AuditService auditService;

    @EventListener
    public void onDenied(AuthorizationDeniedEvent<?> event) {
        if (!(event.getObject() instanceof HttpServletRequest request)) {
            return;
        }
        Authentication authentication = event.getAuthentication().get();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        auditTargetOf(request).ifPresent(target -> {
            try {
                String roles = authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(name -> name.startsWith("ROLE_"))
                        .map(name -> name.substring("ROLE_".length()))
                        .sorted()
                        .collect(Collectors.joining(","));
                auditService.record(new AuditEntry(authentication.getName(), roles, target.action(),
                        target.resource(), AuditOutcome.DENIED, null, request.getMethod(), request.getRequestURI(),
                        request.getRemoteAddr(), List.of(new AuditTarget(null, null))));
            } catch (RuntimeException auditFailure) {
                // the request is refused anyway; never turn the 403 into a 500
                log.error("Could not write the audit event for a refused request", auditFailure);
            }
        });
    }

    private static Optional<Target> auditTargetOf(HttpServletRequest request) {
        if (HttpMethod.POST.matches(request.getMethod()) && "/api/auth/register".equals(request.getRequestURI())) {
            return Optional.of(new Target(AuditResource.USER, AuditAction.CREATE));
        }
        return Optional.empty();
    }

    private record Target(AuditResource resource, AuditAction action) {
    }
}
