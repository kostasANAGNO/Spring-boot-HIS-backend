package com.kostasanagno.his.audit;

import com.kostasanagno.his.dto.PageResponse;
import com.kostasanagno.his.entity.AuditOutcome;
import com.kostasanagno.his.security.Caller;
import com.kostasanagno.his.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes the audit trail for every controller method annotated with {@link Audited}.
 * <p>
 * It runs OUTSIDE method security so refused requests ({@code @PreAuthorize}) are recorded as DENIED. On success
 * the event is written before the response leaves the server: if the audit cannot be stored the request fails and
 * the caller gets no data ("no audit, no data"). Only ids and request metadata are stored, never record content.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditAspect {

    private static final int MAX_PATH_LENGTH = 255;

    private final AuditService auditService;
    private final PatientIdResolver patientIdResolver;

    @Around("@annotation(com.kostasanagno.his.audit.Audited)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        Audited audited = annotationOf(joinPoint);
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            recordFailure(joinPoint, audited, ex);
            throw ex;
        }
        List<AuditTarget> targets = targetsOf(result);
        if (targets.isEmpty()) {
            targets = List.of(targetFromPath(joinPoint, audited));
        }
        record(audited, AuditOutcome.SUCCESS, targets);
        return result;
    }

    /** The annotation on the (most specific) method that was called, found without relying on argument binding. */
    private static Audited annotationOf(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = joinPoint.getTarget() == null ? method.getDeclaringClass() : joinPoint.getTarget().getClass();
        return AnnotatedElementUtils.findMergedAnnotation(AopUtils.getMostSpecificMethod(method, targetClass), Audited.class);
    }

    private void recordFailure(ProceedingJoinPoint joinPoint, Audited audited, Throwable failure) {
        AuditOutcome outcome = failure instanceof AccessDeniedException ? AuditOutcome.DENIED : AuditOutcome.FAILED;
        try {
            record(audited, outcome, List.of(targetFromPath(joinPoint, audited)));
        } catch (RuntimeException auditFailure) {
            // never hide the original error behind an audit problem
            log.error("Could not write the audit event for a {} request", outcome, auditFailure);
        }
    }

    private void record(Audited audited, AuditOutcome outcome, List<AuditTarget> targets) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Caller caller = Caller.from(authentication);
        String roles = caller.roles().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        HttpServletRequest request = currentRequest();
        auditService.record(new AuditEntry(caller.username(), roles, audited.action(), audited.resource(), outcome,
                request == null ? "N/A" : request.getMethod(),
                request == null ? "N/A" : abbreviate(request.getRequestURI()),
                request == null ? null : request.getRemoteAddr(), targets));
    }

    /** Every auditable element of the response (single, page, list or wrapped in a ResponseEntity). */
    private List<AuditTarget> targetsOf(Object result) {
        Object body = result instanceof ResponseEntity<?> entity ? entity.getBody() : result;
        List<?> items = switch (body) {
            case PageResponse<?> page -> page.content();
            case Collection<?> collection -> new ArrayList<>(collection);
            case null -> List.of();
            default -> List.of(body);
        };
        return items.stream()
                .filter(Auditable.class::isInstance)
                .map(item -> (Auditable) item)
                .map(item -> new AuditTarget(item.auditResourceId(), item.auditPatientId()))
                .toList();
    }

    /** A single target built from the id in the request path (or an empty one when the request named no record). */
    private AuditTarget targetFromPath(ProceedingJoinPoint joinPoint, Audited audited) {
        Long id = idArgument(joinPoint, audited);
        return new AuditTarget(id, id == null ? null : patientIdResolver.resolve(audited.resource(), id));
    }

    private Long idArgument(ProceedingJoinPoint joinPoint, Audited audited) {
        if (audited.idArg().isEmpty()) {
            return null;
        }
        String[] names = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; names != null && i < names.length; i++) {
            if (names[i].equals(audited.idArg()) && args[i] instanceof Long id) {
                return id;
            }
        }
        return null;
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private static String abbreviate(String path) {
        return path.length() <= MAX_PATH_LENGTH ? path : path.substring(0, MAX_PATH_LENGTH);
    }
}
