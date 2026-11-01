package com.kostasanagno.his.config;

import com.kostasanagno.his.security.JwtProperties;
import com.kostasanagno.his.service.AppointmentProperties;
import com.kostasanagno.his.service.BillingProperties;
import com.kostasanagno.his.service.MedicationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AppointmentProperties.class, BillingProperties.class, MedicationProperties.class})
public class PropertiesConfig {
}
