package com.kostasanagno.his.config;

import com.kostasanagno.his.security.JwtProperties;
import com.kostasanagno.his.service.AppointmentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AppointmentProperties.class})
public class PropertiesConfig {
}
