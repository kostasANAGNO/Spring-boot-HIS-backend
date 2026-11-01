package com.kostasanagno.his.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code bedDayCode} is the price list entry used to invoice a hospital stay (one unit per started 24 hours). */
@ConfigurationProperties(prefix = "app.billing")
public record BillingProperties(@DefaultValue("BED-DAY") String bedDayCode) {
}
