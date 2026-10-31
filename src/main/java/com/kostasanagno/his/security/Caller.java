package com.kostasanagno.his.security;

import com.kostasanagno.his.entity.Role;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.Authentication;

/**
 * The authenticated user as seen by services: username plus roles. Built by controllers so services
 * can apply caller-dependent rules without depending on the security context.
 */
public record Caller(String username, Set<Role> roles) {

    private static final String ROLE_PREFIX = "ROLE_";

    public static Caller from(Authentication authentication) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        authentication.getAuthorities().forEach(authority -> {
            String name = authority.getAuthority();
            if (name.startsWith(ROLE_PREFIX)) {
                try {
                    roles.add(Role.valueOf(name.substring(ROLE_PREFIX.length())));
                } catch (IllegalArgumentException ignored) {
                    // not one of our roles
                }
            }
        });
        return new Caller(authentication.getName(), roles);
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }

    /** Diagnoses, notes and other clinical text are for ADMIN, DOCTOR and NURSE; receptionists never see them. */
    public boolean canSeeClinicalData() {
        return roles.contains(Role.ADMIN) || roles.contains(Role.DOCTOR) || roles.contains(Role.NURSE);
    }
}
