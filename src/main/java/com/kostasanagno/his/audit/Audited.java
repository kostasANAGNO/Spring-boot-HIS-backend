package com.kostasanagno.his.audit;

import com.kostasanagno.his.entity.AuditAction;
import com.kostasanagno.his.entity.AuditResource;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put on a controller method that reads or changes patient-related data; {@link AuditAspect} then writes audit
 * events (who, what, which patient, when, allowed or not).
 * <p>
 * The records are taken from the response: every element implementing {@link Auditable} (a single response, or the
 * items of a page/list) becomes one event. When the response has none (a void method, a list of sub-records such as
 * versions, an empty result, or a failure) one event is written using {@link #idArg()}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    AuditResource resource();

    AuditAction action();

    /** Name of the method parameter holding the record id (e.g. {@code "id"}), used when the response has none. */
    String idArg() default "";
}
