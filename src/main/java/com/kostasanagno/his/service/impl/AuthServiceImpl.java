package com.kostasanagno.his.service.impl;

import com.kostasanagno.his.dto.LoginRequest;
import com.kostasanagno.his.dto.LoginResponse;
import com.kostasanagno.his.security.JwtService;
import com.kostasanagno.his.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately NOT transactional (and it suspends a caller's transaction): checking a password with BCrypt takes
 * ~100 ms and must not hold a database connection. The few reads and the audit write it triggers run in their own
 * short transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        UserDetails user = (UserDetails) authentication.getPrincipal();
        log.info("User '{}' logged in", user.getUsername());
        return new LoginResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpiration().toSeconds());
    }
}
