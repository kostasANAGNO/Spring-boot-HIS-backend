package com.kostasanagno.his.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.appointments")
public record AppointmentProperties(
        @DefaultValue("5m") Duration minDuration,
        @DefaultValue("8h") Duration maxDuration) {
}
