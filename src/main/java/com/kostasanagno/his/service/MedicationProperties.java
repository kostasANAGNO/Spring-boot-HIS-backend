package com.kostasanagno.his.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code earlyTolerance}: how much earlier than "last dose + interval" a repeat dose may be recorded (wards are not
 * clockwork). Anything earlier is refused as a possible double dose.
 */
@ConfigurationProperties(prefix = "app.medication")
public record MedicationProperties(@DefaultValue("30m") Duration earlyTolerance) {
}
